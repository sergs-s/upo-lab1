package ru.vka.upo.ui;

import java.util.List;
import java.util.Random;
import ru.vka.upo.core.Trajectory;
import ru.vka.upo.math.Geometry;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.VariantTable;

/**
 * Расчёт для наглядного окна «Сглаживание измерений дальности полиномом»
 * ({@link SmoothingFrame}): пролёт объекта через зону видимости пункта,
 * 60 измерений дальности с шумом и их сглаживание полиномом первой степени
 * по участкам из 10 измерений.
 *
 * Расчётное ядро программы здесь не меняется и не используется для таблиц
 * результатов: берётся лишь та же модель движения ({@link Trajectory.Orbital}).
 * Зона видимости, моменты входа и выхода и МНК по участку считаются здесь.
 *
 * Время внутри класса – то же, что у модели движения (траверз в середине
 * интервала измерений варианта); на рисунках и в таблице оно отсчитывается
 * от входа в зону, для этого есть {@link #fromEntry(double)}.
 */
public final class SmoothingModel {

    /** Наименьший угол места, при котором объект считается видимым, градусы. */
    public static final double MIN_ELEVATION_DEG = 7.0;
    /** Число измерений за пролёт. */
    public static final int COUNT = 60;
    /** Число измерений на участке сглаживания. */
    public static final int SEGMENT = 10;
    /** Число участков. */
    public static final int SEGMENTS = COUNT / SEGMENT;
    /**
     * Разброс точек на графике, м: при реальном СКО в метры шум на шкале
     * в тысячи километров не виден, поэтому на рисунке он увеличен до
     * такого СКО. В таблицу идут настоящие числа.
     */
    public static final double DISPLAY_SIGMA = 50000.0;

    private final InputData data;
    private final Trajectory.Orbital orbit;
    private final double entry;
    private final double exit;
    private final double[] time = new double[COUNT];
    private final double[] trueRange = new double[COUNT];
    private final double[] noise = new double[COUNT];
    /** Коэффициенты {α0, α1} по измерениям и по истинным значениям для каждого участка. */
    private final double[][] fitMeasured = new double[SEGMENTS][];
    private final double[][] fitTrue = new double[SEGMENTS][];

    /**
     * @param data исходные данные (траектория и СКО дальности)
     * @param seed зерно датчика шума: новое при каждом «Заново»
     */
    public SmoothingModel(InputData data, long seed) {
        this.data = data;
        this.orbit = new Trajectory.Orbital(data);
        double traverse = orbit.closestApproachTime();
        // при центральном угле 90° и больше объект заведомо под горизонтом:
        // между этим моментом и траверзом угол места меняется монотонно
        double far = traverse - 0.5 * Math.PI / orbit.angularRate();
        this.entry = bisect(far, traverse);
        this.exit = bisect(2 * traverse - far, traverse);
        Random rnd = new Random(seed);
        double sigma = data.getSigmaRange();
        for (int i = 0; i < COUNT; i++) {
            time[i] = entry + i * (exit - entry) / (COUNT - 1);
            trueRange[i] = orbit.range(time[i]);
            noise[i] = sigma * rnd.nextGaussian();
        }
        double[] measured = new double[COUNT];
        for (int i = 0; i < COUNT; i++) {
            measured[i] = trueRange[i] + noise[i];
        }
        for (int s = 0; s < SEGMENTS; s++) {
            fitMeasured[s] = fitLine(s, measured);
            fitTrue[s] = fitLine(s, trueRange);
        }
    }

    /** Исходные данные варианта 1 таблицы вариантов (при её отсутствии – по умолчанию). */
    public static InputData variantOne() {
        List<VariantTable.Variant> all = VariantTable.variants();
        for (VariantTable.Variant v : all) {
            if (v.getNumber() == 1) {
                return v.toInputData();
            }
        }
        return all.isEmpty() ? new InputData() : all.get(0).toInputData();
    }

    /**
     * Угол места объекта в момент t, рад:
     * sin ε = ((R + H)·cos γ − R) / D.
     */
    public double elevation(double t) {
        double gamma = orbit.centralAngle(t);
        double d = orbit.range(t);
        double s = (orbit.getOrbitRadius() * Math.cos(gamma) - Geometry.EARTH_RADIUS) / d;
        return Math.asin(Math.max(-1.0, Math.min(1.0, s)));
    }

    /** Момент, где угол места равен наименьшему, между a (ниже) и b (выше). */
    private double bisect(double below, double above) {
        double limit = Math.toRadians(MIN_ELEVATION_DEG);
        double lo = below;
        double hi = above;
        for (int k = 0; k < 200 && Math.abs(hi - lo) > 1e-9; k++) {
            double mid = 0.5 * (lo + hi);
            if (elevation(mid) < limit) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return 0.5 * (lo + hi);
    }

    /**
     * Прямая МНК R*(t) = α0 + α1·t по измерениям участка s, t – от момента
     * первого измерения участка. Прямые формулы линейной регрессии.
     */
    private double[] fitLine(int s, double[] values) {
        int from = s * SEGMENT;
        double t0 = time[from];
        double st = 0;
        double sv = 0;
        for (int i = from; i < from + SEGMENT; i++) {
            st += time[i] - t0;
            sv += values[i];
        }
        double mt = st / SEGMENT;
        double mv = sv / SEGMENT;
        double stt = 0;
        double stv = 0;
        for (int i = from; i < from + SEGMENT; i++) {
            double dt = time[i] - t0 - mt;
            stt += dt * dt;
            stv += dt * (values[i] - mv);
        }
        double a1 = stv / stt;
        return new double[] {mv - a1 * mt, a1};
    }

    public InputData getData() {
        return data;
    }

    public Trajectory.Orbital getOrbit() {
        return orbit;
    }

    /** Момент входа в зону видимости (время модели движения), с. */
    public double getEntry() {
        return entry;
    }

    /** Момент выхода из зоны видимости (время модели движения), с. */
    public double getExit() {
        return exit;
    }

    /** Длительность зоны видимости, с. */
    public double duration() {
        return exit - entry;
    }

    /** Время от входа в зону, с. */
    public double fromEntry(double t) {
        return t - entry;
    }

    /** Коэффициент увеличения разброса на графике. */
    public double displayFactor() {
        return DISPLAY_SIGMA / data.getSigmaRange();
    }

    /** Момент i-го измерения (с нуля), время модели движения, с. */
    public double time(int i) {
        return time[i];
    }

    public double trueRange(int i) {
        return trueRange[i];
    }

    public double noise(int i) {
        return noise[i];
    }

    public double measured(int i) {
        return trueRange[i] + noise[i];
    }

    /** Истинная дальность в произвольный момент модели движения, м. */
    public double trueRangeAt(double t) {
        return orbit.range(t);
    }

    /** Номер участка (с нуля), которому принадлежит измерение i. */
    public static int segmentOf(int i) {
        return i / SEGMENT;
    }

    /** Момент первого измерения участка – начало отсчёта t его полинома. */
    public double segmentStart(int s) {
        return time[s * SEGMENT];
    }

    /** Коэффициенты {α0, α1} полинома участка по реальным измерениям. */
    public double[] coefficients(int s) {
        return fitMeasured[s].clone();
    }

    /** Коэффициенты {α0, α1} полинома участка по истинным значениям. */
    public double[] trueCoefficients(int s) {
        return fitTrue[s].clone();
    }

    /** Значение полинома участка s (по реальным измерениям) в момент t модели, м. */
    public double polynomial(int s, double t) {
        return fitMeasured[s][0] + fitMeasured[s][1] * (t - segmentStart(s));
    }

    /** Значение полинома участка s по истинным значениям в момент t модели, м. */
    public double truePolynomial(int s, double t) {
        return fitTrue[s][0] + fitTrue[s][1] * (t - segmentStart(s));
    }

    /** Положение точки измерения на графике: R_ист + k·n, м. */
    public double displayMeasured(int i) {
        return trueRange[i] + displayFactor() * noise[i];
    }

    /**
     * Полином участка на графике – по «завышенным» точкам. МНК линеен,
     * поэтому это P_ист + k·(P_изм − P_ист).
     */
    public double displayPolynomial(int s, double t) {
        double pt = truePolynomial(s, t);
        return pt + displayFactor() * (polynomial(s, t) - pt);
    }

    /** Полином − измеренная в момент измерения i, м. */
    public double residual(int i) {
        return polynomial(segmentOf(i), time[i]) - measured(i);
    }

    /** Истинная − полином в момент измерения i, м. */
    public double error(int i) {
        return trueRange[i] - polynomial(segmentOf(i), time[i]);
    }

    /** СКО (корень из среднего квадрата) «полином − измеренная» по участку, м. */
    public double residualRms(int s) {
        double sum = 0;
        for (int i = s * SEGMENT; i < (s + 1) * SEGMENT; i++) {
            sum += residual(i) * residual(i);
        }
        return Math.sqrt(sum / SEGMENT);
    }

    /** СКО (корень из среднего квадрата) «истинная − полином» по участку, м. */
    public double errorRms(int s) {
        double sum = 0;
        for (int i = s * SEGMENT; i < (s + 1) * SEGMENT; i++) {
            sum += error(i) * error(i);
        }
        return Math.sqrt(sum / SEGMENT);
    }
}
