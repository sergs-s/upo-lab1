package ru.vka.upo.core;

import ru.vka.upo.math.ErrorFormulas;
import ru.vka.upo.math.LeastSquares;
import ru.vka.upo.math.Polynomial;
import ru.vka.upo.model.InputData;

/**
 * Ход ошибок оценивания по интервалу усреднения.
 *
 * Руководство к работе определяет динамическую ошибку как разность между
 * истинным значением параметра и значением аппроксимирующей функции
 * в произвольный момент времени при отсутствии шумов измерений:
 * Δ(t) = r(t) – r̂(t). В таблицу результатов выводится её значение
 * в момент привязки, однако сама величина определена на всём интервале
 * усреднения, и её ход по интервалу нагляден: он показывает, почему
 * положение момента привязки внутри выборки существенно.
 *
 * Здесь же вычисляется среднее квадратическое отклонение оценки σ(t) как
 * функция времени: σ²(t) = aᵀ(t) K a(t), где K – корреляционная матрица
 * ошибок коэффициентов, a(t) – вектор степеней нормированного времени.
 * В момент привязки Δ и σ равны ERD и ERS из соответствующей строки
 * таблицы, поэтому рисунок прямо связан с расчётом.
 */
public class ErrorProfile {

    private final Trajectory trajectory;
    private final double windowStart;
    private final double windowEnd;
    private final double anchorTime;
    private final double scale;
    private final double[] coeffs;
    private final double[][] covariance;

    public ErrorProfile(InputData data, Trajectory trajectory, double windowStart) {
        this.trajectory = trajectory;
        this.windowStart = windowStart;

        int n = data.getSampleSize();
        double step = data.getStep();
        this.windowEnd = windowStart + (n - 1) * step;
        this.anchorTime = windowStart + (data.getAnchor() - 1) * step;
        this.scale = Math.max((n - 1) * step / 2.0, 1e-9);

        boolean useRange = data.getMeasured().hasRange();
        boolean useSpeed = data.getMeasured().hasVelocity();
        int measurements = n * ((useRange ? 1 : 0) + (useSpeed ? 1 : 0));

        double[] s = new double[measurements];
        boolean[] velocity = new boolean[measurements];
        double[] weight = new double[measurements];
        double[] truth = new double[measurements];

        double wRange = useRange ? 1.0 / data.getSigmaRange() : 0.0;
        double wSpeed = useSpeed ? 1.0 / data.getSigmaVelocity() : 0.0;

        int idx = 0;
        for (int i = 0; i < n; i++) {
            double ti = windowStart + i * step;
            if (useRange) {
                s[idx] = ti - anchorTime;
                velocity[idx] = false;
                weight[idx] = wRange;
                truth[idx] = trajectory.range(ti);
                idx++;
            }
            if (useSpeed) {
                s[idx] = ti - anchorTime;
                velocity[idx] = true;
                weight[idx] = wSpeed;
                truth[idx] = trajectory.rangeRate(ti);
                idx++;
            }
        }

        int m = Math.min(data.getDegree(), n - 1);
        LeastSquares lsq = LeastSquares.forPolynomial(s, velocity, weight, m, scale);
        // измерения берутся без шума: остаётся одна динамическая составляющая
        this.coeffs = lsq.solve(truth, weight);
        this.covariance = lsq.covariance();
    }

    /** Значение аппроксимирующего полинома по незашумлённым измерениям, м. */
    public double fitted(double t) {
        return Polynomial.value(coeffs, (t - anchorTime) / scale);
    }

    /** Динамическая ошибка в произвольный момент времени, м. */
    public double dynamic(double t) {
        return trajectory.range(t) - fitted(t);
    }

    /** Среднее квадратическое отклонение оценки в произвольный момент, м. */
    public double random(double t) {
        return ErrorFormulas.sigma(covariance, (t - anchorTime) / scale);
    }

    /** Наибольшее по модулю значение динамической ошибки на интервале, м. */
    public double maxDynamic() {
        double max = 0;
        int steps = 400;
        for (int i = 0; i <= steps; i++) {
            double t = windowStart + (windowEnd - windowStart) * i / steps;
            max = Math.max(max, Math.abs(dynamic(t)));
        }
        return max;
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
}
