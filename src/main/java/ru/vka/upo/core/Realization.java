package ru.vka.upo.core;

import ru.vka.upo.math.LeastSquares;
import ru.vka.upo.math.Noise;
import ru.vka.upo.math.Polynomial;
import ru.vka.upo.model.InputData;

/**
 * Одна реализация процесса измерения и его обработки.
 *
 * В отличие от {@link Processor}, который считает характеристики ошибок
 * (то есть величины осреднённые, от случая не зависящие), здесь строится
 * именно то, что происходит в единичном опыте: истинный ход дальности,
 * конкретные зашумлённые отсчёты и проведённая по ним аппроксимирующая
 * кривая. Это нужно для иллюстрации: обучающийся видит, откуда берутся
 * динамическая и случайная составляющие ошибки.
 *
 * Реализация задаётся зерном датчика случайных чисел, поэтому картинку
 * можно повторить, а можно сменить, оставив всё остальное прежним.
 *
 * На числа в таблице результатов этот класс не влияет: таблица считается
 * {@link Processor}-ом аналитически, по истинной траектории и по формулам
 * раздела 4.2 руководства, и случайных чисел в ней нет. Смена зерна меняет
 * только график измерений с аппроксимирующей кривой и подпись под графиками
 * со статистикой накопленных опытов.
 */
public class Realization {

    private final InputData data;
    private final Trajectory trajectory;
    private final double windowStart;
    private final double windowEnd;
    private final double anchorTime;
    private final double scale;
    private final long seed;

    private final double[] time;      // моменты измерений, с
    private final double[] truth;     // истинная дальность, м
    private final double[] measured;  // измеренная дальность, м
    private final double[] fitted;    // значение аппроксимирующего полинома, м
    private final double[] coeffs;    // коэффициенты при нормированном времени

    public Realization(InputData data, Trajectory trajectory,
            double windowStart, long seed) {
        this.data = data;
        this.trajectory = trajectory;
        this.windowStart = windowStart;
        this.seed = seed;

        int n = data.getSampleSize();
        double t = data.getStep();
        this.windowEnd = windowStart + (n - 1) * t;
        this.anchorTime = windowStart + (data.getAnchor() - 1) * t;
        this.scale = Math.max((n - 1) * t / 2.0, 1e-9);

        time = new double[n];
        truth = new double[n];
        measured = new double[n];
        fitted = new double[n];

        // ошибки измерений: нормальный шум с заданным СКО, датчик
        // запускается с зерна, поэтому опыт воспроизводим
        double[] noise = Noise.sample(n, data.getSigmaRange(), seed);
        boolean[] velocity = new boolean[n];
        double[] s = new double[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            time[i] = windowStart + i * t;
            truth[i] = trajectory.range(time[i]);
            measured[i] = truth[i] + noise[i];
            s[i] = time[i] - anchorTime;
            weight[i] = 1.0;
        }

        int m = Math.min(data.getDegree(), n - 1);
        LeastSquares lsq = LeastSquares.forPolynomial(s, velocity, weight, m, scale);
        coeffs = lsq.solve(measured, weight);
        for (int i = 0; i < n; i++) {
            fitted[i] = value(time[i]);
        }
    }

    /** Значение аппроксимирующего полинома в произвольный момент времени, м. */
    public final double value(double t) {
        return Polynomial.value(coeffs, (t - anchorTime) / scale);
    }

    /** Оценка дальности в момент привязки, м. */
    public double estimate() {
        return coeffs[0];
    }

    /** Ошибка оценки в момент привязки в этой реализации, м. */
    public double error() {
        return estimate() - trajectory.range(anchorTime);
    }

    public InputData getData() {
        return data;
    }

    public Trajectory getTrajectory() {
        return trajectory;
    }

    public double[] getTime() {
        return time;
    }

    public double[] getTruth() {
        return truth;
    }

    public double[] getMeasured() {
        return measured;
    }

    public double[] getFitted() {
        return fitted;
    }

    public double getWindowStart() {
        return windowStart;
    }

    public double getWindowEnd() {
        return windowEnd;
    }

    public double getAnchorTime() {
        return anchorTime;
    }

    public long getSeed() {
        return seed;
    }
}
