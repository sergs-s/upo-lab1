package ru.vka.upo.model;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Проверка допустимости исходных данных.
 *
 * В прежней программе при недопустимом значении выдавалось только
 * «Недопустимое значение! Повторите ввод!», а часть сочетаний вообще
 * не проверялась: например, момент привязки, выходящий за пределы выборки,
 * принимался и приводил к бессмысленным числам. Здесь каждое ограничение
 * сопровождается пояснением, что именно нарушено.
 */
public final class Validation {

    /** Замечание к исходным данным: поле и текст пояснения. */
    public static class Issue {

        private final String field;
        private final String message;
        private final boolean fatal;

        public Issue(String field, String message, boolean fatal) {
            this.field = field;
            this.message = message;
            this.fatal = fatal;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }

        /** true – расчёт невозможен; false – расчёт возможен, но данные сомнительны. */
        public boolean isFatal() {
            return fatal;
        }

        @Override
        public String toString() {
            return message;
        }
    }

    private Validation() {
    }

    public static List<Issue> check(InputData d) {
        List<Issue> issues = new ArrayList<>();

        if (d.getInterval() <= 0) {
            issues.add(new Issue("interval",
                    "Интервал измерений должен быть больше нуля.", true));
        }
        if (d.getStep() <= 0) {
            issues.add(new Issue("step",
                    "Шаг измерений должен быть больше нуля.", true));
        }
        if (d.getSampleSize() < 2) {
            issues.add(new Issue("sampleSize",
                    "Объём выборки должен быть не менее двух измерений.", true));
        }
        if (d.getDegree() < 0) {
            issues.add(new Issue("degree",
                    "Степень аппроксимирующего полинома не может быть отрицательной.", true));
        }
        if (d.getSampleSize() >= 2 && d.getDegree() > d.maxDegree()) {
            issues.add(new Issue("degree",
                    "Степень полинома " + d.getDegree() + " недопустима при объёме выборки "
                    + d.getSampleSize() + ": число оцениваемых коэффициентов не может "
                    + "превышать число измерений. Наибольшая допустимая степень – "
                    + d.maxDegree() + ".", true));
        } else if (d.getSampleSize() >= 2 && d.getDegree() == d.maxDegree()) {
            issues.add(new Issue("degree",
                    "Число коэффициентов полинома равно числу измерений: полином пройдёт "
                    + "точно через все измерения, усреднения не произойдёт и случайная "
                    + "ошибка будет наибольшей.", false));
        }
        if (d.getAnchor() < 1 || d.getAnchor() > d.getSampleSize()) {
            issues.add(new Issue("anchor",
                    "Момент привязки должен быть номером измерения внутри выборки, "
                    + "то есть от 1 до " + d.getSampleSize() + ".", true));
        }
        if (d.getInterval() > 0 && d.getStep() > 0 && d.getSampleSize() >= 2
                && d.averagingInterval() > d.getInterval()) {
            issues.add(new Issue("sampleSize",
                    "Интервал усреднения " + fmt(d.averagingInterval())
                    + " с не помещается в интервал измерений " + fmt(d.getInterval())
                    + " с. Уменьшите объём выборки или шаг измерений.", true));
        }

        if (d.getMeasured().hasRange() && d.getSigmaRange() <= 0) {
            issues.add(new Issue("sigmaRange",
                    "При измерении дальности СКО измерения дальности должно быть "
                    + "больше нуля.", true));
        }
        if (d.getMeasured().hasVelocity() && d.getSigmaVelocity() <= 0) {
            issues.add(new Issue("sigmaVelocity",
                    "При измерении радиальной скорости СКО измерения скорости должно быть "
                    + "больше нуля.", true));
        }

        // высота орбиты и расстояние до трассы задаются при любом измерителе:
        // у бортового измерителя он и объект лишь меняются местами
        if (d.getOrbitHeight() <= 0) {
            issues.add(new Issue("orbitHeight",
                    "Высота орбиты должна быть больше нуля.", true));
        }
        if (d.getTrackDistance() < 0) {
            issues.add(new Issue("trackDistance",
                    "Расстояние до трассы не может быть отрицательным.", true));
        }

        return Collections.unmodifiableList(issues);
    }

    /** Расчёт возможен, если нет ни одного неустранимого замечания. */
    public static boolean isComputable(List<Issue> issues) {
        for (Issue i : issues) {
            if (i.isFatal()) {
                return false;
            }
        }
        return true;
    }

    private static String fmt(double v) {
        String s = String.format(Locale.ROOT,
                v == Math.rint(v) ? "%.0f" : "%.4g", v);
        return s.replace('.', ',');
    }
}
