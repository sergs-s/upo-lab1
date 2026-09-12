package ru.vka.upo.core;

/**
 * Взвешенный метод наименьших квадратов с полиномиальным базисом.
 *
 * Решение находится ортогональным разложением Хаусхолдера, а не обращением
 * матрицы нормальных уравнений: при высоких степенях полинома нормальная
 * матрица обусловлена крайне плохо, и её обращение в прежней программе
 * как раз и приводило к потере точности.
 *
 * Дополнительно относительное время нормируется на половину интервала
 * усреднения, так что аргумент полинома лежит в пределах от минус единицы
 * до единицы. Это ещё на несколько порядков улучшает обусловленность.
 */
public class Lsq {

    private final int rows;
    private final int cols;
    private final double[][] a;   // взвешенная матрица A
    private final double[] scale; // масштаб времени
    private double[][] r;         // верхнетреугольная матрица разложения
    private double[][] qt;        // Q в транспонированном виде

    private Lsq(int rows, int cols, double timeScale) {
        this.rows = rows;
        this.cols = cols;
        this.a = new double[rows][cols];
        this.scale = new double[] {timeScale};
    }

    /**
     * Создаёт задачу МНК для полиномиального усреднения.
     *
     * @param s        относительное время каждого измерения, с (отсчёт от привязки)
     * @param velocity признак того, что измерение относится к радиальной скорости
     * @param weight   вес измерения (единица, делённая на СКО)
     * @param degree   степень аппроксимирующего полинома
     * @param timeScale масштаб нормировки времени, с
     */
    public static Lsq forPolynomial(double[] s, boolean[] velocity, double[] weight,
            int degree, double timeScale) {
        int n = s.length;
        int p = degree + 1;
        Lsq l = new Lsq(n, p, timeScale);
        for (int i = 0; i < n; i++) {
            double x = s[i] / timeScale;
            for (int j = 0; j < p; j++) {
                double v;
                if (!velocity[i]) {
                    v = Math.pow(x, j);
                } else {
                    v = j == 0 ? 0.0 : j * Math.pow(x, j - 1) / timeScale;
                }
                l.a[i][j] = v * weight[i];
            }
        }
        l.decompose();
        return l;
    }

    /** Разложение Хаусхолдера: A = Q·R. */
    private void decompose() {
        double[][] m = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(a[i], 0, m[i], 0, cols);
        }
        double[][] q = identity(rows);

        for (int k = 0; k < cols; k++) {
            double norm = 0;
            for (int i = k; i < rows; i++) {
                norm += m[i][k] * m[i][k];
            }
            norm = Math.sqrt(norm);
            if (norm == 0) {
                continue;
            }
            if (m[k][k] > 0) {
                norm = -norm;
            }
            double[] v = new double[rows];
            for (int i = k; i < rows; i++) {
                v[i] = m[i][k];
            }
            v[k] -= norm;
            double vv = 0;
            for (int i = k; i < rows; i++) {
                vv += v[i] * v[i];
            }
            if (vv == 0) {
                continue;
            }
            // применяем отражение к m и накапливаем его в q
            for (int j = k; j < cols; j++) {
                double dot = 0;
                for (int i = k; i < rows; i++) {
                    dot += v[i] * m[i][j];
                }
                double f = 2 * dot / vv;
                for (int i = k; i < rows; i++) {
                    m[i][j] -= f * v[i];
                }
            }
            for (int j = 0; j < rows; j++) {
                double dot = 0;
                for (int i = k; i < rows; i++) {
                    dot += v[i] * q[i][j];
                }
                double f = 2 * dot / vv;
                for (int i = k; i < rows; i++) {
                    q[i][j] -= f * v[i];
                }
            }
        }
        this.r = m;
        this.qt = q;
    }

    private static double[][] identity(int n) {
        double[][] e = new double[n][n];
        for (int i = 0; i < n; i++) {
            e[i][i] = 1.0;
        }
        return e;
    }

    /**
     * Оценки коэффициентов полинома по вектору измерений.
     * Возвращаются коэффициенты при нормированном времени.
     */
    public double[] solve(double[] measurements, double[] weight) {
        double[] b = new double[rows];
        for (int i = 0; i < rows; i++) {
            b[i] = measurements[i] * weight[i];
        }
        double[] qtb = new double[rows];
        for (int i = 0; i < rows; i++) {
            double sum = 0;
            for (int j = 0; j < rows; j++) {
                sum += qt[i][j] * b[j];
            }
            qtb[i] = sum;
        }
        return backSubstitute(qtb);
    }

    private double[] backSubstitute(double[] y) {
        double[] c = new double[cols];
        for (int i = cols - 1; i >= 0; i--) {
            double sum = y[i];
            for (int j = i + 1; j < cols; j++) {
                sum -= r[i][j] * c[j];
            }
            c[i] = sum / r[i][i];
        }
        return c;
    }

    /**
     * Корреляционная матрица ошибок оценивания коэффициентов
     * при нормированном времени: K = (AᵀA)⁻¹ = R⁻¹·R⁻ᵀ.
     */
    public double[][] covariance() {
        double[][] inv = new double[cols][cols]; // R в минус первой степени
        for (int j = 0; j < cols; j++) {
            inv[j][j] = 1.0 / r[j][j];
            for (int i = j - 1; i >= 0; i--) {
                double sum = 0;
                for (int k = i + 1; k <= j; k++) {
                    sum += r[i][k] * inv[k][j];
                }
                inv[i][j] = -sum / r[i][i];
            }
        }
        double[][] k = new double[cols][cols];
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < cols; j++) {
                double sum = 0;
                for (int t = Math.max(i, j); t < cols; t++) {
                    sum += inv[i][t] * inv[j][t];
                }
                k[i][j] = sum;
            }
        }
        return k;
    }

    /** Масштаб нормировки времени, с. */
    public double timeScale() {
        return scale[0];
    }

    public int degree() {
        return cols - 1;
    }
}
