package ru.vka.upo.ui;

import java.awt.Font;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JOptionPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Validation;
import ru.vka.upo.model.VariantTable;

/**
 * Экран ввода исходных данных.
 *
 * Слева задаются параметры траектории и условий измерений, справа –
 * параметры режима обработки. Данные проверяются при каждом изменении,
 * замечания выводятся в нижнем окне; пока есть замечания, препятствующие
 * расчёту, кнопка расчёта недоступна.
 */
public class InputPanel extends javax.swing.JPanel {

    private final MainFrame owner;
    private boolean updating;

    public InputPanel(MainFrame owner) {
        this.owner = owner;
        initComponents();
        customize();
    }

    /**
     * Вызывается при показе экрана: подставляет данные варианта,
     * выбранного обучающимся на первом экране.
     */
    public void onShown() {
        // обучающемуся вариант назначен и менять его нельзя, преподаватель
        // выбирает любой вариант из таблицы
        boolean teacher = owner.getStudent().isTeacher();
        cmbVariant.setEnabled(teacher);
        // «Автоматический расчёт» возвращает на экран преподавателя и виден
        // только там: обучающемуся туда хода нет
        btnTeacherBack.setVisible(teacher);
        VariantTable.Variant v = owner.getSelectedVariant();
        if (v == null) {
            if (teacher) {
                owner.setStatus("Выберите вариант или задайте свои значения.");
            }
            return;
        }
        updating = true;
        try {
            cmbVariant.setSelectedItem(v);
        } finally {
            updating = false;
        }
        applyVariant(v);
    }

    /**
     * Передаёт в поля режима обработки новые значения, не трогая остальные
     * данные, и не показывая сам экран.
     *
     * Вызывается из {@link MainFrame#syncModeParams(InputData)}, когда
     * параметры меняются диалогом {@link ModeParamsDialog} с экрана
     * результатов: без этого поля здесь остались бы устаревшими, хотя
     * расчёт уже ведётся по новым значениям.
     */
    public void applyModeParams(InputData d) {
        updating = true;
        try {
            spnDegree.setValue(d.getDegree());
            spnSample.setValue(d.getSampleSize());
            txtStep.setText(num(d.getStep()));
            spnAnchor.setValue(d.getAnchor());
        } finally {
            updating = false;
        }
        revalidateData();
    }

    /** Подставляет в поля данные варианта, сохраняя параметры режима обработки. */
    private void applyVariant(VariantTable.Variant v) {
        InputData d = v.toInputData();
        StringBuilder ignore = new StringBuilder();
        InputData current = collect(ignore);
        d.setDegree(current.getDegree());
        d.setSampleSize(current.getSampleSize());
        d.setStep(current.getStep());
        d.setAnchor(current.getAnchor());
        show(d);
        revalidateData();
        owner.setStatus("Вариант " + v.getNumber()
                + ": исходные данные подставлены, задайте режим обработки.");
    }

    // ------------------------------------------------------------- настройка
    private void customize() {
        pnlModel.setBorder(BorderFactory.createTitledBorder(
                "Траектория и условия измерений"));
        pnlMode.setBorder(BorderFactory.createTitledBorder(
                "Режим обработки"));
        scrMessages.setBorder(BorderFactory.createTitledBorder(
                "Проверка исходных данных"));

        txtMessages.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
        txtMessages.setOpaque(false);

        cmbMeasurer.setModel(new DefaultComboBoxModel<Object>(InputData.Measurer.values()));
        cmbMeasured.setModel(new DefaultComboBoxModel<Object>(InputData.Measured.values()));

        DefaultComboBoxModel<Object> vm = new DefaultComboBoxModel<Object>();
        vm.addElement("свои значения");
        for (VariantTable.Variant v : VariantTable.variants()) {
            vm.addElement(v);
        }
        cmbVariant.setModel(vm);
        cmbVariant.setEnabled(false); // вариант выбирается на первом экране

        spnDegree.setModel(new SpinnerNumberModel(2, 0, 12, 1));
        spnSample.setModel(new SpinnerNumberModel(49, 2, 999, 1));
        spnAnchor.setModel(new SpinnerNumberModel(25, 1, 999, 1));

        btnTeacherBack.setVisible(false);
        show(new InputData());
        installListeners();
        revalidateData();
    }

    private void installListeners() {
        DocumentListener dl = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                revalidateData();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                revalidateData();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                revalidateData();
            }
        };
        txtInterval.getDocument().addDocumentListener(dl);
        txtOrbitHeight.getDocument().addDocumentListener(dl);
        txtTrackDistance.getDocument().addDocumentListener(dl);
        txtTraverse.getDocument().addDocumentListener(dl);
        txtSpeed.getDocument().addDocumentListener(dl);
        txtSigmaRange.getDocument().addDocumentListener(dl);
        txtSigmaVelocity.getDocument().addDocumentListener(dl);
        txtStep.getDocument().addDocumentListener(dl);

        // единый десятичный разделитель: введённая запятая приводится к точке
        java.awt.event.FocusAdapter dot = new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                javax.swing.text.JTextComponent c =
                        (javax.swing.text.JTextComponent) e.getComponent();
                String s = c.getText();
                if (s != null && s.indexOf(',') >= 0) {
                    c.setText(s.replace(',', '.'));
                }
            }
        };
        for (javax.swing.JTextField c : new javax.swing.JTextField[] {
                txtInterval, txtOrbitHeight, txtTrackDistance, txtTraverse,
                txtSpeed, txtSigmaRange, txtSigmaVelocity, txtStep}) {
            c.addFocusListener(dot);
        }

        ChangeListener cl = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                revalidateData();
            }
        };
        spnDegree.addChangeListener(cl);
        spnSample.addChangeListener(cl);
        spnAnchor.addChangeListener(cl);

        java.awt.event.ActionListener al = new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                revalidateData();
            }
        };
        cmbMeasurer.addActionListener(al);
        cmbMeasured.addActionListener(al);
    }

    // ------------------------------------------------------- обмен с моделью
    /** Показывает данные в полях формы. */
    private void show(InputData d) {
        updating = true;
        try {
            cmbMeasurer.setSelectedItem(d.getMeasurer());
            cmbMeasured.setSelectedItem(d.getMeasured());
            txtInterval.setText(num(d.getInterval()));
            txtOrbitHeight.setText(num(d.getOrbitHeight()));
            txtTrackDistance.setText(num(d.getTrackDistance()));
            txtTraverse.setText(num(d.getTraverseDistance()));
            txtSpeed.setText(num(d.getRelativeSpeed()));
            txtSigmaRange.setText(num(d.getSigmaRange()));
            txtSigmaVelocity.setText(num(d.getSigmaVelocity()));
            spnDegree.setValue(d.getDegree());
            spnSample.setValue(d.getSampleSize());
            txtStep.setText(num(d.getStep()));
            spnAnchor.setValue(d.getAnchor());
        } finally {
            updating = false;
        }
    }

    /**
     * Собирает данные из полей формы. Нечисловые значения превращаются
     * в NaN, а причина попадает в список замечаний.
     */
    private InputData collect(StringBuilder parseErrors) {
        InputData d = new InputData();
        d.setMeasurer((InputData.Measurer) cmbMeasurer.getSelectedItem());
        d.setMeasured((InputData.Measured) cmbMeasured.getSelectedItem());
        d.setInterval(parse(txtInterval.getText(), "интервал измерений", parseErrors));
        d.setOrbitHeight(parse(txtOrbitHeight.getText(), "высота орбиты", parseErrors));
        d.setTrackDistance(parse(txtTrackDistance.getText(), "расстояние до трассы", parseErrors));
        d.setTraverseDistance(parse(txtTraverse.getText(), "траверзное расстояние", parseErrors));
        d.setRelativeSpeed(parse(txtSpeed.getText(), "относительная скорость", parseErrors));
        d.setSigmaRange(parse(txtSigmaRange.getText(), "СКО измерения дальности", parseErrors));
        d.setSigmaVelocity(parse(txtSigmaVelocity.getText(), "СКО измерения скорости", parseErrors));
        d.setDegree((Integer) spnDegree.getValue());
        d.setSampleSize((Integer) spnSample.getValue());
        d.setStep(parse(txtStep.getText(), "шаг измерений", parseErrors));
        d.setAnchor((Integer) spnAnchor.getValue());
        return d;
    }

    private double parse(String s, String what, StringBuilder errors) {
        String t = s == null ? "" : s.trim().replace(',', '.');
        if (t.isEmpty()) {
            if (isRequired(what)) {
                errors.append("Не задано: ").append(what).append(".\n");
            }
            return Double.NaN;
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            errors.append("Не число: ").append(what)
                  .append(" («").append(s.trim()).append("»).\n");
            return Double.NaN;
        }
    }

    /** Поля, отключённые при текущем сочетании признаков, обязательными не считаются. */
    private boolean isRequired(String what) {
        boolean ground = cmbMeasurer.getSelectedItem() == InputData.Measurer.GROUND;
        InputData.Measured m = (InputData.Measured) cmbMeasured.getSelectedItem();
        if ("высота орбиты".equals(what) || "расстояние до трассы".equals(what)) {
            return ground;
        }
        if ("траверзное расстояние".equals(what) || "относительная скорость".equals(what)) {
            return !ground;
        }
        if ("СКО измерения дальности".equals(what)) {
            return m != null && m.hasRange();
        }
        if ("СКО измерения скорости".equals(what)) {
            return m != null && m.hasVelocity();
        }
        return true;
    }

    private static String num(double v) {
        if (Double.isNaN(v)) {
            return "";
        }
        if (v == Math.rint(v) && Math.abs(v) < 1e9) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.ROOT, "%s", v);
    }

    // -------------------------------------------------------------- проверка
    /** Перепроверяет данные и обновляет доступность полей и кнопок. */
    private void revalidateData() {
        if (updating) {
            return;
        }
        StringBuilder parseErrors = new StringBuilder();
        InputData d = collect(parseErrors);

        boolean ground = d.getMeasurer() == InputData.Measurer.GROUND;
        setFieldEnabled(txtOrbitHeight, lblOrbitHeight, lblOrbitHeightUnit, ground);
        setFieldEnabled(txtTrackDistance, lblTrackDistance, lblTrackDistanceUnit, ground);
        setFieldEnabled(txtTraverse, lblTraverse, lblTraverseUnit, !ground);
        setFieldEnabled(txtSpeed, lblSpeed, lblSpeedUnit, !ground);
        setFieldEnabled(txtSigmaRange, lblSigmaRange, lblSigmaRangeUnit,
                d.getMeasured().hasRange());
        setFieldEnabled(txtSigmaVelocity, lblSigmaVelocity, lblSigmaVelocityUnit,
                d.getMeasured().hasVelocity());

        double avg = d.averagingInterval();
        lblAveragingValue.setText(Double.isNaN(avg) ? "–" : num(round(avg, 4)) + " с");

        StringBuilder sb = new StringBuilder(parseErrors);
        boolean fatal = parseErrors.length() > 0;
        if (!fatal) {
            List<Validation.Issue> issues = Validation.check(d);
            for (Validation.Issue i : issues) {
                sb.append(i.isFatal() ? "Ошибка: " : "Внимание: ")
                  .append(i.getMessage()).append('\n');
            }
            fatal = !Validation.isComputable(issues);
        }
        if (sb.length() == 0) {
            sb.append("Исходные данные допустимы. Интервал усреднения ")
              .append(num(round(avg, 4)))
              .append(" с при интервале измерений ").append(num(d.getInterval())).append(" с.");
        }
        txtMessages.setText(sb.toString().trim());
        txtMessages.setCaretPosition(0);
        btnCompute.setEnabled(!fatal);
        owner.setStatus(fatal ? "Исходные данные требуют исправления."
                              : "Исходные данные допустимы.");
    }

    private static double round(double v, int digits) {
        double k = Math.pow(10, digits);
        return Math.rint(v * k) / k;
    }

    private static void setFieldEnabled(javax.swing.JComponent field,
            javax.swing.JLabel label, javax.swing.JLabel unit, boolean on) {
        field.setEnabled(on);
        label.setEnabled(on);
        if (unit != null) {
            unit.setEnabled(on);
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
        btnApplyVariant = new javax.swing.JButton();
        pnlModel = new javax.swing.JPanel();
        pnlMode = new javax.swing.JPanel();
        scrMessages = new javax.swing.JScrollPane();
        txtMessages = new javax.swing.JTextArea();
        btnBack = new javax.swing.JButton();
        btnCompute = new javax.swing.JButton();
        btnTeacherBack = new javax.swing.JButton();
        lblMeasurer = new javax.swing.JLabel();
        cmbMeasurer = new javax.swing.JComboBox();
        lblMeasured = new javax.swing.JLabel();
        cmbMeasured = new javax.swing.JComboBox();
        lblInterval = new javax.swing.JLabel();
        txtInterval = new javax.swing.JTextField();
        lblIntervalUnit = new javax.swing.JLabel();
        lblOrbitHeight = new javax.swing.JLabel();
        txtOrbitHeight = new javax.swing.JTextField();
        lblOrbitHeightUnit = new javax.swing.JLabel();
        lblTrackDistance = new javax.swing.JLabel();
        txtTrackDistance = new javax.swing.JTextField();
        lblTrackDistanceUnit = new javax.swing.JLabel();
        lblTraverse = new javax.swing.JLabel();
        txtTraverse = new javax.swing.JTextField();
        lblTraverseUnit = new javax.swing.JLabel();
        lblSpeed = new javax.swing.JLabel();
        txtSpeed = new javax.swing.JTextField();
        lblSpeedUnit = new javax.swing.JLabel();
        lblSigmaRange = new javax.swing.JLabel();
        txtSigmaRange = new javax.swing.JTextField();
        lblSigmaRangeUnit = new javax.swing.JLabel();
        lblSigmaVelocity = new javax.swing.JLabel();
        txtSigmaVelocity = new javax.swing.JTextField();
        lblSigmaVelocityUnit = new javax.swing.JLabel();
        lblDegree = new javax.swing.JLabel();
        spnDegree = new javax.swing.JSpinner();
        lblSample = new javax.swing.JLabel();
        spnSample = new javax.swing.JSpinner();
        lblStep = new javax.swing.JLabel();
        txtStep = new javax.swing.JTextField();
        lblStepUnit = new javax.swing.JLabel();
        lblAnchor = new javax.swing.JLabel();
        spnAnchor = new javax.swing.JSpinner();
        btnAnchorMiddle = new javax.swing.JButton();
        lblAveraging = new javax.swing.JLabel();
        lblAveragingValue = new javax.swing.JLabel();

        lblVariant.setText("Вариант задания");

        btnApplyVariant.setText("Загрузить заново");
        btnApplyVariant.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnApplyVariantActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlModelLayout = new javax.swing.GroupLayout(pnlModel);
        pnlModel.setLayout(pnlModelLayout);
        pnlModelLayout.setHorizontalGroup(
            pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlModelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblMeasurer)
                    .addComponent(lblMeasured)
                    .addComponent(lblInterval)
                    .addComponent(lblOrbitHeight)
                    .addComponent(lblTrackDistance)
                    .addComponent(lblTraverse)
                    .addComponent(lblSpeed)
                    .addComponent(lblSigmaRange)
                    .addComponent(lblSigmaVelocity))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cmbMeasurer, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cmbMeasured, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtInterval, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtOrbitHeight, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtTrackDistance, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtTraverse, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtSpeed, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtSigmaRange, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtSigmaVelocity, javax.swing.GroupLayout.PREFERRED_SIZE, 190, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblIntervalUnit)
                    .addComponent(lblOrbitHeightUnit)
                    .addComponent(lblTrackDistanceUnit)
                    .addComponent(lblTraverseUnit)
                    .addComponent(lblSpeedUnit)
                    .addComponent(lblSigmaRangeUnit)
                    .addComponent(lblSigmaVelocityUnit))
                .addContainerGap(0, Short.MAX_VALUE))
        );
        pnlModelLayout.setVerticalGroup(
            pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlModelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblMeasurer)
                    .addComponent(cmbMeasurer))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblMeasured)
                    .addComponent(cmbMeasured))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblInterval)
                    .addComponent(txtInterval)
                    .addComponent(lblIntervalUnit))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblOrbitHeight)
                    .addComponent(txtOrbitHeight)
                    .addComponent(lblOrbitHeightUnit))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblTrackDistance)
                    .addComponent(txtTrackDistance)
                    .addComponent(lblTrackDistanceUnit))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblTraverse)
                    .addComponent(txtTraverse)
                    .addComponent(lblTraverseUnit))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblSpeed)
                    .addComponent(txtSpeed)
                    .addComponent(lblSpeedUnit))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblSigmaRange)
                    .addComponent(txtSigmaRange)
                    .addComponent(lblSigmaRangeUnit))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblSigmaVelocity)
                    .addComponent(txtSigmaVelocity)
                    .addComponent(lblSigmaVelocityUnit))
                .addContainerGap())
        );

        javax.swing.GroupLayout pnlModeLayout = new javax.swing.GroupLayout(pnlMode);
        pnlMode.setLayout(pnlModeLayout);
        pnlModeLayout.setHorizontalGroup(
            pnlModeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlModeLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlModeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblDegree)
                    .addComponent(lblSample)
                    .addComponent(lblStep)
                    .addComponent(lblAnchor)
                    .addComponent(lblAveraging))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(spnDegree, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(spnSample, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtStep, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(spnAnchor, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblAveragingValue, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblStepUnit)
                    .addComponent(btnAnchorMiddle))
                .addContainerGap(0, Short.MAX_VALUE))
        );
        pnlModeLayout.setVerticalGroup(
            pnlModeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlModeLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlModeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblDegree)
                    .addComponent(spnDegree))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblSample)
                    .addComponent(spnSample))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblStep)
                    .addComponent(txtStep)
                    .addComponent(lblStepUnit))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblAnchor)
                    .addComponent(spnAnchor)
                    .addComponent(btnAnchorMiddle))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModeLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblAveraging)
                    .addComponent(lblAveragingValue))
                .addContainerGap())
        );

        txtMessages.setEditable(false);
        txtMessages.setLineWrap(true);
        txtMessages.setWrapStyleWord(true);
        txtMessages.setColumns(20);
        txtMessages.setRows(4);
        scrMessages.setViewportView(txtMessages);

        btnBack.setText("К началу");
        btnBack.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBackActionPerformed(evt);
            }
        });

        btnCompute.setText("Рассчитать");
        btnCompute.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnComputeActionPerformed(evt);
            }
        });

        btnTeacherBack.setText("Автоматический расчёт");
        btnTeacherBack.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTeacherBackActionPerformed(evt);
            }
        });

        lblMeasurer.setText("Признак траектории");

        lblMeasured.setText("Измеряемые параметры");

        lblInterval.setText("Интервал измерений");

        lblIntervalUnit.setText("с");

        lblOrbitHeight.setText("Высота орбиты");

        lblOrbitHeightUnit.setText("км");

        lblTrackDistance.setText("Расстояние до трассы");

        lblTrackDistanceUnit.setText("км");

        lblTraverse.setText("Траверзное расстояние");

        lblTraverseUnit.setText("км");

        lblSpeed.setText("Относительная скорость");

        lblSpeedUnit.setText("м/с");

        lblSigmaRange.setText("СКО измерения дальности");

        lblSigmaRangeUnit.setText("м");

        lblSigmaVelocity.setText("СКО измерения скорости");

        lblSigmaVelocityUnit.setText("м/с");

        lblDegree.setText("Степень полинома m");

        lblSample.setText("Объём выборки N");

        lblStep.setText("Шаг измерений Δt");

        lblStepUnit.setText("с");

        lblAnchor.setText("Момент привязки M0");

        btnAnchorMiddle.setText("в середину");
        btnAnchorMiddle.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAnchorMiddleActionPerformed(evt);
            }
        });

        lblAveraging.setText("Интервал усреднения");

        lblAveragingValue.setText("–");

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
                        .addComponent(btnApplyVariant, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(pnlModel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pnlMode, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(scrMessages, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnTeacherBack, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnBack, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnCompute, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblVariant)
                    .addComponent(cmbVariant)
                    .addComponent(btnApplyVariant))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pnlModel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlMode, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scrMessages, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnTeacherBack)
                    .addComponent(btnBack)
                    .addComponent(btnCompute))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void btnApplyVariantActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnApplyVariantActionPerformed
        Object sel = cmbVariant.getSelectedItem();
        if (sel instanceof VariantTable.Variant) {
            // возврат к исходным данным варианта, если обучающийся их правил
            applyVariant((VariantTable.Variant) sel);
        } else {
            owner.setStatus("Вариант не выбран: значения вводятся вручную.");
        }
    }//GEN-LAST:event_btnApplyVariantActionPerformed

    private void btnAnchorMiddleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAnchorMiddleActionPerformed
        int n = (Integer) spnSample.getValue();
        spnAnchor.setValue((n + 1) / 2);
        revalidateData();
    }//GEN-LAST:event_btnAnchorMiddleActionPerformed

    private void btnBackActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBackActionPerformed
        owner.setStatus("");
        owner.showCard(MainFrame.CARD_WELCOME);
    }//GEN-LAST:event_btnBackActionPerformed

    private void btnComputeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnComputeActionPerformed
        StringBuilder errors = new StringBuilder();
        owner.setInputData(collect(errors));
        owner.showCard(MainFrame.CARD_RESULT);
    }//GEN-LAST:event_btnComputeActionPerformed

    /**
     * Возврат на экран преподавателя («Расчёт всех пунктов задания сразу»)
     * из ручного ввода данных. Кнопка видна только преподавателю – см.
     * {@link #onShown()}; введённые здесь данные при этом не пропадают: они
     * остаются в полях экрана до следующего изменения.
     */
    private void btnTeacherBackActionPerformed(java.awt.event.ActionEvent evt) {
        // заданные вручную данные передаются экрану преподавателя, если они
        // допустимы; иначе там остаётся то, что было выбрано раньше
        StringBuilder errors = new StringBuilder();
        InputData d = collect(errors);
        if (errors.length() == 0 && Validation.isComputable(Validation.check(d))) {
            owner.setInputData(d);
            owner.setTeacherManualData(d);
        }
        owner.showCard(MainFrame.CARD_TEACHER);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAnchorMiddle;
    private javax.swing.JButton btnApplyVariant;
    private javax.swing.JButton btnBack;
    private javax.swing.JButton btnCompute;
    private javax.swing.JButton btnTeacherBack;
    private javax.swing.JComboBox cmbMeasured;
    private javax.swing.JComboBox cmbMeasurer;
    private javax.swing.JComboBox cmbVariant;
    private javax.swing.JLabel lblAnchor;
    private javax.swing.JLabel lblAveraging;
    private javax.swing.JLabel lblAveragingValue;
    private javax.swing.JLabel lblDegree;
    private javax.swing.JLabel lblInterval;
    private javax.swing.JLabel lblIntervalUnit;
    private javax.swing.JLabel lblMeasured;
    private javax.swing.JLabel lblMeasurer;
    private javax.swing.JLabel lblOrbitHeight;
    private javax.swing.JLabel lblOrbitHeightUnit;
    private javax.swing.JLabel lblSample;
    private javax.swing.JLabel lblSigmaRange;
    private javax.swing.JLabel lblSigmaRangeUnit;
    private javax.swing.JLabel lblSigmaVelocity;
    private javax.swing.JLabel lblSigmaVelocityUnit;
    private javax.swing.JLabel lblSpeed;
    private javax.swing.JLabel lblSpeedUnit;
    private javax.swing.JLabel lblStep;
    private javax.swing.JLabel lblStepUnit;
    private javax.swing.JLabel lblTrackDistance;
    private javax.swing.JLabel lblTrackDistanceUnit;
    private javax.swing.JLabel lblTraverse;
    private javax.swing.JLabel lblTraverseUnit;
    private javax.swing.JLabel lblVariant;
    private javax.swing.JPanel pnlMode;
    private javax.swing.JPanel pnlModel;
    private javax.swing.JScrollPane scrMessages;
    private javax.swing.JSpinner spnAnchor;
    private javax.swing.JSpinner spnDegree;
    private javax.swing.JSpinner spnSample;
    private javax.swing.JTextField txtInterval;
    private javax.swing.JTextArea txtMessages;
    private javax.swing.JTextField txtOrbitHeight;
    private javax.swing.JTextField txtSigmaRange;
    private javax.swing.JTextField txtSigmaVelocity;
    private javax.swing.JTextField txtSpeed;
    private javax.swing.JTextField txtStep;
    private javax.swing.JTextField txtTrackDistance;
    private javax.swing.JTextField txtTraverse;
    // End of variables declaration//GEN-END:variables
}
