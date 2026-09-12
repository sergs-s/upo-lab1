package ru.vka.upo.ui;

import java.util.Locale;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import ru.vka.upo.core.ErrorRow;
import ru.vka.upo.core.Processor;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Notebook;
import ru.vka.upo.report.Report;

/**
 * Рабочая тетрадь: таблицы и графики по пунктам задания.
 *
 * Числа обучающийся переносит из таблицы результатов вручную, программа их
 * не подставляет. По занесённым числам строится график и пишется вывод.
 * После построения программа сверяет занесённое со своим расчётом и
 * подсвечивает выпадающие точки, но правильных значений не показывает:
 * обучающийся должен сам вернуться к расчёту и проверить, что переписал.
 */
public class NotebookPanel extends javax.swing.JPanel {

    /** Относительное расхождение, при котором точка считается выпадающей. */
    private static final double TOLERANCE = 0.02;

    private static final Color DYNAMIC = new Color(0x1F, 0x4E, 0x79);
    private static final Color RANDOM = new Color(0x2E, 0x86, 0x4B);
    private static final Color TOTAL = new Color(0xC0, 0x39, 0x2B);

    private final MainFrame owner;
    private final ChartView chart = new ChartView();
    private final Model model = new Model();
    private JTable table;

    public NotebookPanel(MainFrame owner) {
        this.owner = owner;
        initComponents();
        customize();
    }

    private void customize() {
        cmbItem.setModel(new DefaultComboBoxModel<Object>(Notebook.Item.values()));

        table = new JTable(model);
        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new Renderer());
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(
                new javax.swing.event.ListSelectionListener() {
            @Override
            public void valueChanged(javax.swing.event.ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    showPicked();
                }
            }
        });
        scrTable.setViewportView(table);
        scrTable.setBorder(BorderFactory.createTitledBorder(
                "Выпишите сюда числа из таблицы результатов"));

        pnlChart.setLayout(new BorderLayout());
        pnlChart.add(chart, BorderLayout.CENTER);
        pnlChart.setBorder(BorderFactory.createTitledBorder("График по вашим числам"));

        scrConclusion.setBorder(BorderFactory.createTitledBorder("Вывод по пункту"));
        txtConclusion.setFont(new Font(Font.SERIF, Font.PLAIN, 14));

        spnSourceRow.setModel(new SpinnerNumberModel(1, 1, Processor.ROWS, 1));
        spnDegree.setModel(new SpinnerNumberModel(2, 0, 12, 1));
        lblDegree.setToolTipText("Степень полинома, при которой в пункте а "
                + "полная ошибка оказалась наименьшей");
        lblCheck.setText(" ");
        showItem();
    }

    private Notebook.Item item() {
        Object sel = cmbItem.getSelectedItem();
        return sel instanceof Notebook.Item ? (Notebook.Item) sel : Notebook.Item.A;
    }

    private Notebook.Page page() {
        return owner.getNotebook().page(item());
    }

    /** Вызывается при каждом показе экрана. */
    public void onShown() {
        showItem();
        owner.setStatus("Числа в тетрадь переносятся вручную: так же, "
                + "как переносились бы в тетрадь на бумаге");
    }

    /** Показывает выбранный пункт задания. */
    private void showItem() {
        Notebook.Item it = item();
        Notebook.Page p = page();
        model.setPage(it, p);
        int chosen = owner.getNotebook().getChosenDegree();
        lblFixed.setText("<html>Меняется " + it.getParameter() + " – "
                + it.getParameterTitle() + "; " + it.fixedDescription(chosen)
                + ".&nbsp; По руководству к работе в тетрадь заносятся числа "
                + "<b>первой строки</b> таблицы результатов.</html>");
        boolean needDegree = it != Notebook.Item.A;
        lblDegree.setVisible(needDegree);
        spnDegree.setVisible(needDegree);
        spnDegree.setValue(chosen);
        spnSourceRow.setValue(p.getSourceRow());
        txtConclusion.setText(p.getConclusion());
        chart.clear();
        chart.setMessage("Заполните таблицу и нажмите «Построить график»");
        lblCheck.setText(" ");
    }

    /** Сохраняет то, что обучающийся ввёл на текущей странице. */
    private void store() {
        Notebook.Page p = page();
        p.setConclusion(txtConclusion.getText());
        Object v = spnSourceRow.getValue();
        if (v instanceof Number) {
            p.setSourceRow(((Number) v).intValue());
        }
        Object d = spnDegree.getValue();
        if (d instanceof Number && item() != Notebook.Item.A) {
            owner.getNotebook().setChosenDegree(((Number) d).intValue());
        }
    }

    /**
     * Выделяет на графике точки той строки таблицы, которая выбрана:
     * так видно, какому значению параметра отвечает каждая точка.
     */
    private void showPicked() {
        Notebook.Line l = model.line(table == null ? -1 : table.getSelectedRow());
        chart.setPicked(l == null ? null : Double.valueOf(l.getParameter()));
    }

    /**
     * Строит график по занесённым числам и сверяет их с расчётом.
     * Правильные значения при этом не показываются.
     */
    private void plot() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        store();
        Notebook.Item it = item();
        Notebook.Page p = page();
        if (!p.isReady()) {
            chart.clear();
            chart.setMessage("Чтобы построить график, заполните хотя бы две строки");
            lblCheck.setText("Заполнены не все строки");
            return;
        }

        int suspicious = check(it, p);

        chart.clear();
        chart.setAxes(it.getParameter(), "ошибка, м", it.isLogParameter(), true);
        ChartView.Series sd = new ChartView.Series("динамическая ERD", DYNAMIC);
        ChartView.Series sr = new ChartView.Series("случайная ERS", RANDOM);
        ChartView.Series st = new ChartView.Series("полная ER", TOTAL);
        for (Notebook.Line l : p.getLines()) {
            if (!l.isFilled()) {
                continue;
            }
            sd.add(l.getParameter(), l.getRangeDynamic(), l.isSuspicious());
            sr.add(l.getParameter(), l.getRangeRandom(), l.isSuspicious());
            st.add(l.getParameter(), l.getRangeTotal(), l.isSuspicious());
        }
        chart.add(sd);
        chart.add(sr);
        chart.add(st);
        chart.markMinimum(st);
        showPicked();
        model.fireTableDataChanged();

        lblCheck.setText(suspicious == 0
                ? "График построен."
                : "Проверьте переписанное: " + suspicious
                + (suspicious == 1 ? " строка выделена" : " строк(и) выделено")
                + " – эти числа выпадают из расчёта.");
    }

    /**
     * Сверка занесённых чисел с расчётом. Возвращает число подозрительных
     * строк; какое значение верное, обучающемуся не сообщается.
     */
    private int check(Notebook.Item it, Notebook.Page p) {
        InputData base = owner.getInputData();
        int row = Math.max(1, Math.min(p.getSourceRow(), Processor.ROWS));
        int bad = 0;
        for (Notebook.Line l : p.getLines()) {
            l.setSuspicious(false);
            if (!l.isFilled()) {
                continue;
            }
            try {
                InputData d = it.apply(base, l.getParameter(), owner.getNotebook().getChosenDegree());
                List<ErrorRow> rows = new Processor(d).table();
                ErrorRow r = rows.get(row - 1);
                boolean ok = close(l.getRangeDynamic(), r.getRangeDynamic())
                        && close(l.getRangeRandom(), r.getRangeRandom())
                        && close(l.getRangeTotal(), r.getRangeTotal());
                if (!ok) {
                    l.setSuspicious(true);
                    bad++;
                }
            } catch (RuntimeException e) {
                // недопустимое сочетание параметров: строку не сверяем,
                // об этом обучающемуся скажет проверка исходных данных
                l.setSuspicious(false);
            }
        }
        return bad;
    }

    private static boolean close(double entered, double computed) {
        if (Double.isNaN(computed)) {
            return true;
        }
        double scale = Math.max(Math.abs(computed), 1e-9);
        // допуск ослаблен для очень малых величин: их обучающийся переписывает
        // с округлением, а разница в последнем знаке значения не имеет
        double tol = Math.abs(computed) < 1e-3 ? 0.5 : TOLERANCE;
        return Math.abs(entered - computed) / scale <= tol;
    }

    private void cmbItemActionPerformed(java.awt.event.ActionEvent evt) {
        store();
        showItem();
    }

    private void btnPlotActionPerformed(java.awt.event.ActionEvent evt) {
        plot();
    }

    private void btnClearActionPerformed(java.awt.event.ActionEvent evt) {
        for (Notebook.Line l : page().getLines()) {
            l.setRangeDynamic(null);
            l.setRangeRandom(null);
            l.setRangeTotal(null);
            l.setSuspicious(false);
        }
        model.fireTableDataChanged();
        chart.clear();
        chart.setMessage("Заполните таблицу и нажмите «Построить график»");
        lblCheck.setText("Числа этого пункта стёрты");
    }

    private void btnBackActionPerformed(java.awt.event.ActionEvent evt) {
        store();
        owner.showCard(MainFrame.CARD_RESULT);
    }

    private void btnHelpActionPerformed(java.awt.event.ActionEvent evt) {
        HelpFrame.show(this);
    }

    private void btnTaskActionPerformed(java.awt.event.ActionEvent evt) {
        TaskFrame.show(this);
    }

    private void btnReportActionPerformed(java.awt.event.ActionEvent evt) {
        store();
        Notebook nb = owner.getNotebook();
        if (nb.readyCount() == 0) {
            lblCheck.setText("Отчёт пуст: сначала заполните хотя бы один пункт");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Куда сохранить отчёт");
        chooser.setSelectedFile(new File(Report.fileName(owner.getStudent())));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Report.write(chooser.getSelectedFile().toPath(), owner.getStudent(),
                    owner.getInputData(), nb, charts());
            lblCheck.setText("Отчёт сохранён: "
                    + chooser.getSelectedFile().getName());
            owner.setStatus("Отчёт сохранён в файл "
                    + chooser.getSelectedFile().getAbsolutePath());
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(this,
                    "Записать отчёт не удалось: " + e.getMessage(),
                    "Отчёт", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Изображения графиков по всем заполненным пунктам – для отчёта. */
    private Map<Notebook.Item, BufferedImage> charts() {
        Map<Notebook.Item, BufferedImage> out = new EnumMap<>(Notebook.Item.class);
        for (Notebook.Item it : Notebook.Item.values()) {
            Notebook.Page p = owner.getNotebook().page(it);
            if (!p.isReady()) {
                continue;
            }
            check(it, p);
            ChartView c = new ChartView();
            c.setFont(getFont());
            c.setAxes(it.getParameter(), "ошибка, м", it.isLogParameter(), true);
            ChartView.Series sd = new ChartView.Series("динамическая ERD", DYNAMIC);
            ChartView.Series sr = new ChartView.Series("случайная ERS", RANDOM);
            ChartView.Series st = new ChartView.Series("полная ER", TOTAL);
            for (Notebook.Line l : p.getLines()) {
                if (!l.isFilled()) {
                    continue;
                }
                sd.add(l.getParameter(), l.getRangeDynamic(), false);
                sr.add(l.getParameter(), l.getRangeRandom(), false);
                st.add(l.getParameter(), l.getRangeTotal(), false);
            }
            c.add(sd);
            c.add(sr);
            c.add(st);
            out.put(it, c.image(760, 420));
        }
        return out;
    }

    /** График текущего пункта – для отчёта. */
    public ChartView getChart() {
        return chart;
    }

    /** Подсветка выпадающих строк. */
    private class Renderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                boolean selected, boolean focus, int row, int column) {
            Component c = super.getTableCellRendererComponent(
                    t, value, selected, focus, row, column);
            Notebook.Line l = model.line(row);
            if (!selected) {
                c.setBackground(l != null && l.isSuspicious()
                        ? new Color(0xFD, 0xEB, 0xD0) : Color.WHITE);
            }
            return c;
        }
    }

    /** Модель таблицы тетради: все ячейки заполняет обучающийся. */
    private static class Model extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private Notebook.Item item = Notebook.Item.A;
        private Notebook.Page page;

        void setPage(Notebook.Item item, Notebook.Page page) {
            this.item = item;
            this.page = page;
            fireTableStructureChanged();
        }

        Notebook.Line line(int i) {
            return page == null || i < 0 || i >= page.getLines().size()
                    ? null : page.getLines().get(i);
        }

        @Override
        public int getRowCount() {
            return page == null ? 0 : page.getLines().size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int c) {
            switch (c) {
                case 0: return item.getParameter();
                case 1: return "ERD, м";
                case 2: return "ERS, м";
                default: return c == 3 ? "ER, м" : "";
            }
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return true;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Notebook.Line l = line(r);
            if (l == null) {
                return "";
            }
            switch (c) {
                case 0: return text(l.getParameter());
                case 1: return text(l.getRangeDynamic());
                case 2: return text(l.getRangeRandom());
                default: return text(l.getRangeTotal());
            }
        }

        @Override
        public void setValueAt(Object value, int r, int c) {
            Notebook.Line l = line(r);
            if (l == null) {
                return;
            }
            Double v = parse(value);
            switch (c) {
                case 0:
                    if (v != null) {
                        l.setParameter(v);
                    }
                    break;
                case 1: l.setRangeDynamic(v); break;
                case 2: l.setRangeRandom(v); break;
                default: l.setRangeTotal(v); break;
            }
            l.setSuspicious(false);
            fireTableRowsUpdated(r, r);
        }

        private static Double parse(Object value) {
            if (value == null) {
                return null;
            }
            String s = value.toString().trim().replace(',', '.').replace(" ", "");
            if (s.isEmpty()) {
                return null;
            }
            try {
                return Double.valueOf(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static String text(Double v) {
            if (v == null) {
                return "";
            }
            double a = Math.abs(v);
            if (v == Math.rint(v) && a < 1e6) {
                return String.format(Locale.ROOT, "%.0f", v);
            }
            if (a >= 1e5 || (a > 0 && a < 1e-3)) {
                return String.format(Locale.ROOT, "%.4g", v);
            }
            return String.valueOf(v);
        }

        private static String text(double v) {
            return text(Double.valueOf(v));
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

        lblItem = new javax.swing.JLabel();
        cmbItem = new javax.swing.JComboBox();
        lblFixed = new javax.swing.JLabel();
        lblSource = new javax.swing.JLabel();
        spnSourceRow = new javax.swing.JSpinner();
        lblDegree = new javax.swing.JLabel();
        spnDegree = new javax.swing.JSpinner();
        btnPlot = new javax.swing.JButton();
        btnClear = new javax.swing.JButton();
        scrTable = new javax.swing.JScrollPane();
        pnlChart = new javax.swing.JPanel();
        scrConclusion = new javax.swing.JScrollPane();
        txtConclusion = new javax.swing.JTextArea();
        lblCheck = new javax.swing.JLabel();
        btnBack = new javax.swing.JButton();
        btnHelp = new javax.swing.JButton();
        btnTask = new javax.swing.JButton();
        btnReport = new javax.swing.JButton();

        lblItem.setText("Пункт задания");

        cmbItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cmbItemActionPerformed(evt);
            }
        });

        lblFixed.setText(" ");

        lblSource.setText("Числа берутся из строки таблицы результатов №");

        lblDegree.setText("Степень m, выбранная в пункте а");

        btnPlot.setText("Построить график");
        btnPlot.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnPlotActionPerformed(evt);
            }
        });

        btnClear.setText("Стереть числа пункта");
        btnClear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnClearActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlChartLayout = new javax.swing.GroupLayout(pnlChart);
        pnlChart.setLayout(pnlChartLayout);
        pnlChartLayout.setHorizontalGroup(
            pnlChartLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 520, Short.MAX_VALUE)
        );
        pnlChartLayout.setVerticalGroup(
            pnlChartLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 280, Short.MAX_VALUE)
        );

        txtConclusion.setLineWrap(true);
        txtConclusion.setWrapStyleWord(true);
        txtConclusion.setColumns(20);
        txtConclusion.setRows(3);
        scrConclusion.setViewportView(txtConclusion);

        lblCheck.setText(" ");

        btnBack.setText("К расчёту");
        btnBack.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBackActionPerformed(evt);
            }
        });

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

        btnReport.setText("Сохранить отчёт");
        btnReport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnReportActionPerformed(evt);
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
                        .addComponent(lblItem, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cmbItem, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(lblFixed, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblSource, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spnSourceRow, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblDegree, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(spnDegree, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(scrTable, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pnlChart, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(scrConclusion, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblCheck, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnBack, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnTask, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnHelp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnClear, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnPlot, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnReport, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblItem)
                    .addComponent(cmbItem))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblFixed)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblSource)
                    .addComponent(spnSourceRow)
                    .addComponent(lblDegree)
                    .addComponent(spnDegree))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(scrTable, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlChart, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scrConclusion, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblCheck)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnBack)
                    .addComponent(btnTask)
                    .addComponent(btnHelp)
                    .addComponent(btnClear)
                    .addComponent(btnPlot)
                    .addComponent(btnReport))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnBack;
    private javax.swing.JButton btnClear;
    private javax.swing.JButton btnHelp;
    private javax.swing.JButton btnPlot;
    private javax.swing.JButton btnReport;
    private javax.swing.JButton btnTask;
    private javax.swing.JComboBox cmbItem;
    private javax.swing.JLabel lblCheck;
    private javax.swing.JLabel lblDegree;
    private javax.swing.JLabel lblFixed;
    private javax.swing.JLabel lblItem;
    private javax.swing.JLabel lblSource;
    private javax.swing.JPanel pnlChart;
    private javax.swing.JScrollPane scrConclusion;
    private javax.swing.JScrollPane scrTable;
    private javax.swing.JSpinner spnDegree;
    private javax.swing.JSpinner spnSourceRow;
    private javax.swing.JTextArea txtConclusion;
    // End of variables declaration//GEN-END:variables
}
