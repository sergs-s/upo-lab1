package ru.vka.upo.model;

import java.util.Collections;
import java.util.List;

/**
 * Вопрос входного контроля знаний: формулировка, варианты ответа
 * и номер правильного варианта (нумерация с единицы).
 *
 * @author кафедра космической радиолокации и радионавигации
 */
public class Question {

    private final String id;
    private final String text;
    private final List<String> options;
    private final int correct;

    public Question(String id, String text, List<String> options, int correct) {
        if (correct < 1 || correct > options.size()) {
            throw new IllegalArgumentException("Номер правильного ответа вне диапазона: " + id);
        }
        this.id = id;
        this.text = text;
        this.options = Collections.unmodifiableList(options);
        this.correct = correct;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public List<String> getOptions() {
        return options;
    }

    /** Номер правильного варианта ответа, нумерация с единицы. */
    public int getCorrect() {
        return correct;
    }

    public boolean isCorrect(int answer) {
        return answer == correct;
    }

    @Override
    public String toString() {
        return id + ": " + text;
    }
}
