package ru.vka.upo.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import ru.vka.upo.model.InputData;

/**
 * Диалог изменения параметров режима обработки (степень полинома,
 * объём выборки, шаг измерений, момент привязки) без возврата на экран
 * ввода исходных данных.
 *
 * Вызывается с экрана результатов кнопкой «Параметры режима обработки»,
 * чтобы не гонять пользователя туда-сюда между экранами ради одной цифры.
 * Значения синхронизируются с экраном ввода данных в обе стороны:
 * изменения отсюда сразу передаются в поля того экрана (см.
 * {@link InputPanel#applyModeParams(InputData)}, вызывается из
 * {@link MainFrame#syncModeParams(InputData)}), а при повторном открытии
 * этого диалога он видит те значения, что действуют сейчас в расчёте.
 *
 * Траектория и условия измерений (всё, кроме этих четырёх параметров)
 * в диалоге не показываются и не меняются – для них по-прежнему служит
 * экран ввода данных.
 */
public final class ModeParamsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JSpinner spnDegree = new JSpinner(new SpinnerNumberModel(2, 0, 12, 1));
    private final JSpinner spnSample = new JSpinner(new SpinnerNumberModel(49, 2, 999, 1));
    private final JTextField txtStep = new JTextField(10);
    private final JSpinner spnAnchor = new JSpinner(new SpinnerNumberModel(25, 1, 999, 1));
    private final JLabel lblAveragingValue = new JLabel("–");

    private final InputData base;
    private InputData result;

    private ModeParamsDialog(Window owner, InputData base) {
        super(owner, "Параметры режима обработки", ModalityType.APPLICATION_MODAL);
        this.base = base;

        spnDegree.setValue(base.getDegree());
        spnSample.setValue(base.getSampleSize());
        txtStep.setText(num(base.getStep()));
        spnAnchor.setValue(base.getAnchor());

        JPanel fields = new JPanel();
        fields.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(fields);
        fields.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(false);

        JLabel lblDegree = new JLabel("Степень полинома m");
        JLabel lblSample = new JLabel("Объём выборки N");
        JLabel lblStep = new JLabel("Шаг измерений T");
        JLabel lblStepUnit = new JLabel("с");
        JLabel lblAnchor = new JLabel("Момент привязки M0");
        JButton btnAnchorMiddle = new JButton("в середину");
        JLabel lblAveraging = new JLabel("Интервал усреднения");

        layout.setHorizontalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup()
                        .addComponent(lblDegree)
                        .addComponent(lblSample)
                        .addComponent(lblStep)
                        .addComponent(lblAnchor)
                        .addComponent(lblAveraging))
                .addGroup(layout.createParallelGroup()
                        .addComponent(spnDegree, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(spnSample, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(txtStep, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(spnAnchor, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(lblAveragingValue, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(layout.createParallelGroup()
                        .addComponent(lblStepUnit)
                        .addComponent(btnAnchorMiddle)));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lblDegree).addComponent(spnDegree))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lblSample).addComponent(spnSample))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lblStep).addComponent(txtStep).addComponent(lblStepUnit))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lblAnchor).addComponent(spnAnchor).addComponent(btnAnchorMiddle))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lblAveraging).addComponent(lblAveragingValue)));

        btnAnchorMiddle.addActionListener(e -> {
            int n = (Integer) spnSample.getValue();
            spnAnchor.setValue((n + 1) / 2);
            updateAveraging();
        });

        ChangeListener cl = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateAveraging();
            }
        };
        spnDegree.addChangeListener(cl);
        spnSample.addChangeListener(cl);
        spnAnchor.addChangeListener(cl);
        txtStep.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateAveraging();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateAveraging();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateAveraging();
            }
        });
        updateAveraging();

        JButton btnOk = new JButton("ОК");
        JButton btnCancel = new JButton("Отмена");
        btnOk.addActionListener(e -> {
            result = collect();
            setVisible(false);
        });
        btnCancel.addActionListener(e -> {
            result = null;
            setVisible(false);
        });
        getRootPane().setDefaultButton(btnOk);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttons.add(btnOk);
        buttons.add(btnCancel);

        setLayout(new BorderLayout());
        add(fields, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        Emblem.applyTo(this);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void updateAveraging() {
        try {
            InputData d = collect();
            double avg = d.averagingInterval();
            lblAveragingValue.setText(Double.isNaN(avg) ? "–" : num(round(avg, 4)) + " с");
        } catch (RuntimeException e) {
            lblAveragingValue.setText("–");
        }
    }

    /** Копия исходных данных с новыми параметрами режима обработки. */
    private InputData collect() {
        InputData d = base.clone();
        d.setDegree((Integer) spnDegree.getValue());
        d.setSampleSize((Integer) spnSample.getValue());
        String s = txtStep.getText() == null ? "" : txtStep.getText().trim().replace(',', '.');
        try {
            d.setStep(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            d.setStep(base.getStep());
        }
        d.setAnchor((Integer) spnAnchor.getValue());
        return d;
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

    private static double round(double v, int digits) {
        double k = Math.pow(10, digits);
        return Math.rint(v * k) / k;
    }

    /**
     * Показывает диалог и возвращает копию {@code base} с новыми параметрами
     * режима обработки, или {@code null}, если пользователь нажал «Отмена»
     * или закрыл окно.
     */
    public static InputData show(java.awt.Component parent, InputData base) {
        ModeParamsDialog d = new ModeParamsDialog(
                SwingUtilities.getWindowAncestor(parent), base);
        d.setVisible(true);
        return d.result;
    }
}
