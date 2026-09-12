package ru.vka.upo.ui;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import ru.vka.upo.model.Question;
import ru.vka.upo.model.QuestionBank;
import ru.vka.upo.model.TestSession;

/**
 * Экран входного контроля знаний. Вопросы и варианты ответов выводятся
 * последовательно; переключатели вариантов создаются во время работы
 * программы и помещаются в панель {@code pnlOptions}.
 */
public class TestPanel extends javax.swing.JPanel {

    private final MainFrame owner;
    private TestSession session;
    private final List<JRadioButton> optionButtons = new ArrayList<>();
    private ButtonGroup group = new ButtonGroup();

    public TestPanel(MainFrame owner) {
        this.owner = owner;
        initComponents();
        customize();
    }

    private void customize() {
        Font serif = new Font(Font.SERIF, Font.PLAIN, 15);
        txtQuestion.setFont(serif.deriveFont(Font.BOLD));
        txtQuestion.setOpaque(false);
        scrQuestion.setOpaque(false);
        scrQuestion.getViewport().setOpaque(false);
        scrQuestion.setBorder(null);
        lblProgress.setFont(lblProgress.getFont().deriveFont(Font.PLAIN, 12f));

        // Переключатели вариантов ответа создаются во время работы программы.
        // В конструкторе форм панель остаётся пустым контейнером со свободной
        // компоновкой, поэтому раскладку задаём здесь: иначе добавленные
        // переключатели не получат размера и окажутся невидимыми.
        pnlOptions.setLayout(new javax.swing.BoxLayout(pnlOptions,
                javax.swing.BoxLayout.Y_AXIS));
    }

    /** Начинает новый сеанс контроля: отбирает вопросы и показывает первый. */
    public final void startNewSession() {
        session = new TestSession();
        btnAnswer.setText("Ответить");
        showQuestion();
    }

    private void showQuestion() {
        Question q = session.getCurrentQuestion();
        lblProgress.setText("Вопрос " + (session.getCurrentIndex() + 1)
                + " из " + session.getCount());
        txtQuestion.setText(q.getText());

        pnlOptions.removeAll();
        optionButtons.clear();
        group = new ButtonGroup();
        List<String> options = q.getOptions();
        for (int i = 0; i < options.size(); i++) {
            JRadioButton rb = new JRadioButton("<html><body style='width:620px'>"
                    + (i + 1) + ". " + escape(options.get(i)) + "</body></html>");
            rb.setFont(new Font(Font.SERIF, Font.PLAIN, 15));
            rb.setVerticalTextPosition(javax.swing.SwingConstants.TOP);
            rb.setAlignmentX(LEFT_ALIGNMENT);
            group.add(rb);
            optionButtons.add(rb);
            pnlOptions.add(rb);
            pnlOptions.add(javax.swing.Box.createVerticalStrut(10));
        }
        pnlOptions.add(javax.swing.Box.createVerticalGlue());
        pnlOptions.revalidate();
        pnlOptions.repaint();

        owner.setStatus("Выберите один из вариантов ответа и нажмите «Ответить».");
        btnAnswer.setText(session.isLast() ? "Завершить контроль" : "Ответить");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private int selectedOption() {
        for (int i = 0; i < optionButtons.size(); i++) {
            AbstractButton b = optionButtons.get(i);
            if (b.isSelected()) {
                return i + 1;
            }
        }
        return 0;
    }

    private void finish() {
        int score = session.getScore();
        String verdict = session.getVerdict();
        StringBuilder sb = new StringBuilder();
        sb.append("Правильных ответов: ").append(score)
          .append(" из ").append(session.getCount()).append('.').append('\n');
        sb.append("Оценка: ").append(score).append('.').append('\n').append('\n');
        sb.append(verdict);

        if (session.isPassed()) {
            owner.getStudent().setTestScore(score);
            JOptionPane.showMessageDialog(this, sb.toString(),
                    "Результат входного контроля", JOptionPane.INFORMATION_MESSAGE);
            owner.setStatus("Входной контроль пройден, оценка " + score
                    + ". Переход к вводу исходных данных.");
            owner.showCard(MainFrame.CARD_INPUT);
        } else {
            sb.append('\n').append('\n')
              .append("Повторить контроль можно только с разрешения преподавателя.");
            JOptionPane.showMessageDialog(this, sb.toString(),
                    "Результат входного контроля", JOptionPane.WARNING_MESSAGE);
            owner.setStatus("Входной контроль не пройден, оценка " + score
                    + " (требуется не менее " + QuestionBank.PASS_MARK + ").");
            btnAnswer.setText("Пройти контроль заново");
            btnAnswer.setEnabled(true);
            session = null;
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

        lblProgress = new javax.swing.JLabel();
        scrQuestion = new javax.swing.JScrollPane();
        txtQuestion = new javax.swing.JTextArea();
        pnlOptions = new javax.swing.JPanel();
        btnAnswer = new javax.swing.JButton();

        lblProgress.setText("Вопрос 1 из 5");

        txtQuestion.setEditable(false);
        txtQuestion.setLineWrap(true);
        txtQuestion.setWrapStyleWord(true);
        txtQuestion.setColumns(20);
        txtQuestion.setRows(3);
        scrQuestion.setViewportView(txtQuestion);

        javax.swing.GroupLayout pnlOptionsLayout = new javax.swing.GroupLayout(pnlOptions);
        pnlOptions.setLayout(pnlOptionsLayout);
        pnlOptionsLayout.setHorizontalGroup(
            pnlOptionsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 700, Short.MAX_VALUE)
        );
        pnlOptionsLayout.setVerticalGroup(
            pnlOptionsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 260, Short.MAX_VALUE)
        );

        btnAnswer.setText("Ответить");
        btnAnswer.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAnswerActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblProgress, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(scrQuestion, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlOptions, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnAnswer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblProgress)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scrQuestion, javax.swing.GroupLayout.PREFERRED_SIZE, 80, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnlOptions, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnAnswer))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void btnAnswerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAnswerActionPerformed
        if (session == null) {
            startNewSession();
            return;
        }
        int option = selectedOption();
        if (option == 0) {
            owner.setStatus("Ответ не выбран.");
            JOptionPane.showMessageDialog(this,
                    "Выберите один из вариантов ответа.",
                    "Ответ не выбран", JOptionPane.WARNING_MESSAGE);
            return;
        }
        session.answer(option);
        if (session.next()) {
            showQuestion();
        } else {
            finish();
        }
    }//GEN-LAST:event_btnAnswerActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAnswer;
    private javax.swing.JLabel lblProgress;
    private javax.swing.JPanel pnlOptions;
    private javax.swing.JScrollPane scrQuestion;
    private javax.swing.JTextArea txtQuestion;
    // End of variables declaration//GEN-END:variables
}
