package ru.vka.upo.model;

import java.util.List;
import java.util.Random;

/**
 * Сеанс входного контроля знаний: последовательность вопросов,
 * ответы обучающегося, подсчёт баллов и выставление оценки.
 */
public class TestSession {

    private final List<Question> questions;
    private final int[] answers;
    private int current;

    public TestSession() {
        this(new Random());
    }

    public TestSession(Random random) {
        this.questions = QuestionBank.selectForSession(random);
        this.answers = new int[questions.size()];
        this.current = 0;
    }

    public int getCount() {
        return questions.size();
    }

    public int getCurrentIndex() {
        return current;
    }

    public Question getCurrentQuestion() {
        return questions.get(current);
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public int getAnswer(int index) {
        return answers[index];
    }

    /** Записывает ответ на текущий вопрос (нумерация вариантов с единицы). */
    public void answer(int option) {
        answers[current] = option;
    }

    public boolean isLast() {
        return current == questions.size() - 1;
    }

    public boolean next() {
        if (isLast()) {
            return false;
        }
        current++;
        return true;
    }

    /** Число правильных ответов; оно же выставляемая оценка. */
    public int getScore() {
        int score = 0;
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).isCorrect(answers[i])) {
                score++;
            }
        }
        return score;
    }

    public boolean isPassed() {
        return getScore() >= QuestionBank.PASS_MARK;
    }

    /**
     * Заключение по результатам контроля. Формулировки сохранены
     * от прежней программы, но приведены к более сдержанному виду.
     */
    public String getVerdict() {
        switch (getScore()) {
            case 5:
                return "Отлично. Можете приступать к работе.";
            case 4:
                return "Хорошо. Можете приступать к работе.";
            case 3:
                return "Удовлетворительно. Можете приступать к работе.";
            case 2:
                return "Ваши знания оставляют желать лучшего. К работе не допущены, "
                        + "обратитесь к преподавателю.";
            default:
                return "Вы не готовы к работе. Обратитесь к преподавателю.";
        }
    }
}
