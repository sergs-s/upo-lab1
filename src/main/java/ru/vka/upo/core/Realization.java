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
 * Моделируются те измерения, которые заданы признаком скорости: дальности,
 * радиальной скорости или обе величины сразу; обрабатываются они так же,
 * как в расчёте таблицы (при измерении обеих величин – совместно, с весами,
 * обратными СКО; при измерении одной скорости – собственным полиномом
 * скорости). Поэтому среднее и разброс ошибок по опытам сходятся к числам
 * таблицы для любой из оцениваемых величин.
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

    /**
     * Измеряется одна радиальная скорость: полином описывает саму скорость
     * (как в {@link Processor}), дальность не оценивается.
     */
    private final boolean velocityOnly;
    private final boolean hasRange;
    private final boolean hasSpeed;

    private final double[] time;       // моменты измерений, с
    private final double[] truth;      // истинная дальность, м
    private final double[] measured;   // измеренная дальность, м
    private final double[] fitted;     // оценка дальности в моменты измерений, м
    private final double[] speedTruth; // истинная радиальная скорость, м/с
    private final double[] speedMeas;  // измеренная радиальная скорость, м/с
    private final double[] speedFit;   // оценка скорости в моменты измерений, м/с
    private final double[] coeffs;     // коэффициенты при нормированном времени
    private final int degree;

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
        InputData.Measured what = data.getMeasured() == null
                ? InputData.Measured.RANGE : data.getMeasured();
        this.velocityOnly = what == InputData.Measured.VELOCITY;
        this.hasRange = what.hasRange();
        this.hasSpeed = what.hasVelocity();

        time = new double[n];
        truth = new double[n];
        measured = new double[n];
        fitted = new double[n];
        speedTruth = new double[n];
        speedMeas = new double[n];
        speedFit = new double[n];

        // ошибки измерений: нормальный шум с заданным СКО, датчик
        // запускается с зерна, поэтому опыт воспроизводим; для скорости –
        // свой датчик, чтобы шум дальности от этого не менялся
        double[] noise = Noise.sample(n, data.getSigmaRange(), seed);
        double[] speedNoise = hasSpeed
                ? Noise.sample(n, data.getSigmaVelocity(), seed ^ 0x5BEEDL)
                : new double[n];
        for (int i = 0; i < n; i++) {
            time[i] = windowStart + i * t;
            truth[i] = trajectory.range(time[i]);
            measured[i] = truth[i] + noise[i];
            speedTruth[i] = trajectory.rangeRate(time[i]);
            speedMeas[i] = speedTruth[i] + speedNoise[i];
        }

        int m = Math.min(data.getDegree(), n - 1);
        this.degree = m;
        if (!hasSpeed) {
            // одна дальность: равноточные измерения, как было всегда
            boolean[] velocity = new boolean[n];
            double[] s = new double[n];
            double[] weight = new double[n];
            for (int i = 0; i < n; i++) {
                s[i] = time[i] - anchorTime;
                weight[i] = 1.0;
            }
            LeastSquares lsq = LeastSquares.forPolynomial(s, velocity, weight, m, scale);
            coeffs = lsq.solve(measured, weight);
        } else if (velocityOnly) {
            // одна скорость: сглаживается собственным полиномом скорости
            boolean[] velocity = new boolean[n];
            double[] s = new double[n];
            double[] weight = new double[n];
            for (int i = 0; i < n; i++) {
                s[i] = time[i] - anchorTime;
                weight[i] = 1.0;
            }
            LeastSquares lsq = LeastSquares.forPolynomial(s, velocity, weight, m, scale);
            coeffs = lsq.solve(speedMeas, weight);
        } else {
            // дальность и скорость: совместная обработка с весами 1/σ,
            // так же, как в расчёте таблицы
            int k = 2 * n;
            boolean[] velocity = new boolean[k];
            double[] s = new double[k];
            double[] weight = new double[k];
            double[] y = new double[k];
            for (int i = 0; i < n; i++) {
                s[2 * i] = time[i] - anchorTime;
                weight[2 * i] = 1.0 / data.getSigmaRange();
                y[2 * i] = measured[i];
                s[2 * i + 1] = time[i] - anchorTime;
                velocity[2 * i + 1] = true;
                weight[2 * i + 1] = 1.0 / data.getSigmaVelocity();
                y[2 * i + 1] = speedMeas[i];
            }
            LeastSquares lsq = LeastSquares.forPolynomial(s, velocity, weight, m, scale);
            coeffs = lsq.solve(y, weight);
        }
        for (int i = 0; i < n; i++) {
            fitted[i] = value(time[i]);
            speedFit[i] = speedValue(time[i]);
        }
    }

    /**
     * Оценка дальности в произвольный момент времени, м. При измерении одной
     * скорости дальность не оценивается – NaN.
     */
    public final double value(double t) {
        if (velocityOnly) {
            return Double.NaN;
        }
        return Polynomial.value(coeffs, (t - anchorTime) / scale);
    }

    /**
     * Оценка радиальной скорости в произвольный момент, м/с: производная
     * полинома дальности либо, при измерении одной скорости, сам полином
     * скорости. При полиноме дальности нулевой степени – NaN.
     */
    public final double speedValue(double t) {
        double x = (t - anchorTime) / scale;
        if (velocityOnly) {
            return Polynomial.value(coeffs, x);
        }
        if (degree < 1) {
            return Double.NaN;
        }
        return Polynomial.derivative(coeffs, x) / scale;
    }

    /** Оценка дальности в момент привязки, м. */
    public double estimate() {
        return value(anchorTime);
    }

    /** Ошибка оценки дальности в момент привязки в этой реализации, м. */
    public double error() {
        return estimate() - trajectory.range(anchorTime);
    }

    /** Ошибка оценки радиальной скорости в момент привязки, м/с. */
    public double speedError() {
        return speedValue(anchorTime) - trajectory.rangeRate(anchorTime);
    }

    /** Есть ли в этой реализации измерения дальности. */
    public boolean hasRangeMeasurements() {
        return hasRange;
    }

    /** Есть ли в этой реализации измерения скорости. */
    public boolean hasSpeedMeasurements() {
        return hasSpeed;
    }

    /** Степень полинома, использованная при обработке. */
    public int getDegree() {
        return degree;
    }

    /** Истинная радиальная скорость в моменты измерений, м/с. */
    public double[] getSpeedTruth() {
        return speedTruth;
    }

    /** Измеренная радиальная скорость, м/с (при её измерении). */
    public double[] getSpeedMeasured() {
        return speedMeas;
    }

    /** Оценка скорости в моменты измерений, м/с. */
    public double[] getSpeedFitted() {
        return speedFit;
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
