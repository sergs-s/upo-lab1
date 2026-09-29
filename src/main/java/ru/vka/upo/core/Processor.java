package ru.vka.upo.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import ru.vka.upo.math.LeastSquares;
import ru.vka.upo.math.NormalEquations;
import ru.vka.upo.math.Polynomial;
import ru.vka.upo.math.SampleWindows;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Settings;

/**
 * Расчёт ошибок оценивания текущих навигационных параметров при
 * полиномиальном усреднении измерений.
 *
 * Для выборки из N измерений с шагом T строится матрица A, оценки
 * коэффициентов полинома находятся методом наименьших квадратов. Ошибка
 * оценивания складывается из двух составляющих:
 *
 * динамическая – разность между истинным значением параметра и значением
 * аппроксимирующего полинома в момент привязки при отсутствии шумов
 * измерений;
 *
 * случайная – среднее квадратическое отклонение оценки, определяемое
 * корреляционной матрицей K = σ²(AᵀA)⁻¹;
 *
 * полная – корень из суммы их квадратов.
 *
 * Способ вычисления динамической составляющей задаётся режимом
 * {@link Mode}: по умолчанию принято определение из руководства к работе,
 * два других режима сохранены для сопоставления с прежней программой.
 */
public class Processor {

    /** Число выводимых моментов времени. */
    public static final int ROWS = 5;

    /**
     * Способ вычисления динамической составляющей ошибки и размещения
     * выборок по интервалу измерений.
     */
    public enum Mode {

        /**
         * Определение из руководства к работе: динамическая ошибка есть
         * разность истинного значения и значения аппроксимирующей функции
         * в момент привязки при отсутствии шумов измерений. Выборки
         * размещаются так, чтобы каждая целиком лежала внутри интервала
         * измерений. Режим, принятый по умолчанию.
         */
        ANCHOR("в момент привязки (правильно)"),

        /**
         * Всё как в прежней программе, кроме вычислительной погрешности:
         * динамическая ошибка вычисляется как средняя квадратическая
         * невязка аппроксимации по всему интервалу усреднения (и потому
         * от момента привязки не зависит), выборки размещаются по-старому
         * (её разметка воспроизведена в {@link #windowStarts()}), но система
         * решается ортогональным разложением в двойной точности.
         * В этом режиме видно, что при высоких степенях полинома ошибка
         * продолжает убывать, а «полка» прежней программы к существу дела
         * отношения не имеет.
         */
        AVERAGED("средняя по интервалу усреднения"),

        /**
         * Воспроизведение прежней программы: её разметка выборок, средняя
         * квадратическая невязка вместо ошибки в момент привязки, решение
         * нормальных уравнений прямым обращением в одинарной точности и
         * вычисление невязок тоже в одинарной точности. Последнее и даёт
         * ту «полку», ниже которой прежняя программа не опускалась при
         * высоких степенях полинома.
         *
         * Что сверено с самой прежней программой и совпадает точно:
         * моменты времени в таблице, число выводимых строк, случайная
         * и полная случайная составляющие (ERS, EVS), а также независимость
         * динамической ошибки от момента привязки.
         *
         * Что совпадает лишь по порядку величины: сама «полка», то есть
         * значения динамической ошибки при степени полинома 2 и выше.
         * Там результат определяется не формулой, а конкретной
         * вычислительной погрешностью фортрановских подпрограмм, и без
         * их исходного текста повторить её поразрядно нельзя. При степенях
         * 0 и 1, где динамическая ошибка настоящая, а не вычислительная,
         * совпадение полное.
         */
        LEGACY("как в прежней программе (одинарная точность)");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private final InputData data;
    private final Trajectory trajectory;
    private Mode mode = ru.vka.upo.model.Settings.errorMode();

    public Processor(InputData data) {
        this(data, Trajectory.of(data));
    }

    public Processor(InputData data, Trajectory trajectory) {
        this.data = data;
        this.trajectory = trajectory;
    }

    /** Задаёт способ вычисления динамической составляющей ошибки. */
    public Processor setMode(Mode mode) {
        this.mode = mode == null ? Mode.ANCHOR : mode;
        return this;
    }

    public Mode getMode() {
        return mode;
    }

    /** Считается ли динамическая ошибка средней по интервалу усреднения. */
    public boolean isAveraged() {
        return mode != Mode.ANCHOR;
    }

    public Trajectory getTrajectory() {
        return trajectory;
    }

    /**
     * Таблица ошибок: пять выборок, равномерно смещённых по интервалу
     * измерений. Начало k-й выборки – k·T<sub>К</sub>/5.
     */
    public List<ErrorRow> table() {
        List<ErrorRow> rows = new ArrayList<>();
        for (double start : windowStarts()) {
            if (mode != Mode.ANCHOR && !SampleWindows.fitsLegacyLimit(start,
                    data.getInterval(), data.getSampleSize(), data.getStep())) {
                // прежняя программа такую выборку не считала: она не
                // укладывается в размеченный ею запас 1,2·T_К (проверено
                // прогонами самой программы, см. README)
                continue;
            }
            rows.add(row(start));
        }
        return rows;
    }

    /**
     * Начала пяти выборок.
     *
     * В режиме {@link Mode#ANCHOR} выборки размещаются равномерно так, чтобы
     * каждая целиком лежала внутри интервала измерений: первая начинается в его
     * начале, последняя им заканчивается.
     *
     * В режимах {@link Mode#AVERAGED} и {@link Mode#LEGACY} воспроизводится
     * разметка прежней программы. Она отсчитывала начало k-й выборки не по
     * времени, а по номеру измерения: шаг между выборками равен целому числу
     * измерений INT(T<sub>К</sub>/5/Δt), а сам момент набирается накоплением
     * шага Δt в одинарной точности. Из-за этого начала выборок слегка смещены
     * относительно k·T<sub>К</sub>/5 (например, при Δt=0,1 с шаг между выборками
     * получается 35,9 с вместо 36 с), а на мелком шаге заметен ещё и дрейф
     * накопления (при Δt=0,01 с последняя выборка начинается в 179,98 с).
     * Оба эффекта проверены прогонами прежней программы.
     */
    public double[] windowStarts() {
        // сами формулы разметки вынесены в пакет математики: там они
        // разобраны подробно и проверяются отдельно от программы
        return mode == Mode.ANCHOR
                ? SampleWindows.uniform(data.getInterval(), data.getSampleSize(),
                        data.getStep(), ROWS)
                : SampleWindows.legacy(data.getInterval(), data.getStep(), ROWS);
    }

    /** Ошибки оценивания для выборки, начинающейся в момент windowStart. */
    public ErrorRow row(double windowStart) {
        if (data.getMeasured() == InputData.Measured.VELOCITY) {
            return velocityOnlyRow(windowStart);
        }
        int n = data.getSampleSize();
        int m = data.getDegree();
        double t = data.getStep();
        // прежняя программа отсчитывала момент привязки от начала выборки
        // накоплением шага в одинарной точности, наш режим – умножением
        double t0 = mode == Mode.ANCHOR
                ? SampleWindows.anchorTime(windowStart, t, data.getAnchor())
                : SampleWindows.legacyAnchorTime(windowStart, t, data.getAnchor());
        double windowEnd = windowStart + (n - 1) * t;

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
            double ti = windowStart + i * t;
            if (useRange) {
                s[idx] = ti - t0;
                velocity[idx] = false;
                weight[idx] = wRange;
                truth[idx] = trajectory.range(ti);
                idx++;
            }
            if (useSpeed) {
                s[idx] = ti - t0;
                velocity[idx] = true;
                weight[idx] = wSpeed;
                truth[idx] = trajectory.rangeRate(ti);
                idx++;
            }
        }

        double scale = Math.max((n - 1) * t / 2.0, 1e-9);
        LeastSquares lsq = LeastSquares.forPolynomial(s, velocity, weight, m, scale);

        // случайная составляющая: диагональ корреляционной матрицы
        double[][] k = lsq.covariance();
        double randomRange = Math.sqrt(Math.max(k[0][0], 0.0));
        double randomRate = m >= 1 ? Math.sqrt(Math.max(k[1][1], 0.0)) / scale : Double.NaN;

        // динамическая составляющая: приближаем истинную зависимость без шумов.
        //
        // В режимах прежней программы полиномиальный базис строится не от
        // момента привязки, а от середины выборки. Так делала прежняя
        // программа: её динамическая ошибка от момента привязки не зависит,
        // что проверено её прогонами (при M0 = 5, 13, 25, 37, 49 она выдаёт
        // одни и те же числа). Если строить базис от точки привязки, при
        // смещённой привязке матрица нормальных уравнений становится плохо
        // обусловленной и в одинарной точности результат разваливается.
        double[] sFit = s;
        double tFit = t0;
        if (mode != Mode.ANCHOR) {
            tFit = windowStart + (n - 1) * t / 2.0;
            sFit = new double[measurements];
            for (int i = 0; i < measurements; i++) {
                sFit[i] = s[i] + (t0 - tFit);
            }
        }
        double[] c = mode == Mode.LEGACY
                ? NormalEquations.solve(sFit, truth, m, scale)
                : (sFit == s ? lsq : LeastSquares.forPolynomial(sFit, velocity, weight, m, scale))
                        .solve(truth, weight);
        double estimatedRange = c[0];
        double estimatedRate = m >= 1 ? c[1] / scale : 0.0;
        double dynamicRange = trajectory.range(t0) - estimatedRange;
        double dynamicRate = trajectory.rangeRate(t0) - estimatedRate;

        if (mode != Mode.ANCHOR) {
            // прежняя программа: не ошибка в момент привязки, а средняя
            // квадратическая невязка аппроксимации по всей выборке
            double sumR = 0;
            double sumV = 0;
            for (int i = 0; i < n; i++) {
                double ti = windowStart + i * t;
                double x = (ti - tFit) / scale;
                double dr;
                double dv;
                if (mode == Mode.LEGACY) {
                    // прежняя программа хранила и вычитала дальности
                    // в одинарной точности: при дальности порядка миллиона
                    // метров это само по себе даёт разброс в доли метра
                    float fp = Polynomial.valueSingle(c, x);
                    float fdp = Polynomial.derivativeSingle(c, x);
                    dr = (float) trajectory.range(ti) - fp;
                    dv = (float) trajectory.rangeRate(ti) - fdp / (float) scale;
                } else {
                    dr = trajectory.range(ti) - Polynomial.value(c, x);
                    dv = trajectory.rangeRate(ti) - Polynomial.derivative(c, x) / scale;
                }
                sumR += dr * dr;
                sumV += dv * dv;
            }
            return new ErrorRow(t0, windowStart, windowEnd,
                    Math.sqrt(sumR / n), randomRange,
                    Math.sqrt(sumV / n), m >= 1 ? randomRate : 0.0,
                    windowEnd <= data.getInterval() + 1e-9);
        }

        if (m < 1) {
            // полином нулевой степени не даёт оценки скорости
            dynamicRate = Double.NaN;
            randomRate = Double.NaN;
        }

        if (Settings.noiseMonteCarlo()) {
            // настройка noise.montecarlo: вместо аналитического расчёта –
            // прямое статистическое моделирование по тем же измерениям.
            // Подробное обоснование того, что это даёт тот же результат
            // с точностью до статистической погрешности моделирования, –
            // в записке «Аналитический расчёт и метод Монте-Карло».
            double[] mc = monteCarlo(lsq, truth, weight, scale, m,
                    trajectory.range(t0), trajectory.rangeRate(t0));
            dynamicRange = mc[0];
            randomRange = mc[1];
            if (m >= 1) {
                dynamicRate = mc[2];
                randomRate = mc[3];
            }
        }

        boolean inside = windowEnd <= data.getInterval() + 1e-9;
        return new ErrorRow(t0, windowStart, windowEnd,
                Math.abs(dynamicRange), randomRange,
                Math.abs(dynamicRate), randomRate, inside);
    }

    /**
     * Ошибки оценивания при измерении одной радиальной скорости.
     *
     * Так же, как в прежней программе (сверено её прогонами), измерения
     * скорости сглаживаются полиномом по времени, и степень m есть степень
     * полинома самой скорости: при m = 0 оценка скорости – среднее
     * арифметическое измерений выборки, её случайная ошибка σV/√N. Этим
     * измерение одной скорости отличается от совместного измерения
     * дальности и скорости, где m – степень полинома дальности, а скорость –
     * его производная (степень полинома скорости там на единицу меньше).
     *
     * Дальность по одним измерениям скорости не определяется, поэтому её
     * ошибки не определены (NaN). Прежняя программа печатала в этих столбцах
     * нули.
     *
     * Способы вычисления динамической ошибки ({@link Mode}) и размещение
     * выборок – те же, что при измерении дальности.
     */
    private ErrorRow velocityOnlyRow(double windowStart) {
        int n = data.getSampleSize();
        int m = data.getDegree();
        double t = data.getStep();
        double t0 = mode == Mode.ANCHOR
                ? SampleWindows.anchorTime(windowStart, t, data.getAnchor())
                : SampleWindows.legacyAnchorTime(windowStart, t, data.getAnchor());
        double windowEnd = windowStart + (n - 1) * t;

        // измерения скорости входят в систему как значения сглаживаемой
        // величины (а не как производная полинома дальности)
        double[] s = new double[n];
        boolean[] asDerivative = new boolean[n];
        double[] weight = new double[n];
        double[] truth = new double[n];
        double w = 1.0 / data.getSigmaVelocity();
        for (int i = 0; i < n; i++) {
            double ti = windowStart + i * t;
            s[i] = ti - t0;
            weight[i] = w;
            truth[i] = trajectory.rangeRate(ti);
        }

        double scale = Math.max((n - 1) * t / 2.0, 1e-9);
        LeastSquares lsq = LeastSquares.forPolynomial(s, asDerivative, weight, m, scale);
        double randomRate = Math.sqrt(Math.max(lsq.covariance()[0][0], 0.0));

        double[] sFit = s;
        double tFit = t0;
        if (mode != Mode.ANCHOR) {
            // как и для дальности: базис от середины выборки
            tFit = windowStart + (n - 1) * t / 2.0;
            sFit = new double[n];
            for (int i = 0; i < n; i++) {
                sFit[i] = s[i] + (t0 - tFit);
            }
        }
        double[] c = mode == Mode.LEGACY
                ? NormalEquations.solve(sFit, truth, m, scale)
                : (sFit == s ? lsq : LeastSquares.forPolynomial(sFit, asDerivative, weight, m, scale))
                        .solve(truth, weight);
        boolean inside = windowEnd <= data.getInterval() + 1e-9;

        if (mode != Mode.ANCHOR) {
            // прежняя программа: средняя квадратическая невязка по выборке
            double sumV = 0;
            for (int i = 0; i < n; i++) {
                double ti = windowStart + i * t;
                double x = (ti - tFit) / scale;
                double dv = mode == Mode.LEGACY
                        ? (float) trajectory.rangeRate(ti) - Polynomial.valueSingle(c, x)
                        : trajectory.rangeRate(ti) - Polynomial.value(c, x);
                sumV += dv * dv;
            }
            return new ErrorRow(t0, windowStart, windowEnd, Double.NaN, Double.NaN,
                    Math.sqrt(sumV / n), randomRate, inside);
        }

        double dynamicRate = trajectory.rangeRate(t0) - c[0];
        if (Settings.noiseMonteCarlo()) {
            // первый коэффициент здесь и есть оценка скорости
            double[] mc = monteCarlo(lsq, truth, weight, scale, m,
                    trajectory.rangeRate(t0), 0.0);
            dynamicRate = mc[0];
            randomRate = mc[1];
        }
        return new ErrorRow(t0, windowStart, windowEnd, Double.NaN, Double.NaN,
                Math.abs(dynamicRate), randomRate, inside);
    }

    /**
     * Прямое статистическое моделирование ошибок оценивания вместо
     * аналитического расчёта: используется только при настройке
     * noise.montecarlo (см. {@link Settings#noiseMonteCarlo()}).
     *
     * Метод наименьших квадратов линеен по измерениям (см. записку
     * «Аналитический расчёт и метод Монте-Карло», раздел 2), поэтому
     * повторное решение уже разложенной системы {@code lsq} с новыми
     * зашумлёнными измерениями обходится без нового разложения матрицы –
     * только обратный ход, и {@link Settings#monteCarloTrials()} опытов
     * выполняются быстро.
     *
     * Зерно датчика зависит от параметров выборки, а не от системного
     * времени: при неизменных исходных данных таблица результатов
     * остаётся воспроизводимой, как и при аналитическом расчёте.
     *
     * @param trueRange исходное, безошибочное значение дальности в момент
     *                  привязки – от опыта к опыту не меняется, поэтому
     *                  вычитается один раз, после накопления сумм
     * @param trueRate  то же для радиальной скорости
     * @return {динамическая ошибка дальности, случайная ошибка дальности,
     *          динамическая ошибка скорости, случайная ошибка скорости}
     */
    private static double[] monteCarlo(LeastSquares lsq, double[] truth,
            double[] weight, double scale, int degree,
            double trueRange, double trueRate) {
        int trials = Settings.monteCarloTrials();
        long seed = 0x5EED_0000_0000L
                ^ Double.doubleToLongBits(trueRange)
                ^ Double.doubleToLongBits(scale)
                ^ ((long) degree << 20)
                ^ truth.length;
        Random rnd = new Random(seed);

        // накапливаются сами оценки (не ошибки): среднее из них даёт оценку
        // динамической ошибки после вычитания истинного значения, а разброс
        // (дисперсия) от такого сдвига на постоянную величину не меняется
        double sumR = 0, sumR2 = 0, sumV = 0, sumV2 = 0;
        double[] noisy = new double[truth.length];
        for (int trial = 0; trial < trials; trial++) {
            for (int i = 0; i < truth.length; i++) {
                double sigma = weight[i] > 0 ? 1.0 / weight[i] : 0.0;
                noisy[i] = truth[i] + sigma * rnd.nextGaussian();
            }
            double[] c = lsq.solve(noisy, weight);
            sumR += c[0];
            sumR2 += c[0] * c[0];
            if (degree >= 1) {
                double v = c[1] / scale;
                sumV += v;
                sumV2 += v * v;
            }
        }
        double meanR = sumR / trials;
        double sdR = Math.sqrt(Math.max(sumR2 / trials - meanR * meanR, 0.0));
        double meanV = sumV / trials;
        double sdV = Math.sqrt(Math.max(sumV2 / trials - meanV * meanV, 0.0));
        return new double[] {
            Math.abs(trueRange - meanR), sdR,
            degree >= 1 ? Math.abs(trueRate - meanV) : Double.NaN,
            degree >= 1 ? sdV : Double.NaN
        };
    }

}
