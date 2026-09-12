package ru.vka.upo.math;

import java.util.Locale;

/**
 * Проверка всех формул пакета математики независимыми способами.
 *
 * Каждая проверка устроена так, что правильный ответ известен заранее –
 * из теории, из прямого счёта по определению или из независимой формулы, –
 * и сравнивается с тем, что выдаёт программа. Печатается сама разница,
 * чтобы было видно не только «сошлось или нет», но и насколько.
 *
 * Запуск (в поставку не входит, только для проверки):
 * <pre>
 * javac -encoding UTF-8 -d target/check -cp target/classes \
 *       src/test/java/ru/vka/upo/math/MathCheck.java
 * java -cp target/classes:target/check ru.vka.upo.math.MathCheck
 * </pre>
 */
public final class MathCheck {

    private static int failed;

    private MathCheck() {
    }

    public static void main(String[] args) {
        leastSquaresOnExactPolynomial();
        covarianceAgainstDirectInverse();
        sigmaAtZeroDegree();
        sigmaProfileShape();
        polynomialAndDerivative();
        normalEquationsLoseAccuracy();
        windowsAgainstDirectCount();
        geometryAgainstCosineLaw();
        rangeRateAgainstDifference();
        noiseStatistics();

        System.out.println();
        System.out.println(failed == 0
                ? "Все проверки пройдены."
                : "Не пройдено проверок: " + failed);
    }

    // ------------------------------------------------------------------
    // 1. МНК на измерениях, точно лежащих на полиноме
    // ------------------------------------------------------------------
    /**
     * Если измерения взяты точно с полинома степени m, то МНК обязан
     * вернуть его же коэффициенты: невязки равны нулю, и минимум суммы
     * квадратов достигается на исходном полиноме. Это самая строгая
     * проверка решателя: любая ошибка в разложении или в обратном ходе
     * сразу видна.
     */
    private static void leastSquaresOnExactPolynomial() {
        int n = 25;
        double step = 0.5;
        double scale = (n - 1) * step / 2.0;
        double[] want = {1000.0, -20.0, 3.0};  // коэффициенты при 1, x, x²

        double[] s = new double[n];
        double[] y = new double[n];
        boolean[] velocity = new boolean[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            s[i] = i * step - (n - 1) * step / 2.0;
            double x = s[i] / scale;
            y[i] = want[0] + want[1] * x + want[2] * x * x;
            weight[i] = 1.0;
        }
        double[] got = LeastSquares.forPolynomial(s, velocity, weight, 2, scale)
                .solve(y, weight);
        double worst = 0;
        for (int j = 0; j < want.length; j++) {
            worst = Math.max(worst, Math.abs(got[j] - want[j]));
        }
        report("МНК на точном полиноме: коэффициенты восстановлены",
                worst < 1e-9, "наибольшее расхождение " + fmt(worst));
    }

    // ------------------------------------------------------------------
    // 2. Корреляционная матрица против прямого обращения AᵀA
    // ------------------------------------------------------------------
    /**
     * При малой степени полинома матрица AᵀA ещё обусловлена терпимо,
     * и её можно обратить прямо, методом Гаусса в двойной точности.
     * Результат обязан совпасть с K = R⁻¹R⁻ᵀ, которую даёт разложение.
     * Так проверяется вся цепочка получения корреляционной матрицы.
     */
    private static void covarianceAgainstDirectInverse() {
        int n = 21;
        double step = 1.0;
        double scale = (n - 1) * step / 2.0;
        int m = 2;

        double[] s = new double[n];
        boolean[] velocity = new boolean[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            s[i] = i * step - (n - 1) * step / 2.0;
            weight[i] = 1.0 / 10.0;   // СКО измерения 10 м
        }
        double[][] k = LeastSquares.forPolynomial(s, velocity, weight, m, scale)
                .covariance();

        // независимо: собираем AᵀA и обращаем её
        int p = m + 1;
        double[][] ata = new double[p][p];
        for (int i = 0; i < n; i++) {
            double x = s[i] / scale;
            double[] a = new double[p];
            a[0] = weight[i];
            for (int j = 1; j < p; j++) {
                a[j] = a[j - 1] * x;
            }
            for (int r = 0; r < p; r++) {
                for (int c = 0; c < p; c++) {
                    ata[r][c] += a[r] * a[c];
                }
            }
        }
        double[][] inv = invert(ata);

        // сравнение ведётся относительно наибольшего элемента матрицы,
        // а не каждого в отдельности: внедиагональные элементы здесь
        // равны нулю с точностью до машинного нуля, и относительная
        // разница двух таких нулей ни о чём не говорит
        double largest = 0;
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                largest = Math.max(largest, Math.abs(inv[i][j]));
            }
        }
        double worst = 0;
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                worst = Math.max(worst, Math.abs(k[i][j] - inv[i][j]) / largest);
            }
        }
        report("Корреляционная матрица совпадает с прямым обращением AᵀA",
                worst < 1e-9, "наибольшее относительное расхождение " + fmt(worst));
    }

    // ------------------------------------------------------------------
    // 3. Случайная ошибка при нулевой степени полинома
    // ------------------------------------------------------------------
    /**
     * При m = 0 сглаживание сводится к обычному осреднению, и случайная
     * ошибка обязана равняться σ/√N в любой момент времени. Это то самое
     * единственное сочетание, при котором она постоянна по интервалу.
     */
    private static void sigmaAtZeroDegree() {
        int n = 49;
        double sigma = 10.0;
        double step = 1.0;
        double scale = (n - 1) * step / 2.0;

        double[] s = new double[n];
        boolean[] velocity = new boolean[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            s[i] = i * step;
            weight[i] = 1.0 / sigma;
        }
        LeastSquares lsq = LeastSquares.forPolynomial(s, velocity, weight, 0, scale);

        double want = sigma / Math.sqrt(n);
        double worst = 0;
        for (double t = -20; t <= 70; t += 3.0) {
            worst = Math.max(worst, Math.abs(lsq.sigmaAt(t) - want));
        }
        report("При m = 0 случайная ошибка равна σ/√N = " + fmt(want) + " м всюду",
                worst < 1e-12, "наибольшее отклонение " + fmt(worst));
    }

    // ------------------------------------------------------------------
    // 4. Форма профиля случайной ошибки
    // ------------------------------------------------------------------
    /**
     * При m ≥ 1 случайная ошибка по интервалу непостоянна: у краёв выборки
     * она больше, чем в середине, а вне выборки растёт быстро. Профиль
     * симметричен относительно середины, если выборка расположена
     * симметрично относительно момента привязки.
     */
    private static void sigmaProfileShape() {
        int n = 49;
        double sigma = 10.0;
        double step = 1.0;
        double half = (n - 1) * step / 2.0;

        double[] s = new double[n];
        boolean[] velocity = new boolean[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            s[i] = i * step - half;   // привязка в середине выборки
            weight[i] = 1.0 / sigma;
        }
        LeastSquares lsq = LeastSquares.forPolynomial(s, velocity, weight, 2, half);

        double middle = lsq.sigmaAt(0.0);
        double edge = lsq.sigmaAt(half);
        double outside = lsq.sigmaAt(2 * half);
        double symmetry = Math.abs(lsq.sigmaAt(half) - lsq.sigmaAt(-half));

        report("Случайная ошибка у края выборки больше, чем в середине",
                edge > middle, String.format(Locale.ROOT,
                        "середина %.4g м, край %.4g м", middle, edge));
        report("При экстраполяции случайная ошибка растёт дальше",
                outside > edge, "вне выборки " + fmt(outside) + " м");
        report("Профиль симметричен относительно середины выборки",
                symmetry < 1e-12, "разность крыльев " + fmt(symmetry));
    }

    // ------------------------------------------------------------------
    // 5. Полином и его производная
    // ------------------------------------------------------------------
    /**
     * Значение полинома сверяется с прямым подсчётом по определению,
     * производная – с разностным отношением при малом приращении.
     */
    private static void polynomialAndDerivative() {
        double[] c = {5.0, -3.0, 2.0, 0.5};
        double x = 0.37;

        double direct = c[0] + c[1] * x + c[2] * x * x + c[3] * x * x * x;
        double got = Polynomial.value(c, x);
        report("Значение полинома совпадает с подсчётом по определению",
                Math.abs(got - direct) < 1e-12, "расхождение " + fmt(Math.abs(got - direct)));

        double h = 1e-6;
        double numeric = (Polynomial.value(c, x + h) - Polynomial.value(c, x - h)) / (2 * h);
        double exact = Polynomial.derivative(c, x);
        report("Производная полинома совпадает с разностным отношением",
                Math.abs(numeric - exact) < 1e-5,
                String.format(Locale.ROOT, "точно %.8g, разностно %.8g", exact, numeric));
    }

    // ------------------------------------------------------------------
    // 6. Потеря точности в нормальных уравнениях
    // ------------------------------------------------------------------
    /**
     * На тех же данных решение через нормальные уравнения в одинарной
     * точности при m = 0 и m = 1 совпадает с ортогональным разложением,
     * а при m = 4 расходится с ним на заметную величину. Это и есть
     * происхождение «полки» прежней программы.
     */
    private static void normalEquationsLoseAccuracy() {
        int n = 49;
        double step = 0.1;
        double scale = (n - 1) * step / 2.0;
        double[] s = new double[n];
        double[] y = new double[n];
        boolean[] velocity = new boolean[n];
        double[] weight = new double[n];
        for (int i = 0; i < n; i++) {
            s[i] = i * step - (n - 1) * step / 2.0;
            // дальность порядка миллиона метров, как в работе
            y[i] = 1.2345678e6 + 300.0 * s[i] - 1.5 * s[i] * s[i];
            weight[i] = 1.0;
        }
        for (int m : new int[] {1, 4}) {
            double[] exact = LeastSquares.forPolynomial(s, velocity, weight, m, scale)
                    .solve(y, weight);
            double[] single = NormalEquations.solve(s, y, m, scale);
            double diff = Math.abs(exact[0] - single[0]);
            if (m == 1) {
                report("При m = 1 одинарная точность ещё держится",
                        diff < 1.0, "расхождение в оценке дальности " + fmt(diff) + " м");
            } else {
                report("При m = 4 одинарная точность даёт заметную погрешность",
                        diff > 1e-3, "расхождение в оценке дальности " + fmt(diff) + " м");
            }
        }
    }

    // ------------------------------------------------------------------
    // 7. Разметка выборок
    // ------------------------------------------------------------------
    /**
     * Равномерная разметка сверяется с прямым счётом, разметка прежней
     * программы – с теми значениями, которые выдавала сама прежняя
     * программа при T<sub>К</sub> = 180 с и T = 0,1 с (35,9; 71,8; …).
     */
    private static void windowsAgainstDirectCount() {
        double[] uniform = SampleWindows.uniform(180.0, 49, 1.0, 5);
        double[] wantUniform = {0.0, 33.0, 66.0, 99.0, 132.0};
        double worst = 0;
        for (int i = 0; i < 5; i++) {
            worst = Math.max(worst, Math.abs(uniform[i] - wantUniform[i]));
        }
        report("Равномерная разметка: 0; 33; 66; 99; 132 с",
                worst < 1e-9, "наибольшее расхождение " + fmt(worst));

        double[] legacy = SampleWindows.legacy(180.0, 0.1, 5);
        double[] wantLegacy = {35.9, 71.8, 107.7, 143.6, 179.5};
        worst = 0;
        for (int i = 0; i < 5; i++) {
            worst = Math.max(worst, Math.abs(legacy[i] - wantLegacy[i]));
        }
        report("Разметка прежней программы при T = 0,1 с: 35,9; 71,8; 107,7; 143,6; 179,5 с",
                worst < 0.01, "наибольшее расхождение " + fmt(worst) + " с");

        // снос накопления при мелком шаге: последняя выборка не в 180 с
        double[] drift = SampleWindows.legacy(180.0, 0.01, 5);
        report("При T = 0,01 с накопление даёт снос: последняя выборка в "
                + fmt(drift[4]) + " с",
                Math.abs(drift[4] - 179.98) < 0.02, "ожидалось около 179,98 с");
    }

    // ------------------------------------------------------------------
    // 8. Дальность по теореме косинусов
    // ------------------------------------------------------------------
    /**
     * Дальность до объекта на орбите сверяется с прямым применением
     * теоремы косинусов, а её наименьшее значение – с разностью радиусов
     * при нулевом отклонении пункта от плоскости орбиты: в траверзе
     * объект проходит точно над пунктом, и дальность равна высоте орбиты.
     */
    private static void geometryAgainstCosineLaw() {
        double orbitRadius = Geometry.EARTH_RADIUS + 1000e3;
        double rate = Geometry.orbitalRate(orbitRadius);
        double cosBeta = Geometry.cosBeta(50e3);
        double t = 30.0;
        double traverse = 90.0;

        double cosGamma = Geometry.cosGamma(cosBeta, rate, t, traverse);
        double direct = Math.sqrt(Geometry.EARTH_RADIUS * Geometry.EARTH_RADIUS
                + orbitRadius * orbitRadius
                - 2 * Geometry.EARTH_RADIUS * orbitRadius * cosGamma);
        double got = Geometry.orbitalRange(orbitRadius, cosGamma);
        report("Дальность совпадает с теоремой косинусов",
                Math.abs(got - direct) < 1e-6, "расхождение " + fmt(Math.abs(got - direct)) + " м");

        double overhead = Geometry.orbitalRange(orbitRadius,
                Geometry.cosGamma(Geometry.cosBeta(0.0), rate, traverse, traverse));
        report("В траверзе при нулевом отстоянии от трассы дальность равна высоте орбиты",
                Math.abs(overhead - 1000e3) < 1e-6,
                "получено " + fmt(overhead / 1000.0) + " км");

        double period = 2 * Math.PI / rate;
        report("Период обращения на высоте 1000 км около 105 минут",
                Math.abs(period / 60.0 - 105.0) < 1.5,
                "получено " + fmt(period / 60.0) + " мин");
    }

    // ------------------------------------------------------------------
    // 9. Радиальная скорость против разностного отношения
    // ------------------------------------------------------------------
    /**
     * Радиальная скорость есть производная дальности по времени, поэтому
     * её обязано воспроизводить разностное отношение дальностей при малом
     * шаге. Проверяются обе обстановки: орбитальная и сближение по прямой.
     */
    private static void rangeRateAgainstDifference() {
        double orbitRadius = Geometry.EARTH_RADIUS + 1000e3;
        double rate = Geometry.orbitalRate(orbitRadius);
        double cosBeta = Geometry.cosBeta(50e3);
        double traverse = 90.0;
        double t = 40.0;
        double h = 0.001;

        double r = Geometry.orbitalRange(orbitRadius,
                Geometry.cosGamma(cosBeta, rate, t, traverse));
        double exact = Geometry.orbitalRangeRate(orbitRadius, cosBeta, rate, t, traverse, r);
        double numeric = (Geometry.orbitalRange(orbitRadius,
                    Geometry.cosGamma(cosBeta, rate, t + h, traverse))
                - Geometry.orbitalRange(orbitRadius,
                    Geometry.cosGamma(cosBeta, rate, t - h, traverse))) / (2 * h);
        report("Радиальная скорость на орбите совпадает с производной дальности",
                Math.abs(exact - numeric) < 1e-4,
                String.format(Locale.ROOT, "точно %.8g м/с, разностно %.8g м/с", exact, numeric));

        double speed = 3000.0;
        double d = 200e3;
        double rr = Geometry.relativeRange(d, speed, t, traverse);
        double exact2 = Geometry.relativeRangeRate(speed, t, traverse, rr);
        double numeric2 = (Geometry.relativeRange(d, speed, t + h, traverse)
                - Geometry.relativeRange(d, speed, t - h, traverse)) / (2 * h);
        report("Радиальная скорость при сближении по прямой совпадает с производной",
                Math.abs(exact2 - numeric2) < 1e-4,
                String.format(Locale.ROOT, "точно %.8g м/с, разностно %.8g м/с",
                        exact2, numeric2));
    }

    // ------------------------------------------------------------------
    // 10. Свойства шума измерений
    // ------------------------------------------------------------------
    /**
     * У длинной последовательности ошибок среднее должно быть близко
     * к нулю, а среднее квадратическое отклонение – к заданному. Кроме
     * того, при одном и том же зерне последовательности обязаны совпадать
     * полностью: расчёт воспроизводим.
     */
    private static void noiseStatistics() {
        int n = 100000;
        double sigma = 10.0;
        double[] a = Noise.sample(n, sigma, 20250101L);

        double sum = 0;
        for (double v : a) {
            sum += v;
        }
        double mean = sum / n;
        double var = 0;
        for (double v : a) {
            var += (v - mean) * (v - mean);
        }
        double sd = Math.sqrt(var / (n - 1));

        report("Среднее шума близко к нулю",
                Math.abs(mean) < 0.2, "получено " + fmt(mean) + " м");
        report("СКО шума близко к заданному " + fmt(sigma) + " м",
                Math.abs(sd - sigma) < 0.2, "получено " + fmt(sd) + " м");

        double[] b = Noise.sample(n, sigma, 20250101L);
        boolean same = true;
        for (int i = 0; i < n && same; i++) {
            same = a[i] == b[i];
        }
        report("При одном зерне последовательность повторяется", same, "");

        double[] c = Noise.sample(n, sigma, 1L);
        boolean differs = false;
        for (int i = 0; i < n && !differs; i++) {
            differs = a[i] != c[i];
        }
        report("При другом зерне последовательность иная", differs, "");
    }

    // ------------------------------------------------------------------
    // вспомогательное
    // ------------------------------------------------------------------

    /** Обращение матрицы методом Гаусса-Жордана в двойной точности. */
    private static double[][] invert(double[][] a) {
        int n = a.length;
        double[][] m = new double[n][n];
        double[][] e = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            e[i][i] = 1.0;
        }
        for (int c = 0; c < n; c++) {
            double diag = m[c][c];
            for (int j = 0; j < n; j++) {
                m[c][j] /= diag;
                e[c][j] /= diag;
            }
            for (int r = 0; r < n; r++) {
                if (r == c) {
                    continue;
                }
                double f = m[r][c];
                for (int j = 0; j < n; j++) {
                    m[r][j] -= f * m[c][j];
                    e[r][j] -= f * e[c][j];
                }
            }
        }
        return e;
    }

    private static void report(String what, boolean ok, String detail) {
        if (!ok) {
            failed++;
        }
        System.out.println((ok ? "  так  " : "НЕ ТАК ") + what
                + (detail.isEmpty() ? "" : " [" + detail + "]"));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.6g", v);
    }
}
