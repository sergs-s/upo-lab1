package ru.vka.upo.ui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import ru.vka.upo.core.ErrorRow;
import ru.vka.upo.core.Processor;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Settings;
import ru.vka.upo.model.Notebook;
import ru.vka.upo.model.VariantTable;

/**
 * Экран преподавателя: расчёт всех пунктов задания сразу.
 *
 * Обучающемуся этот экран недоступен: там смысл работы как раз в том, чтобы
 * прогонять режимы по одному и снимать числа самому. Преподавателю же нужна
 * готовая таблица – проверить работу или подготовиться к занятию.
 *
 * Предусмотрен режим точного воспроизведения прежней программы: он нужен
 * на время отработки, для сверки с эталонными расчётами, и по её окончании
 * будет убран.
 */
public class TeacherPanel extends javax.swing.JPanel {

    private final MainFrame owner;
    private final Model model = new Model();

    /**
     * Строка списка «Вариант» для исходных данных, заданных вручную на экране
     * ввода (кнопка «Ввод данных вручную», возврат – «Автоматический расчёт»).
     * Появляется в списке после первого такого возврата.
     */
    private static final String MANUAL = "данные, заданные вручную";

    /** Исходные данные, заданные вручную, или null, если их не задавали. */
    private InputData manual;

    public TeacherPanel(MainFrame owner) {
        this.owner = owner;
        initComponents();
        customize();
    }

    private void customize() {
        DefaultComboBoxModel<Object> vm = new DefaultComboBoxModel<Object>();
        for (VariantTable.Variant v : VariantTable.variants()) {
            vm.addElement(v);
        }
        cmbVariant.setModel(vm);

        scrTable.setBorder(BorderFactory.createTitledBorder("Расчёт всех пунктов задания"));
        scrTable.setViewportView(new javax.swing.JLabel(
                "Нажмите «Рассчитать все пункты»", javax.swing.SwingConstants.CENTER));
        lblInfo.setText("<html>Все четыре пункта задания, по пять значений изменяемого "
                + "параметра в каждом и по пять выборок на расчёт. Способ вычисления "
                + "динамической составляющей выбирается для сопоставления с прежней "
                + "программой; при запуске он берётся из настроечного файла "
                + "(настройка error.mode). Числа сгруппированы по пунктам и значениям "
                + "изменяемого параметра – каждой группе своя маленькая таблица; "
                + "список прокручивается вниз.</html>");
        cmbMode.setModel(new DefaultComboBoxModel<Object>(Processor.Mode.values()));
        cmbMode.setSelectedItem(Settings.errorMode());
        cmbAnchorC.setModel(new DefaultComboBoxModel<Object>(AnchorC.values()));
        cmbAnchorC.setSelectedItem(Settings.anchorMiddleInItemC()
                ? AnchorC.MIDDLE : AnchorC.START);
        cmbAnchorC.setToolTipText("Куда ставится момент привязки M0 в пункте в), "
                + "где меняется объём выборки N; при запуске берётся из "
                + "настроечного файла (настройка item.c.anchor)");
        lblCount.setText(" ");
    }

    /**
     * Куда ставится момент привязки в пункте в) при автоматическом расчёте.
     *
     * В этом пункте меняется объём выборки N, а момент привязки M0 числом
     * не закреплён: по руководству к работе он переносится в середину
     * выборки и меняется вместе с N. Привязка к началу интервала усреднения
     * (M0 = 1) оставлена для сопоставления с прежней программой. Значение
     * при запуске берётся из настройки item.c.anchor (см. Settings).
     */
    private enum AnchorC {

        MIDDLE("к середине выборки (по руководству)"),
        START("к началу интервала усреднения, M0 = 1");

        private final String title;

        AnchorC(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** Выбранная в списке привязка в пункте в): true – к середине выборки. */
    private boolean anchorMiddleInC() {
        return cmbAnchorC.getSelectedItem() != AnchorC.START;
    }

    /** Вызывается при каждом показе экрана. */
    public void onShown() {
        owner.setStatus("Режим преподавателя: расчёт всех пунктов задания сразу");
    }

    /**
     * Принимает исходные данные, заданные вручную на экране ввода: они
     * добавляются в список «Вариант» отдельной строкой и выбираются в нём,
     * так что расчёт всех пунктов и зависимости строятся именно по ним.
     */
    public void setManualData(InputData d) {
        manual = d.clone();
        DefaultComboBoxModel<Object> vm = (DefaultComboBoxModel<Object>) cmbVariant.getModel();
        if (vm.getIndexOf(MANUAL) < 0) {
            vm.insertElementAt(MANUAL, 0);
        }
        cmbVariant.setSelectedItem(MANUAL);
    }

    /**
     * Исходные данные для расчёта по выбранной строке списка «Вариант»
     * или null, если ничего не выбрано.
     */
    private InputData selectedData() {
        Object sel = cmbVariant.getSelectedItem();
        if (sel instanceof VariantTable.Variant) {
            return ((VariantTable.Variant) sel).toInputData();
        }
        if (MANUAL.equals(sel) && manual != null) {
            return manual.clone();
        }
        return null;
    }

    private void btnComputeActionPerformed(java.awt.event.ActionEvent evt) {
        InputData base = selectedData();
        if (base == null) {
            return;
        }
        Object modeSel = cmbMode.getSelectedItem();
        Processor.Mode mode = modeSel instanceof Processor.Mode
                ? (Processor.Mode) modeSel : Processor.Mode.ANCHOR;
        // степень, которую обучающийся нашёл бы в пункте а; для сводной таблицы
        // берём ту, что предусмотрена заданием как исходная
        int chosen = 2;
        List<Row> rows = new ArrayList<>();
        for (Notebook.Item item : Notebook.Item.values()) {
            for (double value : item.getDefaults()) {
                InputData d = item.apply(base, value, chosen, anchorMiddleInC());
                List<ErrorRow> table1;
                try {
                    table1 = new Processor(d).setMode(mode).table();
                } catch (RuntimeException e) {
                    continue;
                }
                for (int i = 0; i < table1.size(); i++) {
                    rows.add(new Row(item, value, i + 1, table1.get(i), d.getAnchor()));
                }
            }
        }
        model.setRows(rows);
        owner.setInputData(base);
        scrTable.setViewportView(buildGroupedView(rows));
        lblCount.setText("Строк: " + rows.size()
                + ", динамическая ошибка – " + mode
                + ", привязка в пункте в) – " + cmbAnchorC.getSelectedItem());
    }

    /**
     * Строит вид результатов для показа на экране: одна маленькая таблица
     * на каждое сочетание пункта задания и значения изменяемого параметра
     * (пять таких таблиц на пункт, четыре пункта – двадцать таблиц), друг
     * под другом, с общей прокруткой. Строки {@code rows} уже идут именно
     * в таком порядке (см. {@link #btnComputeActionPerformed}), поэтому
     * границы групп находятся простым сравнением с предыдущей строкой.
     *
     * Столбцы «Пункт», «Параметр», «Значение» и «Строка» здесь не нужны –
     * то, что они показывали, вынесено в заголовок группы. Полный плоский
     * вариант со всеми столбцами по-прежнему доступен через «Сохранить
     * в CSV» ({@link #model}).
     */
    private JPanel buildGroupedView(List<Row> rows) {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        int i = 0;
        while (i < rows.size()) {
            Row first = rows.get(i);
            List<Row> group = new ArrayList<>();
            while (i < rows.size() && rows.get(i).item == first.item
                    && rows.get(i).value == first.value) {
                group.add(rows.get(i));
                i++;
            }

            StringBuilder headerText = new StringBuilder("Пункт ")
                    .append(first.item.getLetter()).append(')')
                    .append(", параметр ").append(first.item.getParameter())
                    .append(" = ").append(Model.num(first.value));
            if (first.item == Notebook.Item.C) {
                // в этом пункте привязка не закреплена: по заданию она
                // переносится в середину выборки и меняется вместе с N,
                // поэтому показывается явно – см. Row.anchor
                headerText.append(", M0 = ").append(first.anchor);
            }
            javax.swing.JLabel header = new javax.swing.JLabel(headerText.toString());
            header.setFont(header.getFont().deriveFont(java.awt.Font.BOLD));
            header.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            header.setBorder(BorderFactory.createEmptyBorder(10, 2, 4, 2));
            panel.add(header);

            JTable t = new JTable(new GroupModel(group));
            t.setRowHeight(20);
            t.getTableHeader().setReorderingAllowed(false);
            t.setEnabled(false);

            JPanel tablePanel = new JPanel(new java.awt.BorderLayout());
            tablePanel.add(t.getTableHeader(), java.awt.BorderLayout.NORTH);
            tablePanel.add(t, java.awt.BorderLayout.CENTER);
            tablePanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            java.awt.Dimension pref = tablePanel.getPreferredSize();
            tablePanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, pref.height));
            panel.add(tablePanel);
        }
        return panel;
    }

    /** Одна маленькая таблица группы: только сами ошибки, без столбцов группировки. */
    private static class GroupModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private static final String[] COLUMNS = {
            "t, с", "ERD, м", "ERS, м", "ER, м", "EVD, м/с", "EVS, м/с", "EV, м/с"
        };

        private final List<Row> rows;

        GroupModel(List<Row> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNS[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            ErrorRow e = rows.get(r).error;
            switch (c) {
                case 0: return Model.num(e.getTime());
                case 1: return Model.num(e.getRangeDynamic());
                case 2: return Model.num(e.getRangeRandom());
                case 3: return Model.num(e.getRangeTotal());
                case 4: return Model.num(e.getSpeedDynamic());
                case 5: return Model.num(e.getSpeedRandom());
                default: return Model.num(e.getSpeedTotal());
            }
        }
    }

    /**
     * Строит зависимости ошибок от параметров режима обработки сразу по всем
     * четырём пунктам задания: четыре графика и четыре таблицы в одном окне.
     *
     * Расчёт ведётся по руководству к работе: числа берутся из первой строки
     * таблицы результатов, а степень полинома для пунктов б, в и г находится
     * по пункту а – по наименьшей полной ошибке дальности.
     */
    private void btnChartsActionPerformed(java.awt.event.ActionEvent evt) {
        InputData base = selectedData();
        if (base == null) {
            return;
        }
        Object modeSel = cmbMode.getSelectedItem();
        Processor.Mode mode = modeSel instanceof Processor.Mode
                ? (Processor.Mode) modeSel : Processor.Mode.ANCHOR;
        owner.setInputData(base);
        // расчёт всех четырёх пунктов занимает заметное время: показываем,
        // что программа занята, а не зависла
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        try {
            SummaryFrame.show(this, base, mode, anchorMiddleInC());
        } finally {
            setCursor(java.awt.Cursor.getDefaultCursor());
        }
    }

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {
        if (model.getRowCount() == 0) {
            lblCount.setText("Сначала выполните расчёт");
            return;
        }
        Object sel = cmbVariant.getSelectedItem();
        String name = sel instanceof VariantTable.Variant
                ? "вариант " + ((VariantTable.Variant) sel).getNumber()
                : "свои данные";
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Куда сохранить таблицу");
        chooser.setSelectedFile(new File("Расчёт ЛР1 " + name + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (Writer out = new BufferedWriter(Files.newBufferedWriter(
                chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8))) {
            // метка порядка байтов: без неё Excel не распознаёт кириллицу
            out.write('﻿');
            for (int c = 0; c < model.getColumnCount(); c++) {
                out.write(model.getColumnName(c));
                out.write(c == model.getColumnCount() - 1 ? "\n" : ";");
            }
            for (int r = 0; r < model.getRowCount(); r++) {
                for (int c = 0; c < model.getColumnCount(); c++) {
                    out.write(String.valueOf(model.getValueAt(r, c)).replace('.', ','));
                    out.write(c == model.getColumnCount() - 1 ? "\n" : ";");
                }
            }
            lblCount.setText("Сохранено: " + chooser.getSelectedFile().getName());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Записать не удалось: " + e.getMessage(),
                    "Сохранение", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void btnBackActionPerformed(java.awt.event.ActionEvent evt) {
        owner.showCard(MainFrame.CARD_LOGIN);
    }

    /** Журнал обучающихся: просмотр и удаление записей. */
    private void btnJournalActionPerformed(java.awt.event.ActionEvent evt) {
        JournalFrame.show(this, owner.getJournalKeeper().getJournal());
    }

    /**
     * Смена пароля преподавателя: текущий пароль, новый и его повтор. Ключ
     * журнала обучающихся получается из пароля, поэтому при смене все
     * записи журнала удаляются – об этом предупреждается заранее.
     */
    private void btnPasswordActionPerformed(java.awt.event.ActionEvent evt) {
        javax.swing.JPasswordField current = new javax.swing.JPasswordField(16);
        javax.swing.JPasswordField fresh = new javax.swing.JPasswordField(16);
        javax.swing.JPasswordField repeat = new javax.swing.JPasswordField(16);
        int records = owner.getJournalKeeper().recordCount();
        javax.swing.JPanel form = new javax.swing.JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        form.add(new javax.swing.JLabel("<html><body style='width:380px'><b>Внимание.</b> Оценки в журнале обучающихся "
                + "защищены паролем преподавателя. После смены пароля все записи "
                + "журнала (сейчас их " + records + ") станут недействительными и будут "
                + "удалены: обучающимся, прервавшим работу, придётся начать её "
                + "заново.</html>"));
        form.add(new javax.swing.JLabel("Текущий пароль:"));
        form.add(current);
        form.add(new javax.swing.JLabel("Новый пароль:"));
        form.add(fresh);
        form.add(new javax.swing.JLabel("Новый пароль ещё раз:"));
        form.add(repeat);
        int answer = javax.swing.JOptionPane.showConfirmDialog(this, form, "Смена пароля преподавателя",
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
        if (answer != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        String message;
        String newPassword = new String(fresh.getPassword()).trim();
        if (!Settings.checkTeacherPassword(new String(current.getPassword()))) {
            message = "Текущий пароль указан неверно. Пароль не изменён.";
        } else if (newPassword.isEmpty()) {
            message = "Новый пароль не может быть пустым. Пароль не изменён.";
        } else if (!newPassword.equals(new String(repeat.getPassword()).trim())) {
            message = "Новый пароль и его повтор не совпадают. Пароль не изменён.";
        } else if (!ru.vka.upo.model.TeacherPassword.change(newPassword)) {
            message = "Записать новый пароль в файл настроек не удалось "
                    + "(нет доступа на запись). Пароль не изменён.";
        } else {
            int removed = owner.getJournalKeeper().passwordChanged();
            message = "Пароль изменён. Удалено записей журнала: " + removed + ".";
        }
        javax.swing.JOptionPane.showMessageDialog(this, message, "Смена пароля преподавателя",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
        owner.setStatus(message);
    }

    private void btnInputActionPerformed(java.awt.event.ActionEvent evt) {
        owner.showCard(MainFrame.CARD_INPUT);
    }

    /** Строка сводной таблицы. */
    private static class Row {

        final Notebook.Item item;
        final double value;
        final int line;
        final ErrorRow error;

        /**
         * Момент привязки M0, фактически использованный при расчёте этой
         * строки. В пункте в (объём выборки N) он по заданию переносится
         * в середину выборки (см. {@link Notebook.Item#apply}) и меняется
         * вместе с N – поэтому в заголовке группы он показывается явно,
         * а не подразумевается постоянным.
         */
        final int anchor;

        Row(Notebook.Item item, double value, int line, ErrorRow error, int anchor) {
            this.item = item;
            this.value = value;
            this.line = line;
            this.error = error;
            this.anchor = anchor;
        }
    }

    private static class Model extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private static final String[] COLUMNS = {
            "Пункт", "Параметр", "Значение", "Строка", "t, с",
            "ERD, м", "ERS, м", "ER, м", "EVD, м/с", "EVS, м/с", "EV, м/с"
        };

        private List<Row> rows = new ArrayList<>();

        void setRows(List<Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNS[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            ErrorRow e = row.error;
            switch (c) {
                case 0: return row.item.getLetter();
                case 1: return row.item.getParameter();
                case 2: return num(row.value);
                case 3: return String.valueOf(row.line);
                case 4: return num(e.getTime());
                case 5: return num(e.getRangeDynamic());
                case 6: return num(e.getRangeRandom());
                case 7: return num(e.getRangeTotal());
                case 8: return num(e.getSpeedDynamic());
                case 9: return num(e.getSpeedRandom());
                default: return num(e.getSpeedTotal());
            }
        }

        private static String num(double v) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                // ошибки скорости при m = 0 не определены (оценка скорости
                // берётся из коэффициента при первой степени полинома);
                // бесконечность означает, что величина не наблюдаема
                return "–";
            }
            double a = Math.abs(v);
            if (v == Math.rint(v) && a < 1e6) {
                return String.format(Locale.ROOT, "%.0f", v);
            }
            return String.format(Locale.ROOT, "%.4g", v);
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

        lblVariant = new javax.swing.JLabel();
        cmbVariant = new javax.swing.JComboBox();
        lblMode = new javax.swing.JLabel();
        cmbMode = new javax.swing.JComboBox();
        lblAnchorC = new javax.swing.JLabel();
        cmbAnchorC = new javax.swing.JComboBox();
        btnCompute = new javax.swing.JButton();
        btnCharts = new javax.swing.JButton();
        btnSave = new javax.swing.JButton();
        lblInfo = new javax.swing.JLabel();
        scrTable = new javax.swing.JScrollPane();
        lblCount = new javax.swing.JLabel();
        btnJournal = new javax.swing.JButton();
        btnPassword = new javax.swing.JButton();
        btnInput = new javax.swing.JButton();
        btnBack = new javax.swing.JButton();

        lblVariant.setText("Вариант");

        lblMode.setText("Динамическая ошибка");

        lblAnchorC.setText("Привязка в пункте в)");

        btnCompute.setText("Рассчитать все пункты");
        btnCompute.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnComputeActionPerformed(evt);
            }
        });

        btnCharts.setText("Построить зависимости по всем пунктам");
        btnCharts.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnChartsActionPerformed(evt);
            }
        });

        btnSave.setText("Сохранить в CSV");
        btnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });

        lblInfo.setText(" ");

        lblCount.setText(" ");

        btnJournal.setText("Журнал обучающихся");
        btnJournal.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnJournalActionPerformed(evt);
            }
        });

        btnPassword.setText("Сменить пароль");
        btnPassword.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPasswordActionPerformed(evt);
            }
        });

        btnInput.setText("Ввод данных вручную");
        btnInput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInputActionPerformed(evt);
            }
        });

        btnBack.setText("Выйти");
        btnBack.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBackActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblVariant, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cmbVariant, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cmbMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblAnchorC, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cmbAnchorC, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnCompute, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnCharts, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSave, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(lblInfo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(scrTable, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(lblCount, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnJournal, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnInput, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnBack, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblVariant)
                    .addComponent(cmbVariant)
                    .addComponent(lblMode)
                    .addComponent(cmbMode))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblAnchorC)
                    .addComponent(cmbAnchorC))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnCompute)
                    .addComponent(btnCharts)
                    .addComponent(btnSave))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblInfo)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scrTable, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblCount)
                    .addComponent(btnJournal)
                    .addComponent(btnPassword)
                    .addComponent(btnInput)
                    .addComponent(btnBack))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnBack;
    private javax.swing.JButton btnCharts;
    private javax.swing.JButton btnCompute;
    private javax.swing.JButton btnInput;
    private javax.swing.JButton btnJournal;
    private javax.swing.JButton btnPassword;
    private javax.swing.JButton btnSave;
    private javax.swing.JComboBox cmbAnchorC;
    private javax.swing.JComboBox cmbMode;
    private javax.swing.JComboBox cmbVariant;
    private javax.swing.JLabel lblAnchorC;
    private javax.swing.JLabel lblCount;
    private javax.swing.JLabel lblInfo;
    private javax.swing.JLabel lblMode;
    private javax.swing.JLabel lblVariant;
    private javax.swing.JScrollPane scrTable;
    // End of variables declaration//GEN-END:variables
}
