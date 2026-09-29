package ru.vka.upo.ui;

import java.awt.CardLayout;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Notebook;
import ru.vka.upo.model.Student;
import ru.vka.upo.model.VariantTable;
import java.awt.Font;

/**
 * Главное окно программы. Содержит область с последовательно сменяющими
 * друг друга экранами: приветствие, входной контроль знаний, ввод исходных
 * данных, результаты расчёта, графики и иллюстрации.
 *
 * Смена экранов выполняется методом {@link #showCard(String)}.
 */
public class MainFrame extends javax.swing.JFrame {

    public static final String CARD_LOGIN = "login";
    public static final String CARD_WELCOME = "welcome";
    public static final String CARD_TEST = "test";
    public static final String CARD_INPUT = "input";
    public static final String CARD_RESULT = "result";
    public static final String CARD_NOTEBOOK = "notebook";
    public static final String CARD_TEACHER = "teacher";

    // экраны создаются в customize(), уже после initComponents(): при создании
    // они обращаются к строке состояния главного окна
    private LoginPanel loginPanel;
    private WelcomePanel welcomePanel;
    private TestPanel testPanel;
    private InputPanel inputPanel;
    private ResultsPanel resultsPanel;
    private NotebookPanel notebookPanel;
    private TeacherPanel teacherPanel;

    // вид окна берётся из настроечного файла один раз при создании: сменить
    // его на лету всё равно нельзя (undecorated задаётся до отображения окна)
    private final boolean fullScreen = ru.vka.upo.model.Settings.fullScreen();

    public MainFrame() {
        // без рамки и системных кнопок: setUndecorated() нужно вызвать до того,
        // как окно станет отображаемым, а initComponents() уже вызывает pack()
        if (fullScreen) {
            setUndecorated(true);
        }
        initComponents();
        customize();
    }

    /** Настройка, не относящаяся к визуальному редактору форм. */
    private void customize() {
        // значок программы – эмблема кафедры; диалоги получают его от своего
        // окна-владельца сами, поэтому отдельно каждому его ставить не нужно
        Emblem.applyTo(this);
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 16f));
        lblStatus.setFont(lblStatus.getFont().deriveFont(Font.PLAIN, 12f));

        // раскладку карт задаём здесь: в конструкторе форм панель остаётся пустым
        // контейнером, а экраны добавляются программой
        pnlCards.setLayout(new CardLayout());
        loginPanel = new LoginPanel(this);
        welcomePanel = new WelcomePanel(this);
        testPanel = new TestPanel(this);
        inputPanel = new InputPanel(this);
        resultsPanel = new ResultsPanel(this);
        notebookPanel = new NotebookPanel(this);
        teacherPanel = new TeacherPanel(this);
        pnlCards.add(loginPanel, CARD_LOGIN);
        pnlCards.add(welcomePanel, CARD_WELCOME);
        pnlCards.add(testPanel, CARD_TEST);
        pnlCards.add(inputPanel, CARD_INPUT);
        pnlCards.add(resultsPanel, CARD_RESULT);
        pnlCards.add(notebookPanel, CARD_NOTEBOOK);
        pnlCards.add(teacherPanel, CARD_TEACHER);

        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        if (fullScreen) {
            // окно разворачивается на весь экран, включая место, занятое панелью
            // задач: это учебный терминал на время работы с лабораторной, а не
            // обычное приложение, из которого можно случайно переключиться.
            // Сворачивать окно и закрывать его системными средствами (Alt+F4
            // и т.п.) нельзя: выход – только через кнопку «Выход»
            // (btnExitActionPerformed), слушатель закрытия окна не добавляется
            setBounds(0, 0, screen.width, screen.height);
            setResizable(false);
            setAlwaysOnTop(true);
        } else {
            // обычное окно: с рамкой, заголовком, сворачивается, разворачивается
            // и изменяется в размерах; закрывается системным крестом с тем же
            // подтверждением, что и кнопкой «Выход» – настройка window.fullscreen
            // = false в Настройки.properties
            setSize(Math.min(1280, screen.width - 60), Math.min(760, screen.height - 60));
            setMinimumSize(new java.awt.Dimension(1000, 640));
            setLocationRelativeTo(null);
            setResizable(true);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    confirmExit();
                }
            });
        }

        showCard(CARD_LOGIN);
    }

    /** Единственный способ завершить работу программы – кнопка «Выход». */
    private void btnExitActionPerformed(java.awt.event.ActionEvent evt) {
        confirmExit();
    }

    /** Запрашивает подтверждение и завершает работу программы. */
    private void confirmExit() {
        Object[] options = {"Завершить", "Продолжить работу"};
        int answer = javax.swing.JOptionPane.showOptionDialog(this,
                "Завершить работу с программой?\n"
                + "Результаты расчёта и записи рабочей тетради не сохраняются.",
                "Выход", javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        if (answer == 0) {
            dispose();
            System.exit(0);
        }
    }

    /** Показывает экран с заданным именем. */
    public final void showCard(String name) {
        ((CardLayout) pnlCards.getLayout()).show(pnlCards, name);
        if (CARD_LOGIN.equals(name)) {
            loginPanel.onShown();
        } else if (CARD_WELCOME.equals(name)) {
            welcomePanel.onShown();
        } else if (CARD_TEST.equals(name)) {
            testPanel.startNewSession();
        } else if (CARD_INPUT.equals(name)) {
            inputPanel.onShown();
        } else if (CARD_RESULT.equals(name)) {
            resultsPanel.onShown();
        } else if (CARD_NOTEBOOK.equals(name)) {
            notebookPanel.onShown();
        } else if (CARD_TEACHER.equals(name)) {
            teacherPanel.onShown();
        }
    }

    /** Сведения о том, кто работает с программой. */
    private Student student = new Student();

    public Student getStudent() {
        return student;
    }

    /**
     * Принимает сведения о вошедшем. Обучающемуся вместе с этим назначается
     * вариант, и его исходные данные становятся текущими.
     */
    public void setStudent(Student student) {
        this.student = student == null ? new Student() : student;
        VariantTable.Variant v = this.student.getVariant();
        if (v != null) {
            inputData = v.toInputData();
        }
    }

    /** Вариант, назначенный обучающемуся, или null у преподавателя. */
    public VariantTable.Variant getSelectedVariant() {
        return student.getVariant();
    }

    /** Исходные данные, принятые на экране ввода: их получит расчётное ядро. */
    private InputData inputData = new InputData();

    public InputData getInputData() {
        return inputData;
    }

    public void setInputData(InputData inputData) {
        this.inputData = inputData;
    }

    /**
     * Передаёт параметры режима обработки (степень, объём выборки, шаг,
     * привязка) в поля экрана ввода данных, не показывая сам экран.
     *
     * Нужен, чтобы диалог {@link ModeParamsDialog}, вызванный с экрана
     * результатов, не оставлял эти поля устаревшими: без этого при переходе
     * на экран ввода («К вводу данных») там были бы видны прежние значения,
     * а не те, что уже действуют в расчёте.
     */
    public void syncModeParams(InputData d) {
        inputPanel.applyModeParams(d);
    }

    /**
     * Передаёт экрану преподавателя исходные данные, заданные вручную на
     * экране ввода: расчёт всех пунктов пойдёт по ним, а не по варианту.
     */
    public void setTeacherManualData(InputData d) {
        teacherPanel.setManualData(d);
    }

    /** Развёрнуто ли главное окно на весь экран (настройка window.fullscreen). */
    public boolean isFullScreenMode() {
        return fullScreen;
    }

    /** Рабочая тетрадь обучающегося: живёт всё время работы с программой. */
    private final Notebook notebook = new Notebook();

    public Notebook getNotebook() {
        return notebook;
    }

    /** Экран рабочей тетради: нужен отчёту, чтобы взять готовые графики. */
    public NotebookPanel getNotebookPanel() {
        return notebookPanel;
    }

    /** Выводит строку состояния в нижней части окна. */
    public void setStatus(String text) {
        lblStatus.setText(text == null || text.isEmpty() ? " " : text);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        btnExit = new javax.swing.JButton();
        lblTitle = new javax.swing.JLabel();
        pnlCards = new javax.swing.JPanel();
        lblStatus = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Лабораторная работа «Предварительная обработка»");

        btnExit.setText("Выход");
        btnExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnExitActionPerformed(evt);
            }
        });

        lblTitle.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTitle.setText("Исследование эффективности устройств предварительной обработки");

        javax.swing.GroupLayout pnlCardsLayout = new javax.swing.GroupLayout(pnlCards);
        pnlCards.setLayout(pnlCardsLayout);
        pnlCardsLayout.setHorizontalGroup(
            pnlCardsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 860, Short.MAX_VALUE)
        );
        pnlCardsLayout.setVerticalGroup(
            pnlCardsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 480, Short.MAX_VALUE)
        );

        lblStatus.setText(" ");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnExit, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(lblTitle, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlCards, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lblStatus, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnExit))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnlCards, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblStatus)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnExit;
    private javax.swing.JLabel lblStatus;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JPanel pnlCards;
    // End of variables declaration//GEN-END:variables
}
