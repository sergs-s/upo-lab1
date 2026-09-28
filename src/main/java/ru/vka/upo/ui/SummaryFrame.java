package ru.vka.upo.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import ru.vka.upo.core.ErrorRow;
import ru.vka.upo.core.Processor;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Notebook;

/**
 * Готовые зависимости ошибок от параметров режима обработки по всем четырём
 * пунктам задания: четыре графика и четыре таблицы сразу.
 *
 * Экран только для преподавателя. Обучающемуся он недоступен намеренно:
 * смысл работы как раз в том, чтобы прогонять режимы по одному, выписывать
 * числа и строить эти зависимости самому.
 *
 * Отдельное окно (JFrame), а не JDialog: так оно разворачивается и
 * восстанавливается штатной кнопкой в заголовке, как любое обычное окно
 * Windows, без самодельной кнопки «во весь экран». При этом окно ведёт себя
 * как модальное: одновременно открыт только один экземпляр, главное окно
 * программы на время его показа недоступно (setEnabled(false)) и снова
 * становится доступным, как только это окно закрыто. Если преподаватель
 * успевает открыть его повторно с новыми данными (например, после пересчёта
 * по другому варианту) – окно не плодится, а обновляет содержимое.
 *
 * Расчёт ведётся ровно так, как предписано руководством к работе:
 * <ul>
 *   <li>числа берутся из первой строки таблицы результатов – именно её
 *       руководство велит заносить в тетрадь (п. 4.3);</li>
 *   <li>степень полинома для пунктов б, в и г не задаётся произвольно,
 *       а определяется по пункту а: берётся та, при которой полная ошибка
 *       дальности оказалась наименьшей (при измерении одной радиальной
 *       скорости – полная ошибка скорости).</li>
 * </ul>
 *
 * Зависимости строятся по ошибкам той величины, что измеряется: дальности
 * или радиальной скорости. При измерении обоих параметров над графиками
 * есть переключатель «ошибки дальности / ошибки скорости», действующий
 * сразу на все четыре графика и таблицы: величины с разными единицами на
 * одном поле не совмещаются.
 */
public class SummaryFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Цвета кривых те же, что и в рабочей тетради: глаз к ним привыкает. */
    private static final Color DYNAMIC = new Color(0x1F, 0x4E, 0x79);
    private static final Color RANDOM = new Color(0x2E, 0x86, 0x4B);
    private static final Color TOTAL = new Color(0xC0, 0x39, 0x2B);

    /** Номер строки таблицы результатов, из которой берутся числа. */
    private static final int SOURCE_ROW = 1;

    /** Единственный открытый экземпляр окна – второй не заводится. */
    private static SummaryFrame instance;

    /**
     * Запоминается отдельно: у JFrame нет настоящего AWT-владельца
     * (getOwner() тут всегда вернёт null, т.к. конструктор не вызывает
     * super(owner)), поэтому признак полноэкранного режима параметром
     * owner конструктора не передать в другие методы иначе как полем.
     */
    private boolean fullScreenOwner;

    private final JLabel head = new JLabel();
    private final JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
    /** Переключатель величины для всех четырёх графиков сразу. */
    private final JToggleButton btnQuantity = new JToggleButton("ошибки скорости");

    /** Данные, по которым построено окно: нужны при переключении величины. */
    private InputData base;
    private Processor.Mode mode;
    private boolean middleInC;
    private int degree;
    /** Первые строки таблиц результатов по пунктам: {значение параметра, строка}. */
    private final Map<Notebook.Item, List<Object[]>> computed =
            new EnumMap<Notebook.Item, List<Object[]>>(Notebook.Item.class);
    /** Выбранный линейный масштаб оси ошибок по пунктам. */
    private final Map<Notebook.Item, Boolean> linear =
            new EnumMap<Notebook.Item, Boolean>(Notebook.Item.class);

    private SummaryFrame(Window owner) {
        super("Зависимости ошибок от параметров режима обработки");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        grid.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        head.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));

        btnQuantity.setFocusable(false);
        btnQuantity.setToolTipText("Переключить все графики и таблицы: "
                + "ошибки дальности / ошибки скорости");
        btnQuantity.addActionListener(e -> {
            btnQuantity.setText(btnQuantity.isSelected()
                    ? "ошибки дальности" : "ошибки скорости");
            rebuild();
        });
        JPanel quantityBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        quantityBar.add(btnQuantity);
        JPanel north = new JPanel(new BorderLayout());
        north.add(head, BorderLayout.CENTER);
        north.add(quantityBar, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(north, BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);

        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1240, screen.width - 80), Math.min(820, screen.height - 80));
        setLocationRelativeTo(owner);
        Emblem.applyTo(this);

        // окно модальное: сворачивать его незачем, а свёрнутое при
        // отключённом главном окне программы выглядело бы так, будто
        // программа зависла, поэтому попытку свернуть сразу отменяем
        addWindowStateListener(e -> {
            if ((e.getNewState() & Frame.ICONIFIED) != 0) {
                setExtendedState(e.getOldState());
                javax.swing.JOptionPane.showMessageDialog(this,
                        "Это окно нельзя свернуть, можно только закрыть.",
                        "Предварительная обработка",
                        javax.swing.JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // если сама программа развёрнута на весь экран (настройка
        // window.fullscreen = true), это окно тоже открывается без рамки
        // и сразу занимает весь экран; поскольку штатных кнопок заголовка
        // в этом случае не будет, добавляем свою кнопку «Закрыть»
        if (owner instanceof MainFrame && ((MainFrame) owner).isFullScreenMode()) {
            fullScreenOwner = true;
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            JButton close = new JButton("Закрыть");
            close.addActionListener(e -> dispose());
            JPanel closeBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            closeBar.add(close);
            add(closeBar, BorderLayout.SOUTH);
        }
    }

    /**
     * Показывает окно с зависимостями по всем пунктам задания. Если оно уже
     * открыто, второй раз не создаётся – в уже открытом окне просто
     * обновляются графики и таблицы по переданным данным.
     */
    public static void show(Component parent, InputData base, Processor.Mode mode,
            boolean middleInC) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        if (instance != null && instance.isDisplayable()) {
            instance.updateData(base, mode, middleInC);
            instance.bringToFront();
            return;
        }
        final Window ownerFinal = owner;
        instance = new SummaryFrame(owner);
        instance.updateData(base, mode, middleInC);
        instance.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                instance = null;
                if (ownerFinal != null) {
                    ownerFinal.setEnabled(true);
                    ownerFinal.toFront();
                }
            }
        });
        if (ownerFinal != null) {
            ownerFinal.setEnabled(false);
        }
        instance.setVisible(true);
        instance.bringToFront();
    }

    /**
     * Принудительно выводит окно поверх владельца. В обычном режиме –
     * кратким включением/выключением "поверх всех окон": этого достаточно,
     * чтобы операционная система вывела окно вперёд, а сам флаг не остаётся
     * висеть постоянно (иначе стало бы невозможно переключиться на другую
     * программу). В полноэкранном же режиме главное окно программы само
     * держит "поверх всех окон" постоянно (см. MainFrame.customize()) –
     * против этого временное включение бессильно: как только оно снимается,
     * главное окно тут же перекрывает открытое поверх него. Поэтому здесь
     * флаг оставляется включённым на всё время показа окна.
     */
    private void bringToFront() {
        setAlwaysOnTop(true);
        toFront();
        requestFocus();
        if (!fullScreenOwner) {
            setAlwaysOnTop(false);
        }
    }

    /** Пересчитывает и перерисовывает содержимое окна по новым данным. */
    private void updateData(InputData base, Processor.Mode mode, boolean middleInC) {
        this.base = base;
        this.mode = mode;
        this.middleInC = middleInC;
        this.degree = bestDegree(base, mode, middleInC);

        computed.clear();
        for (Notebook.Item item : Notebook.Item.values()) {
            List<Object[]> rows = new ArrayList<Object[]>();
            for (double value : item.getDefaults()) {
                ErrorRow row = compute(base, mode, item, value, degree, middleInC);
                if (row != null) {
                    rows.add(new Object[] {value, row});
                }
            }
            computed.put(item, rows);
        }
        btnQuantity.setVisible(quantities().size() > 1);
        rebuild();
    }

    /** Величины, ошибки которых оцениваются при текущих исходных данных. */
    private List<Notebook.Quantity> quantities() {
        return Notebook.Quantity.of(base == null ? null : base.getMeasured());
    }

    /** Величина, по ошибкам которой построены графики и таблицы. */
    private Notebook.Quantity shown() {
        List<Notebook.Quantity> q = quantities();
        if (q.size() == 1) {
            return q.get(0);
        }
        return btnQuantity.isSelected() ? Notebook.Quantity.SPEED : Notebook.Quantity.RANGE;
    }

    /** Перестраивает графики и таблицы по уже рассчитанным числам. */
    private void rebuild() {
        Notebook.Quantity q = shown();
        Notebook.Quantity choice = Notebook.Quantity.forDegreeChoice(
                base == null ? null : base.getMeasured());

        grid.removeAll();
        for (Notebook.Item item : Notebook.Item.values()) {
            grid.add(page(item, q));
        }

        head.setText("<html>Зависимости построены по <b>ошибкам оценивания "
                + q.getGenitive() + "</b> (" + q.getDynamicName() + ", "
                + q.getRandomName() + ", " + q.getTotalName() + ", "
                + q.getUnit() + "). Числа взяты из первой строки таблицы "
                + "результатов, как предписано руководством к работе (п. 4.3). "
                + "Степень полинома для пунктов б, в и г определена по пункту а "
                + "по наименьшей полной ошибке " + choice.getGenitive()
                + " " + choice.getTotalName() + ": <b>m = " + degree
                + "</b>. Способ вычисления динамической ошибки: " + mode
                + ". В пункте в) момент привязки "
                + (middleInC ? "переносится в середину выборки"
                        : "остаётся в начале интервала усреднения (M0 = 1)")
                + ".</html>");

        grid.revalidate();
        grid.repaint();
    }

    /**
     * Степень полинома, при которой полная ошибка наименьшая: ошибка
     * дальности ER, а при измерении одной радиальной скорости – ошибка
     * скорости EV (см. {@link Notebook.Quantity#forDegreeChoice}).
     *
     * Это и есть содержание пункта а задания: обучающийся прогоняет степени
     * одну за другой и выбирает лучшую. Здесь тот же перебор выполняется
     * сразу, по тем же значениям степени, что предусмотрены заданием,
     * и по той же первой строке таблицы.
     *
     * Если ни одно сочетание параметров не удалось рассчитать (чего при
     * значениях из задания не бывает), остаётся степень 2 – та, что задана
     * в исходных данных по умолчанию.
     */
    private static int bestDegree(InputData base, Processor.Mode mode,
            boolean middleInC) {
        int best = 2;
        double least = Double.POSITIVE_INFINITY;
        boolean bySpeed = Notebook.Quantity.forDegreeChoice(base.getMeasured())
                == Notebook.Quantity.SPEED;
        for (double value : Notebook.Item.A.getDefaults()) {
            ErrorRow row = compute(base, mode, Notebook.Item.A, value, 2, middleInC);
            if (row == null) {
                continue;
            }
            // при m = 0 ошибка скорости не определена (NaN) и в выбор не идёт
            double total = bySpeed ? row.getSpeedTotal() : row.getRangeTotal();
            if (!Double.isNaN(total) && total < least) {
                least = total;
                best = (int) Math.round(value);
            }
        }
        return best;
    }

    /**
     * Первая строка таблицы результатов при заданном значении изменяемого
     * параметра или null, если такое сочетание параметров недопустимо
     * (например, объём выборки меньше числа коэффициентов полинома).
     */
    private static ErrorRow compute(InputData base, Processor.Mode mode,
            Notebook.Item item, double value, int degree, boolean middleInC) {
        try {
            InputData d = item.apply(base, value, degree, middleInC);
            List<ErrorRow> rows = new Processor(d).setMode(mode).table();
            return rows.size() < SOURCE_ROW ? null : rows.get(SOURCE_ROW - 1);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** График и таблица одного пункта задания по ошибкам величины q. */
    private JPanel page(Notebook.Item item, Notebook.Quantity q) {
        boolean speed = q == Notebook.Quantity.SPEED;
        List<double[]> data = new ArrayList<double[]>();
        for (Object[] c : computed.get(item)) {
            double value = (Double) c[0];
            ErrorRow row = (ErrorRow) c[1];
            data.add(new double[] {value,
                speed ? row.getSpeedDynamic() : row.getRangeDynamic(),
                speed ? row.getSpeedRandom() : row.getRangeRandom(),
                speed ? row.getSpeedTotal() : row.getRangeTotal()});
        }

        boolean lin = Boolean.TRUE.equals(linear.get(item));
        ChartView chart = new ChartView();
        chart.setAxes(item.getParameter(), q.axisTitle(), item.isLogParameter(), !lin);
        ChartView.Series sd = new ChartView.Series("динамическая " + q.getDynamicName(), DYNAMIC);
        ChartView.Series sr = new ChartView.Series("случайная " + q.getRandomName(), RANDOM);
        ChartView.Series st = new ChartView.Series("полная " + q.getTotalName(), TOTAL);
        for (double[] p : data) {
            // ошибки скорости при m = 0 не определены: точку пропускаем
            if (Double.isNaN(p[3])) {
                continue;
            }
            sd.add(p[0], p[1], false);
            sr.add(p[0], p[2], false);
            st.add(p[0], p[3], false);
        }
        chart.add(sd);
        chart.add(sr);
        chart.add(st);
        chart.markMinimum(st);

        JTable table = new JTable(new Model(item, q, data));
        table.setRowHeight(18);
        table.getTableHeader().setReorderingAllowed(false);
        table.setEnabled(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(300, 124));

        JLabel caption = new JLabel("<html>" + item.fixedDescription(degree) + "</html>");
        caption.setFont(caption.getFont().deriveFont(Font.PLAIN, 11f));
        caption.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(caption, BorderLayout.NORTH);
        bottom.add(scroll, BorderLayout.CENTER);

        // переключатель масштаба оси ошибок: по умолчанию логарифмический
        // (значения различаются на порядки), но преподавателю может быть
        // удобнее посмотреть и в привычном линейном виде
        JToggleButton btnScale = new JToggleButton(
                lin ? "логарифмический масштаб" : "линейный масштаб");
        btnScale.setSelected(lin);
        btnScale.setFocusable(false);
        btnScale.setFont(btnScale.getFont().deriveFont(10f));
        btnScale.setToolTipText("Переключить масштаб оси ошибок: "
                + "логарифмический / линейный");
        btnScale.addActionListener(e -> {
            boolean linearNow = btnScale.isSelected();
            // выбор масштаба запоминается: он сохраняется и при
            // переключении графиков с дальности на скорость
            linear.put(item, linearNow);
            chart.setLogY(!linearNow);
            btnScale.setText(linearNow ? "логарифмический масштаб" : "линейный масштаб");
        });
        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        top.add(btnScale);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Пункт " + item.getLetter()
                + ": зависимость ошибок от параметра " + item.getParameter()
                + " – " + item.getParameterTitle()));
        panel.add(top, BorderLayout.NORTH);
        panel.add(chart, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    /** Таблица одного пункта: значение параметра и три ошибки. */
    private static class Model extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final Notebook.Item item;
        private final Notebook.Quantity quantity;
        private final List<double[]> rows;

        Model(Notebook.Item item, Notebook.Quantity quantity, List<double[]> rows) {
            this.item = item;
            this.quantity = quantity;
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int c) {
            switch (c) {
                case 0: return item.getParameter();
                case 1: return quantity.getDynamicName() + ", " + quantity.getUnit();
                case 2: return quantity.getRandomName() + ", " + quantity.getUnit();
                default: return quantity.getTotalName() + ", " + quantity.getUnit();
            }
        }

        @Override
        public Object getValueAt(int r, int c) {
            return num(rows.get(r)[c]);
        }

        private static String num(double v) {
            if (Double.isNaN(v)) {
                // ошибка скорости при m = 0 не определена
                return "–";
            }
            if (v == Math.rint(v) && Math.abs(v) < 1e6) {
                return String.format(Locale.ROOT, "%.0f", v);
            }
            return String.format(Locale.ROOT, "%.4g", v);
        }
    }
}
