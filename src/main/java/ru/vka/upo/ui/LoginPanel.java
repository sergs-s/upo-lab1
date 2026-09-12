package ru.vka.upo.ui;

import java.awt.Font;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import ru.vka.upo.model.Settings;
import ru.vka.upo.model.Student;
import ru.vka.upo.model.VariantTable;

/**
 * Первый экран: кто работает с программой.
 *
 * Обучающийся называет фамилию с инициалами, номер группы и номер по списку
 * в журнале; вариант задания программа назначает сама по номеру по списку,
 * выбирать его обучающийся не может. Эти сведения затем идут в шапку отчёта.
 *
 * Преподаватель входит по паролю; пароль задаётся в настроечном файле рядом
 * с программой (см. {@link Settings}). Поля обучающегося при этом гаснут.
 */
public class LoginPanel extends javax.swing.JPanel {

    private final MainFrame owner;
    private final ButtonGroup roleGroup = new ButtonGroup();

    public LoginPanel(MainFrame owner) {
        this.owner = owner;
        initComponents();
        customize();
    }

    private void customize() {
        roleGroup.add(rbStudent);
        roleGroup.add(rbTeacher);
        rbStudent.setSelected(true);

        txtInfo.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
        txtInfo.setOpaque(false);
        txtInfo.setText(INFO);
        txtInfo.setCaretPosition(0);

        lblAssignedValue.setFont(lblAssignedValue.getFont().deriveFont(Font.BOLD));

        spnListNumber.setModel(new SpinnerNumberModel(1, 1, 999, 1));
        spnListNumber.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                showAssignedVariant();
            }
        });

        // Enter в поле пароля входит так же, как кнопка «Войти»: JPasswordField
        // (как и любой JTextField) сам посылает ActionEvent по Enter, если
        // на него подписан слушатель
        pwdTeacher.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                btnEnterActionPerformed(e);
            }
        });

        // при переходе в поле пароля сразу переключаем раскладку на
        // английскую: в полноэкранном режиме индикатора текущей раскладки
        // не видно, и непонятно, то ли пароль набран неверно, то ли просто
        // была включена русская раскладка
        pwdTeacher.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                try {
                    pwdTeacher.getInputContext().selectInputMethod(java.util.Locale.ENGLISH);
                } catch (RuntimeException ex) {
                    // раскладки может не быть в системе – тогда просто не трогаем её
                }
            }
        });

        showAssignedVariant();
        updateRole();
    }

    /** Вызывается при каждом показе экрана: возвращает строку состояния. */
    public void onShown() {
        updateRole();
    }

    /** Показывает вариант, назначенный по введённому номеру по списку. */
    private void showAssignedVariant() {
        VariantTable.Variant v = VariantTable.byListNumber(listNumber());
        lblAssignedValue.setText(v == null ? "–" : v.toString());
    }

    private int listNumber() {
        Object v = spnListNumber.getValue();
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    /** Включает поля, относящиеся к выбранной роли, и гасит остальные. */
    private void updateRole() {
        boolean student = rbStudent.isSelected();
        for (JComponent c : new JComponent[] {
                lblName, txtName, lblGroup, txtGroup,
                lblListNumber, spnListNumber, lblAssigned, lblAssignedValue}) {
            c.setEnabled(student);
        }
        for (JComponent c : new JComponent[] {lblPassword, pwdTeacher}) {
            c.setEnabled(!student);
        }
        owner.setStatus(student
                ? "Введите свои данные: вариант назначается по номеру в журнале"
                : "Вход преподавателя: расчёт всех пунктов задания сразу");
    }

    /** Собирает сведения о вошедшем или возвращает null, если данных не хватает. */
    private Student collect() {
        Student s = new Student();
        if (rbStudent.isSelected()) {
            s.setRole(Student.Role.STUDENT);
            s.setName(txtName.getText());
            s.setGroup(txtGroup.getText());
            s.setListNumber(listNumber());
            if (s.getName().isEmpty()) {
                owner.setStatus("Укажите фамилию и инициалы");
                txtName.requestFocusInWindow();
                return null;
            }
            if (s.getGroup().isEmpty()) {
                owner.setStatus("Укажите номер учебной группы");
                txtGroup.requestFocusInWindow();
                return null;
            }
            if (s.getVariant() == null) {
                owner.setStatus("Таблица вариантов пуста, работать не с чем");
                return null;
            }
            return s;
        }
        s.setRole(Student.Role.TEACHER);
        if (!Settings.checkTeacherPassword(new String(pwdTeacher.getPassword()))) {
            owner.setStatus("Пароль не подходит");
            pwdTeacher.setText("");
            pwdTeacher.requestFocusInWindow();
            return null;
        }
        return s;
    }

    private static final String INFO =
            "Лабораторная работа «Исследование эффективности устройств "
            + "предварительной обработки радионавигационных систем».\n"
            + "\n"
            + "Обучающийся указывает фамилию с инициалами, номер учебной группы и свой "
            + "номер по списку в журнале. Вариант задания назначается по номеру "
            + "по списку, выбрать его нельзя. Эти сведения войдут в шапку отчёта "
            + "по работе.\n"
            + "\n"
            + "Порядок выполнения работы и содержание её пунктов приведены в окне "
            + "«Задание на работу»; с ним следует ознакомиться до начала расчётов.";

    private void rbStudentActionPerformed(java.awt.event.ActionEvent evt) {
        updateRole();
    }

    private void rbTeacherActionPerformed(java.awt.event.ActionEvent evt) {
        updateRole();
    }

    private void btnTaskActionPerformed(java.awt.event.ActionEvent evt) {
        TaskFrame.show(this);
    }

    private void btnEnterActionPerformed(java.awt.event.ActionEvent evt) {
        Student s = collect();
        if (s == null) {
            return;
        }
        owner.setStudent(s);
        owner.setStatus(s.toString());
        owner.showCard(s.isTeacher() ? MainFrame.CARD_TEACHER : MainFrame.CARD_WELCOME);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rbStudent = new javax.swing.JRadioButton();
        rbTeacher = new javax.swing.JRadioButton();
        pnlStudent = new javax.swing.JPanel();
        pnlTeacher = new javax.swing.JPanel();
        scrInfo = new javax.swing.JScrollPane();
        txtInfo = new javax.swing.JTextArea();
        btnTask = new javax.swing.JButton();
        btnEnter = new javax.swing.JButton();
        lblName = new javax.swing.JLabel();
        txtName = new javax.swing.JTextField();
        lblGroup = new javax.swing.JLabel();
        txtGroup = new javax.swing.JTextField();
        lblListNumber = new javax.swing.JLabel();
        spnListNumber = new javax.swing.JSpinner();
        lblAssigned = new javax.swing.JLabel();
        lblAssignedValue = new javax.swing.JLabel();
        lblPassword = new javax.swing.JLabel();
        pwdTeacher = new javax.swing.JPasswordField();

        rbStudent.setText("обучающийся");
        rbStudent.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rbStudentActionPerformed(evt);
            }
        });

        rbTeacher.setText("преподаватель");
        rbTeacher.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rbTeacherActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnlStudentLayout = new javax.swing.GroupLayout(pnlStudent);
        pnlStudent.setLayout(pnlStudentLayout);
        pnlStudentLayout.setHorizontalGroup(
            pnlStudentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlStudentLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlStudentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblName)
                    .addComponent(lblGroup)
                    .addComponent(lblListNumber))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlStudentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtName, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtGroup, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(spnListNumber, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlStudentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING))
                .addContainerGap(0, Short.MAX_VALUE))
        );
        pnlStudentLayout.setVerticalGroup(
            pnlStudentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlStudentLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlStudentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblName)
                    .addComponent(txtName))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlStudentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblGroup)
                    .addComponent(txtGroup))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlStudentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblListNumber)
                    .addComponent(spnListNumber))
                .addContainerGap())
        );

        javax.swing.GroupLayout pnlTeacherLayout = new javax.swing.GroupLayout(pnlTeacher);
        pnlTeacher.setLayout(pnlTeacherLayout);
        pnlTeacherLayout.setHorizontalGroup(
            pnlTeacherLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlTeacherLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlTeacherLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblPassword))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlTeacherLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pwdTeacher, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlTeacherLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING))
                .addContainerGap(0, Short.MAX_VALUE))
        );
        pnlTeacherLayout.setVerticalGroup(
            pnlTeacherLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlTeacherLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlTeacherLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblPassword)
                    .addComponent(pwdTeacher))
                .addContainerGap())
        );

        txtInfo.setEditable(false);
        txtInfo.setLineWrap(true);
        txtInfo.setWrapStyleWord(true);
        txtInfo.setColumns(20);
        txtInfo.setRows(6);
        scrInfo.setViewportView(txtInfo);

        btnTask.setText("Задание на работу");
        btnTask.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTaskActionPerformed(evt);
            }
        });

        btnEnter.setText("Войти");
        btnEnter.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnEnterActionPerformed(evt);
            }
        });

        lblName.setText("Фамилия и инициалы");

        lblGroup.setText("Учебная группа");

        lblListNumber.setText("Номер по списку в журнале");

        lblAssigned.setText("Назначенный вариант");

        lblAssignedValue.setText("–");

        lblPassword.setText("Пароль преподавателя");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(rbStudent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rbTeacher, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(pnlStudent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lblAssigned, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblAssignedValue, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(pnlTeacher, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(scrInfo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnTask, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnEnter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rbStudent)
                    .addComponent(rbTeacher))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnlStudent)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblAssigned)
                    .addComponent(lblAssignedValue))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnlTeacher)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scrInfo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnTask)
                    .addComponent(btnEnter))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnEnter;
    private javax.swing.JButton btnTask;
    private javax.swing.JLabel lblAssigned;
    private javax.swing.JLabel lblAssignedValue;
    private javax.swing.JLabel lblGroup;
    private javax.swing.JLabel lblListNumber;
    private javax.swing.JLabel lblName;
    private javax.swing.JLabel lblPassword;
    private javax.swing.JPanel pnlStudent;
    private javax.swing.JPanel pnlTeacher;
    private javax.swing.JPasswordField pwdTeacher;
    private javax.swing.JRadioButton rbStudent;
    private javax.swing.JRadioButton rbTeacher;
    private javax.swing.JScrollPane scrInfo;
    private javax.swing.JSpinner spnListNumber;
    private javax.swing.JTextField txtGroup;
    private javax.swing.JTextArea txtInfo;
    private javax.swing.JTextField txtName;
    // End of variables declaration//GEN-END:variables
}
