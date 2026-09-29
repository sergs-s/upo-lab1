package ru.vka.upo.ui;

import java.util.Locale;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import ru.vka.upo.core.ErrorProfile;
import ru.vka.upo.core.ErrorRow;
import ru.vka.upo.core.Processor;
import ru.vka.upo.core.Realization;
import ru.vka.upo.core.Trajectory;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Notebook;
import ru.vka.upo.model.Settings;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Экран результатов расчёта.
 *
 * Слева от таблицы – чертёж пролёта, справа – ход дальности на выбранной
 * выборке с отсчётами и аппроксимирующей кривой. Показан только тот режим
 * обработки, который обучающийся задал сам; никаких готовых зависимостей
 * ошибки от параметров режима здесь нет – их обучающийся добывает сам,
 * прогоняя режимы один за другим и занося числа в рабочую тетрадь.
 */
public class ResultsPanel extends javax.swing.JPanel {

    private final MainFrame owner;
    private final GeometryView geometry = new GeometryView();
    private final FitView fit = new FitView();
    private final ErrorView errors = new ErrorView();
    private final Model model = new Model();
    private JTable table;
    /** Рамка чертежа: её заголовок зависит от вида измерителя. */
    private javax.swing.border.TitledBorder geometryBorder;

    private InputData data;
    private Trajectory trajectory;
    private long seed = 20250101L;

    /**
     * Ошибки оценки, полученные в последовательных реализациях шума при
     * неизменном режиме обработки. По ним видно, что таблица содержит
     * характеристики ошибок, а не результат отдельного опыта: среднее
     * по опытам сходится к динамической составляющей, разброс – к случайной.
     */
    private final List<Double> trials = new ArrayList<>();
    /** То же для ошибок оценки радиальной скорости. */
    private final List<Double> speedTrials = new ArrayList<>();

    /**
     * Переключатель иллюстраций между дальностью и скоростью: виден при
     * измерении обоих параметров (при одной скорости иллюстрации строятся
     * для скорости, при одной дальности – для дальности).
     */
    private final javax.swing.JToggleButton btnPictureSpeed =
            new javax.swing.JToggleButton("показать скорость");
    private final javax.swing.JPanel pictureBar =
            new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
    /** Последняя показанная реализация и строка таблицы. */
    private Realization lastRealization;
    private ErrorRow lastRow;

    public ResultsPanel(MainFrame owner) {
        this.owner = owner;
        initComponents();
        customize();
    }

    private void customize() {
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    updatePictures();
                }
            }
        });
        scrTable.setViewportView(table);
        scrTable.setBorder(BorderFactory.createTitledBorder(
                "Ошибки оценивания в пяти выборках, равномерно смещённых "
                + "по интервалу измерений"));
        // перенос чисел в тетрадь мышью включается настройкой
        // notebook.transfer; по умолчанию его нет, и числа переписываются
        // от руки, как того требует смысл работы
        if (Settings.notebookTransfer()) {
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShowMenu(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShowMenu(e);
                }

                // вызов меню проверяется и при нажатии, и при отпускании:
                // в разных системах признак вызова приходит с разными
                // событиями (в Windows – при отпускании, в Linux – при нажатии)
                private void maybeShowMenu(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showTransferMenu(e);
                    }
                }
            });
        }
        lblWrite.setText(writeHint(InputData.Measured.RANGE));

        javax.swing.JPanel pictures = new javax.swing.JPanel(new GridLayout(1, 3, 8, 0));
        btnPictureSpeed.setFocusable(false);
        btnPictureSpeed.setToolTipText("Показать на рисунках дальность или радиальную скорость");
        btnPictureSpeed.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                btnPictureSpeed.setText(btnPictureSpeed.isSelected()
                        ? "показать дальность" : "показать скорость");
                render();
            }
        });
        pictureBar.add(btnPictureSpeed);
        pictureBar.setVisible(false);
        pnlPictures.setLayout(new BorderLayout());
        pnlPictures.add(pictureBar, BorderLayout.NORTH);
        pnlPictures.add(pictures, BorderLayout.CENTER);
        javax.swing.JPanel geometryPanel = wrap(geometry, GROUND_TITLE);
        geometryBorder = (javax.swing.border.TitledBorder) geometryPanel.getBorder();
        pictures.add(geometryPanel);
        pictures.add(wrap(fit, "Измерения и аппроксимирующий полином"));
        pictures.add(wrap(errors, "Ход ошибок по интервалу усреднения"));

        lblMode.setFont(lblMode.getFont().deriveFont(Font.BOLD));
        lblCaption.setText(CAPTION);
    }

    /**
     * Подсказка, какие числа выписывать в рабочую тетрадь: зависит от
     * измеряемых параметров. При измерении одной дальности текст прежний.
     */
    private static String writeHint(InputData.Measured measured) {
        String what;
        if (measured == InputData.Measured.VELOCITY) {
            what = "значения EVD, EVS и EV";
        } else if (measured == InputData.Measured.BOTH) {
            what = "значения ERD, ERS, ER и EVD, EVS, EV";
        } else {
            what = "значения ERD, ERS и ER";
        }
        return "<html><b>В рабочую тетрадь выпишите " + what + " из первой строки "
                + "таблицы</b> – так предписывает "
                + "руководство к работе (п. 4.3): для построения графиков "
                + "зависимостей ошибок от параметров режима обработки из "
                + "таблицы берётся каждый раз первая строка. Остальные строки "
                + "показывают, как те же ошибки меняются по интервалу "
                + "измерений."
                + (Settings.notebookTransfer()
                        ? "<br>Чтобы не переписывать числа от руки, щёлкните "
                        + "по строке правой кнопкой мыши и укажите пункт "
                        + "задания, в таблицу которого их занести."
                        : "")
                + "</html>";
    }

    /** Заголовки чертежа для наземного и бортового измерителя. */
    private static final String GROUND_TITLE = "Пролёт объекта над пунктом";
    private static final String AIRBORNE_TITLE = "Пролёт измерителя над объектом";

    private static javax.swing.JPanel wrap(javax.swing.JComponent view, String title) {
        javax.swing.JPanel p = new javax.swing.JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(view, BorderLayout.CENTER);
        return p;
    }

    private static final String CAPTION =
            "<html>Аппроксимирующая кривая проведена методом наименьших квадратов: "
            + "коэффициенты полинома определены из условия минимума суммы квадратов "
            + "отклонений измерений от кривой (серые отрезки). На правом рисунке "
            + "показан ход обеих составляющих ошибки по интервалу усреднения: "
            + "динамическая Δ(t) есть разность истинного значения параметра "
            + "(дальности или радиальной скорости) и его оценки при отсутствии "
            + "шума, коридор ±σ(t) отвечает случайной составляющей. "
            + "В момент привязки они равны ERD и ERS (для скорости – EVD и EVS) "
            + "выбранной строки таблицы; "
            + "при переносе привязки внутри выборки эти значения меняются, "
            + "и наименьшими они оказываются вблизи середины интервала.</html>";

    /** Вызывается при каждом показе экрана: считает таблицу заново. */
    public void onShown() {
        data = owner.getInputData();
        trajectory = Trajectory.of(data);
        model.setMeasured(data.getMeasured());
        model.setRows(new Processor(data, trajectory).table());
        lblMode.setText(describe(data));
        lblWrite.setText(writeHint(data.getMeasured()));
        if (model.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
        updatePictures();
        owner.setStatus("Выберите строку таблицы, чтобы увидеть эту выборку на графике");
    }

    private static String describe(InputData d) {
        return String.format(Locale.ROOT, "<html>Режим обработки: степень полинома m = %d, объём "
                + "выборки N = %d, шаг измерений Δt = %s с, привязка M0 = %d "
                + "(интервал усреднения %s с)</html>",
                d.getDegree(), d.getSampleSize(), num(d.getStep()), d.getAnchor(),
                num(d.averagingInterval()));
    }

    /** Перестраивает оба рисунка под выбранную строку таблицы. */
    private void updatePictures() {
        updatePictures(false);
    }

    /**
     * Перестраивает рисунки под выбранную строку таблицы.
     *
     * @param nextTrial true – показана очередная реализация шума при том же
     *                  режиме обработки, накопленная статистика сохраняется
     */
    private void updatePictures(boolean nextTrial) {
        int row = table == null ? -1 : table.getSelectedRow();
        if (data == null || row < 0 || row >= model.getRowCount()) {
            return;
        }
        ErrorRow r = model.row(row);
        lastRow = r;
        lastRealization = new Realization(data, trajectory, r.getWindowStart(), seed);
        if (!nextTrial) {
            trials.clear();
            speedTrials.clear();
        }
        trials.add(lastRealization.error());
        speedTrials.add(lastRealization.speedError());
        render();
    }

    /** Показывается ли на иллюстрациях радиальная скорость. */
    private boolean showsSpeed() {
        InputData.Measured m = data.getMeasured();
        if (m == InputData.Measured.VELOCITY) {
            return true;
        }
        return m == InputData.Measured.BOTH && btnPictureSpeed.isSelected();
    }

    /** Перерисовывает иллюстрации и сводку по уже смоделированным опытам. */
    private void render() {
        if (lastRealization == null || lastRow == null) {
            return;
        }
        boolean both = data.getMeasured() == InputData.Measured.BOTH;
        if (pictureBar.isVisible() != both) {
            pictureBar.setVisible(both);
            pnlPictures.revalidate();
        }
        boolean speed = showsSpeed();
        geometryBorder.setTitle(data.getMeasurer() == InputData.Measurer.AIRBORNE
                ? AIRBORNE_TITLE : GROUND_TITLE);
        pnlPictures.repaint();
        geometry.show(data, trajectory, lastRow.getTime());
        fit.show(lastRealization, speed);
        ErrorProfile profile = new ErrorProfile(data, trajectory, lastRow.getWindowStart());
        if (speed && Double.isNaN(profile.speedRandom(profile.getAnchorTime()))) {
            errors.showUnavailable("При m = 0 полином дальности\n"
                    + "не даёт оценки скорости");
        } else {
            errors.show(profile, speed);
        }
        lblNoise.setText(statistics(lastRow, speed));
    }

    /**
     * Сводка по накопленным реализациям: ошибка текущего опыта, среднее
     * и разброс по всем опытам в сопоставлении с расчётными динамической
     * и случайной ошибками (дальности или скорости – той величины, что
     * показана на рисунках).
     */
    private String statistics(ErrorRow r, boolean speed) {
        List<Double> list = speed ? speedTrials : trials;
        String unit = speed ? "м/с" : "м";
        String dName = speed ? "EVD" : "ERD";
        String sName = speed ? "EVS" : "ERS";
        double dCalc = speed ? r.getSpeedDynamic() : r.getRangeDynamic();
        double sCalc = speed ? r.getSpeedRandom() : r.getRangeRandom();
        int n = list.size();
        double current = list.get(n - 1);
        StringBuilder sb = new StringBuilder("<html>");
        if (Double.isNaN(current)) {
            sb.append("При m = 0 полином дальности не даёт оценки скорости.");
            return sb.append("</html>").toString();
        }
        sb.append(String.format(Locale.ROOT, "<b>Ошибка оценки %s в данной реализации: %+.4g %s.</b>"
                + " В таблице приведены характеристики ошибок, а не результат "
                + "отдельного опыта, поэтому от реализации шума они не зависят.",
                speed ? "скорости" : "дальности", current, unit));
        if (n < 2) {
            sb.append("&nbsp; Нажмите «Другая реализация шума», чтобы увидеть, "
                    + "как эта ошибка меняется от опыта к опыту при неизменных "
                    + "характеристиках в таблице.");
        } else {
            double sum = 0;
            for (double e : list) {
                sum += e;
            }
            double mean = sum / n;
            double var = 0;
            for (double e : list) {
                var += (e - mean) * (e - mean);
            }
            double sd = Math.sqrt(var / (n - 1));
            sb.append(String.format(Locale.ROOT, "&nbsp; По %d опытам: среднее %+.4g %s "
                    + "(расчётная %s = %.4g %s), разброс %.4g %s "
                    + "(расчётная %s = %.4g %s).",
                    n, mean, unit, dName, dCalc, unit, sd, unit, sName, sCalc, unit));
        }
        if (!r.isInsideInterval()) {
            sb.append("&nbsp; Выборка выходит за интервал измерений.");
        }
        return sb.append("</html>").toString();
    }

    /**
     * Меню переноса чисел выбранной строки в рабочую тетрадь.
     *
     * Программа не знает, какой из четырёх опытов проводится: один и тот же
     * режим обработки может относиться к разным пунктам задания (например,
     * m = 3, N = 49, Δt = 1 с, M0 = 25 подходит и под пункт б, и под пункт г).
     * Поэтому пункт выбирает обучающийся, а программа лишь помечает тот,
     * которому заданный режим отвечает по закреплённым параметрам.
     */
    private void showTransferMenu(MouseEvent e) {
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0 || data == null) {
            return;
        }
        table.setRowSelectionInterval(row, row);
        final ErrorRow r = model.row(row);
        final int line = row + 1;

        JPopupMenu menu = new JPopupMenu();
        JMenuItem header = new JMenuItem(String.format(Locale.ROOT,
                "Строка %d (t = %s с): %s – занести в пункт:",
                line, num(r.getTime()), numbers(r)));
        header.setEnabled(false);
        menu.add(header);
        menu.addSeparator();

        int chosen = owner.getNotebook().getChosenDegree();
        for (final Notebook.Item item : Notebook.Item.values()) {
            boolean fits = item.matches(data, chosen);
            String text = "пункт " + item.getLetter() + ": "
                    + item.getParameter() + " = " + num(item.parameterValue(data))
                    + (fits ? "   – заданный режим отвечает этому пункту" : "");
            JMenuItem mi = new JMenuItem(text);
            if (fits) {
                mi.setFont(mi.getFont().deriveFont(Font.BOLD));
            }
            mi.addActionListener(new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent ev) {
                    transfer(item, line, r);
                }
            });
            menu.add(mi);
        }
        menu.show(table, e.getX(), e.getY());
    }

    /**
     * Заносит три числа выбранной строки в таблицу указанного пункта задания.
     *
     * Строка тетради определяется значением того параметра, который в этом
     * пункте меняется: если обучающийся взял значение, не предусмотренное
     * заданием, строка для него заводится. Номер строки таблицы результатов
     * запоминается в пункте: по нему потом идёт сверка занесённых чисел.
     */
    private void transfer(Notebook.Item item, int line, ErrorRow r) {
        double value = item.parameterValue(data);
        Notebook.Page page = owner.getNotebook().page(item);
        Notebook.Line target = page.lineFor(value);
        for (Notebook.Quantity q : Notebook.Quantity.of(data.getMeasured())) {
            if (q == Notebook.Quantity.SPEED) {
                // при m = 0 ошибки скорости не определены: не заносятся
                if (q.isDefined(data.getDegree(), data.getMeasured()) && !Double.isNaN(r.getSpeedTotal())) {
                    target.set(q, r.getSpeedDynamic(), r.getSpeedRandom(), r.getSpeedTotal());
                }
            } else {
                target.set(q, r.getRangeDynamic(), r.getRangeRandom(), r.getRangeTotal());
            }
        }
        target.setSuspicious(false);
        // вместе с числами запоминается режим, при котором они получены:
        // именно он пойдёт в отчёт, а не предписанный пунктом задания
        target.setMode(data);
        page.setSourceRow(line);
        owner.setStatus(String.format(Locale.ROOT,
                "В тетрадь, пункт %s: при %s = %s занесены %s "
                + "(строка %d таблицы)",
                item.getLetter(), item.getParameter(), num(value),
                numbers(r), line));
    }

    /**
     * Числа строки, которые выписываются в тетрадь при текущих измеряемых
     * параметрах: «ERD = …, ERS = …, ER = …» и/или то же для скорости.
     */
    private String numbers(ErrorRow r) {
        StringBuilder sb = new StringBuilder();
        for (Notebook.Quantity q : Notebook.Quantity.of(data.getMeasured())) {
            boolean speed = q == Notebook.Quantity.SPEED;
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(q.getDynamicName()).append(" = ")
              .append(num(speed ? r.getSpeedDynamic() : r.getRangeDynamic()))
              .append(", ").append(q.getRandomName()).append(" = ")
              .append(num(speed ? r.getSpeedRandom() : r.getRangeRandom()))
              .append(", ").append(q.getTotalName()).append(" = ")
              .append(num(speed ? r.getSpeedTotal() : r.getRangeTotal()));
        }
        return sb.toString();
    }

    private void btnNoiseActionPerformed(java.awt.event.ActionEvent evt) {
        seed = new Random().nextLong();
        updatePictures(true);
        owner.setStatus("Другая реализация шума: отсчёты и аппроксимирующий "
                + "полином изменились, характеристики ошибок в таблице – нет");
    }

    private void btnBackActionPerformed(java.awt.event.ActionEvent evt) {
        owner.showCard(MainFrame.CARD_INPUT);
    }

    /**
     * Меняет параметры режима обработки (степень, объём выборки, шаг,
     * привязка), не уходя с экрана результатов: диалог удобнее, чем
     * возврат на экран ввода данных ради одной цифры.
     *
     * Новые значения сразу передаются в поля экрана ввода данных
     * ({@link MainFrame#syncModeParams(InputData)}), поэтому при переходе
     * туда кнопкой «К вводу данных» там уже видны изменённые параметры.
     */
    private void btnModeParamsActionPerformed(java.awt.event.ActionEvent evt) {
        if (data == null) {
            return;
        }
        InputData updated = ModeParamsDialog.show(this, data);
        if (updated == null) {
            return;
        }
        data = updated;
        owner.setInputData(data);
        owner.syncModeParams(data);
        trajectory = Trajectory.of(data);
        model.setRows(new Processor(data, trajectory).table());
        lblMode.setText(describe(data));
        if (model.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
        updatePictures();
        owner.setStatus("Параметры режима обработки изменены: таблица и графики пересчитаны.");
    }

    private void btnHelpActionPerformed(java.awt.event.ActionEvent evt) {
        HelpFrame.show(this);
    }

    private void btnTaskActionPerformed(java.awt.event.ActionEvent evt) {
        TaskFrame.show(this);
    }

    private void btnNotebookActionPerformed(java.awt.event.ActionEvent evt) {
        owner.showCard(MainFrame.CARD_NOTEBOOK);
    }

    /** Число в удобном для чтения виде: без лишних нулей. */
    private static String num(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "–";
        }
        if (v == Math.rint(v) && Math.abs(v) < 1e7) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        double a = Math.abs(v);
        if (a >= 1e5 || (a < 1e-3 && a > 0)) {
            return String.format(Locale.ROOT, "%.3e", v);
        }
        return String.format(Locale.ROOT, "%.4g", v);
    }

    /**
     * Модель таблицы ошибок.
     *
     * При измерении одной радиальной скорости дальность не наблюдаема:
     * постоянная составляющая полинома (α0) измерениями скорости не
     * определяется, и ошибки дальности получаются бесконечными. Поэтому
     * в этом случае столбцы дальности не показываются.
     */
    private static class Model extends AbstractTableModel {

        private static final String[] COLUMNS = {
            "№", "t, с", "ERD, м", "ERS, м", "ER, м", "EVD, м/с", "EVS, м/с", "EV, м/с"
        };

        /** Номера показанных столбцов в полном наборе COLUMNS. */
        private int[] shown = {0, 1, 2, 3, 4, 5, 6, 7};

        private List<ErrorRow> rows;

        void setRows(List<ErrorRow> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        /** Задаёт состав столбцов по измеряемым параметрам. */
        void setMeasured(InputData.Measured measured) {
            int[] next = measured == InputData.Measured.VELOCITY
                    ? new int[] {0, 1, 5, 6, 7}
                    : new int[] {0, 1, 2, 3, 4, 5, 6, 7};
            if (!java.util.Arrays.equals(next, shown)) {
                shown = next;
                fireTableStructureChanged();
            }
        }

        ErrorRow row(int i) {
            return rows.get(i);
        }

        @Override
        public int getRowCount() {
            return rows == null ? 0 : rows.size();
        }

        @Override
        public int getColumnCount() {
            return shown.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNS[shown[c]];
        }

        @Override
        public Object getValueAt(int r, int c) {
            ErrorRow e = rows.get(r);
            switch (shown[c]) {
                case 0: return String.valueOf(r + 1);
                case 1: return num(e.getTime());
                case 2: return num(e.getRangeDynamic());
                case 3: return num(e.getRangeRandom());
                case 4: return num(e.getRangeTotal());
                case 5: return num(e.getSpeedDynamic());
                case 6: return num(e.getSpeedRandom());
                default: return num(e.getSpeedTotal());
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        lblMode = new javax.swing.JLabel();
        pnlModeButtons = new javax.swing.JPanel();
        btnModeParams = new javax.swing.JButton();
        scrTable = new javax.swing.JScrollPane();
        lblWrite = new javax.swing.JLabel();
        pnlPictures = new javax.swing.JPanel();
        lblCaption = new javax.swing.JLabel();
        lblNoise = new javax.swing.JLabel();
        pnlNoiseButtons = new javax.swing.JPanel();
        btnNoise = new javax.swing.JButton();
        btnBack = new javax.swing.JButton();
        btnHelp = new javax.swing.JButton();
        btnTask = new javax.swing.JButton();
        btnNotebook = new javax.swing.JButton();

        lblMode.setText("Режим обработки");

        btnModeParams.setText("Параметры режима обработки");
        btnModeParams.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnModeParamsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlModeButtonsLayout = new javax.swing.GroupLayout(pnlModeButtons);
        pnlModeButtons.setLayout(pnlModeButtonsLayout);
        pnlModeButtonsLayout.setHorizontalGroup(
            pnlModeButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlModeButtonsLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(btnModeParams, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        pnlModeButtonsLayout.setVerticalGroup(
            pnlModeButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(btnModeParams, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        javax.swing.GroupLayout pnlPicturesLayout = new javax.swing.GroupLayout(pnlPictures);
        pnlPictures.setLayout(pnlPicturesLayout);
        pnlPicturesLayout.setHorizontalGroup(
            pnlPicturesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 860, Short.MAX_VALUE)
        );
        pnlPicturesLayout.setVerticalGroup(
            pnlPicturesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 320, Short.MAX_VALUE)
        );

        lblWrite.setText(" ");

        lblCaption.setText(" ");

        lblNoise.setText(" ");

        btnBack.setText("К вводу данных");
        btnBack.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBackActionPerformed(evt);
            }
        });

        btnNoise.setText("Другая реализация шума");
        btnNoise.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNoiseActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlNoiseButtonsLayout = new javax.swing.GroupLayout(pnlNoiseButtons);
        pnlNoiseButtons.setLayout(pnlNoiseButtonsLayout);
        pnlNoiseButtonsLayout.setHorizontalGroup(
            pnlNoiseButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlNoiseButtonsLayout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(btnNoise, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        pnlNoiseButtonsLayout.setVerticalGroup(
            pnlNoiseButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(btnNoise, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        btnHelp.setText("Сведения из теории");
        btnHelp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnHelpActionPerformed(evt);
            }
        });

        btnTask.setText("Задание на работу");
        btnTask.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTaskActionPerformed(evt);
            }
        });

        btnNotebook.setText("В рабочую тетрадь");
        btnNotebook.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNotebookActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblMode, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlModeButtons, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(scrTable, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblWrite, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlPictures, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblCaption, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblNoise, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlNoiseButtons, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnBack, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnTask, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnHelp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnNotebook, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblMode)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnlModeButtons, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scrTable, javax.swing.GroupLayout.PREFERRED_SIZE, 175, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblWrite)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnlPictures, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblCaption)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblNoise)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnlNoiseButtons, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnBack)
                    .addComponent(btnTask)
                    .addComponent(btnHelp)
                    .addComponent(btnNotebook))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnBack;
    private javax.swing.JButton btnHelp;
    private javax.swing.JButton btnModeParams;
    private javax.swing.JButton btnNoise;
    private javax.swing.JButton btnNotebook;
    private javax.swing.JButton btnTask;
    private javax.swing.JLabel lblCaption;
    private javax.swing.JLabel lblMode;
    private javax.swing.JLabel lblNoise;
    private javax.swing.JLabel lblWrite;
    private javax.swing.JPanel pnlModeButtons;
    private javax.swing.JPanel pnlNoiseButtons;
    private javax.swing.JPanel pnlPictures;
    private javax.swing.JScrollPane scrTable;
    // End of variables declaration//GEN-END:variables
}
