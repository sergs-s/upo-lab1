package ru.vka.upo.math;

import java.util.List;
import java.util.Locale;
import ru.vka.upo.core.ErrorRow;
import ru.vka.upo.core.Processor;
import ru.vka.upo.core.Realization;
import ru.vka.upo.core.Trajectory;
import ru.vka.upo.model.InputData;

/**
 * Сверка аналитического расчёта работы с прямым статистическим
 * моделированием (методом Монте-Карло).
 *
 * Программа считает ошибки по формулам, не обращаясь к датчику случайных
 * чисел. Здесь проверяется, даёт ли то же самое прямое моделирование:
 * измерения набираются датчиком случайных чисел с заданным СКО, по ним
 * методом наименьших квадратов проводится полином, берётся ошибка оценки
 * в момент привязки, и так много раз. Среднее по реализациям должно
 * сойтись к динамической ошибке, среднее квадратическое отклонение по
 * реализациям – к случайной.
 */
public final class MonteCarlo {

    private static final int TRIALS = 200000;

    public static void main(String[] args) {
        System.out.println("Сверка формул с прямым моделированием, "
                + TRIALS + " реализаций на режим");
        System.out.println();
        System.out.printf(Locale.ROOT,
                "%-28s %10s %10s %10s   %10s %10s %8s%n",
                "режим", "ERD форм.", "ERD мод.", "СКО средн.",
                "ERS форм.", "ERS мод.", "разн.%");

        run("m=0, N=25, T=0,1, M0=1", 0, 25, 0.1, 1);
        run("m=1, N=25, T=0,1, M0=1", 1, 25, 0.1, 1);
        run("m=2, N=25, T=0,1, M0=1", 2, 25, 0.1, 1);
        run("m=3, N=25, T=0,1, M0=1", 3, 25, 0.1, 1);
        run("m=4, N=25, T=0,1, M0=1", 4, 25, 0.1, 1);
        run("m=2, N=49, T=0,1, M0=25", 2, 49, 0.1, 25);
        run("m=2, N=49, T=1,0, M0=25", 2, 49, 1.0, 25);
        run("m=3, N=49, T=1,0, M0=1", 3, 49, 1.0, 1);
        run("m=1, N=13, T=0,5, M0=7", 1, 13, 0.5, 7);
    }

    private static void run(String title, int degree, int n, double step, int anchor) {
        InputData d = new InputData();
        d.setInterval(180.0);
        d.setOrbitHeight(1000.0);
        d.setTrackDistance(50.0);
        d.setSigmaRange(10.0);
        d.setDegree(degree);
        d.setSampleSize(n);
        d.setStep(step);
        d.setAnchor(anchor);

        Trajectory tr = Trajectory.of(d);
        List<ErrorRow> table = new Processor(d, tr).table();
        ErrorRow first = table.get(0);
        double erdFormula = first.getRangeDynamic();
        double ersFormula = first.getRangeRandom();

        // прямое моделирование: каждая реализация – свой набор измерений
        double sum = 0, sum2 = 0;
        for (int k = 0; k < TRIALS; k++) {
            Realization r = new Realization(d, tr, first.getWindowStart(), 1000L + k);
            double e = r.error();
            sum += e;
            sum2 += e * e;
        }
        double mean = sum / TRIALS;
        double sd = Math.sqrt((sum2 - TRIALS * mean * mean) / (TRIALS - 1));

        System.out.printf(Locale.ROOT,
                "%-28s %10.4f %10.4f %10.4f   %10.4f %10.4f %8.2f%n",
                title, erdFormula, Math.abs(mean), sd / Math.sqrt(TRIALS),
                ersFormula, sd, rel(ersFormula, sd));
    }

    private static double rel(double a, double b) {
        double base = Math.max(Math.abs(a), 1e-12);
        return 100.0 * Math.abs(a - b) / base;
    }
}
