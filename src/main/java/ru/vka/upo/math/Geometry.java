package ru.vka.upo.math;

/**
 * Формулы измеряемых навигационных параметров: наклонной дальности
 * и радиальной скорости в двух обстановках, предусмотренных руководством
 * к работе.
 *
 * Класс содержит только формулы и не знает ни об исходных данных работы,
 * ни об экранах программы: на вход подаются числа, на выходе получаются
 * числа. Поэтому любую из формул можно проверить отдельно – подстановкой
 * в неё значений и сравнением с ручным расчётом или с разностным
 * отношением для производных.
 *
 * <h2>Наземный измеритель, объект на круговой орбите</h2>
 *
 * Измерительный пункт и объект видны из центра Земли под центральным углом
 * γ. По теореме косинусов для треугольника «центр Земли – пункт – объект»
 *
 * <pre>
 *     R(t) = √(R_з² + r_орб² − 2·R_з·r_орб·cos γ(t)),
 * </pre>
 *
 * где R_з – радиус Земли, r_орб – радиус орбиты. Объект движется по орбите
 * равномерно с угловой скоростью ω = √(μ/r_орб³) (третий закон Кеплера,
 * μ – геоцентрическая гравитационная постоянная), а пункт отстоит от трассы
 * полёта на заданное расстояние, чему отвечает угол β. Тогда
 *
 * <pre>
 *     cos γ(t) = cos β · cos(ω·(t − t_тр)),
 * </pre>
 *
 * где t_тр – момент траверза, то есть наибольшего сближения. Вращение Земли
 * не учитывается: на интервале измерений в несколько минут его вклад
 * пренебрежимо мал по сравнению с движением объекта по орбите.
 *
 * Радиальная скорость есть производная дальности по времени; она получается
 * дифференцированием того же выражения и отдельного численного
 * дифференцирования не требует.
 *
 * <h2>Бортовой измеритель, сближение по прямой</h2>
 *
 * Два объекта сближаются по прямой с постоянной относительной скоростью V,
 * наименьшее (траверзное) расстояние равно D. Тогда
 *
 * <pre>
 *     R(t) = √(D² + V²·(t − t_тр)²),
 *     Ṙ(t) = V²·(t − t_тр)/R(t).
 * </pre>
 *
 * Обе зависимости чётны относительно момента траверза – это свойство
 * пригодится при разборе формы динамической ошибки: полином нечётной
 * степени не может дать ничего сверх полинома предыдущей чётной степени,
 * если выборка расположена симметрично относительно траверза.
 */
public final class Geometry {

    /** Средний радиус Земли, м. */
    public static final double EARTH_RADIUS = 6371.0e3;

    /** Геоцентрическая гравитационная постоянная, м³/с². */
    public static final double MU = 3.986004418e14;

    private Geometry() {
    }

    /** Угловая скорость движения по круговой орбите, рад/с. */
    public static double orbitalRate(double orbitRadius) {
        return Math.sqrt(MU / (orbitRadius * orbitRadius * orbitRadius));
    }

    /**
     * Косинус углового отклонения измерительного пункта от плоскости
     * орбиты: расстояние по поверхности переводится в центральный угол.
     *
     * @param trackDistance расстояние от пункта до трассы полёта, м
     */
    public static double cosBeta(double trackDistance) {
        return Math.cos(trackDistance / EARTH_RADIUS);
    }

    /**
     * Косинус центрального угла между направлениями из центра Земли
     * на пункт и на объект.
     *
     * @param cosBeta   косинус отклонения пункта от плоскости орбиты
     * @param rate      угловая скорость движения по орбите, рад/с
     * @param t         момент времени, с
     * @param traverse  момент траверза, с
     */
    public static double cosGamma(double cosBeta, double rate, double t, double traverse) {
        return cosBeta * Math.cos(rate * (t - traverse));
    }

    /**
     * Наклонная дальность до объекта на круговой орбите, м.
     *
     * @param orbitRadius радиус орбиты, м
     * @param cosGamma    косинус центрального угла
     */
    public static double orbitalRange(double orbitRadius, double cosGamma) {
        double r2 = EARTH_RADIUS * EARTH_RADIUS + orbitRadius * orbitRadius
                - 2.0 * EARTH_RADIUS * orbitRadius * cosGamma;
        return Math.sqrt(Math.max(r2, 0.0));
    }

    /**
     * Радиальная скорость объекта на круговой орбите, м/с.
     *
     * Производная дальности по времени: дифференцируется подкоренное
     * выражение, в котором от времени зависит только cos γ.
     *
     * @param orbitRadius радиус орбиты, м
     * @param cosBeta     косинус отклонения пункта от плоскости орбиты
     * @param rate        угловая скорость движения по орбите, рад/с
     * @param t           момент времени, с
     * @param traverse    момент траверза, с
     * @param range       дальность в тот же момент, м
     */
    public static double orbitalRangeRate(double orbitRadius, double cosBeta,
            double rate, double t, double traverse, double range) {
        if (range == 0) {
            return 0;
        }
        double dCosGamma = -cosBeta * Math.sin(rate * (t - traverse)) * rate;
        return -EARTH_RADIUS * orbitRadius * dCosGamma / range;
    }

    /**
     * Дальность при сближении по прямой, м.
     *
     * @param traverseRange траверзное расстояние D, м
     * @param speed         относительная скорость V, м/с
     * @param t             момент времени, с
     * @param traverse      момент траверза, с
     */
    public static double relativeRange(double traverseRange, double speed,
            double t, double traverse) {
        double x = speed * (t - traverse);
        return Math.sqrt(traverseRange * traverseRange + x * x);
    }

    /**
     * Радиальная скорость при сближении по прямой, м/с.
     *
     * @param speed    относительная скорость V, м/с
     * @param t        момент времени, с
     * @param traverse момент траверза, с
     * @param range    дальность в тот же момент, м
     */
    public static double relativeRangeRate(double speed, double t, double traverse,
            double range) {
        if (range == 0) {
            return 0;
        }
        double x = speed * (t - traverse);
        return speed * x / range;
    }
}
