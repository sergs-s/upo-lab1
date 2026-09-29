package ru.vka.upo.core;

import ru.vka.upo.math.Geometry;
import ru.vka.upo.model.InputData;

/**
 * Модель изменения измеряемых текущих навигационных параметров во времени.
 *
 * Программа воспроизводит пролёт КА по круговой орбите над точкой на
 * поверхности Земли. При наземном измерителе в этой точке стоит измеритель,
 * а на КА – объект измерений; при бортовом они меняются местами: измеритель
 * на КА, объект на поверхности. Дальность между двумя точками и её
 * производная от того, какая из них измеритель, не зависят, поэтому
 * модель движения в обоих случаях одна и та же; скорость КА – первая
 * космическая для заданной высоты орбиты, измеряется её проекция на линию
 * визирования (радиальная скорость).
 */
public interface Trajectory {

    /** Истинная наклонная дальность в момент времени t, м. */
    double range(double t);

    /** Истинная радиальная скорость в момент времени t, м/с. */
    double rangeRate(double t);

    /** Момент наибольшего сближения (траверза), с. */
    double closestApproachTime();

    /** Модель по исходным данным. */
    static Trajectory of(InputData d) {
        return new Orbital(d);
    }

    /**
     * Наземный измеритель, объект на круговой орбите.
     *
     * Измеритель отстоит от трассы полёта (проекции орбиты на поверхность)
     * на заданное расстояние. Вращение Земли не учитывается: на интервале
     * измерений в несколько минут его вклад пренебрежимо мал по сравнению
     * с движением объекта по орбите.
     */
    class Orbital implements Trajectory {

        /** Средний радиус Земли, м. Формулы – в {@link Geometry}. */
        public static final double EARTH_RADIUS = Geometry.EARTH_RADIUS;
        /** Геоцентрическая гравитационная постоянная, м³/с². */
        public static final double MU = Geometry.MU;

        private final double orbitRadius;   // радиус орбиты, м
        private final double omega;         // угловая скорость движения по орбите, рад/с
        private final double cosBeta;       // косинус углового отклонения пункта от плоскости орбиты
        private final double traverse;      // момент траверза, с

        public Orbital(InputData d) {
            this.orbitRadius = EARTH_RADIUS + d.getOrbitHeight() * 1000.0;
            this.omega = Geometry.orbitalRate(orbitRadius);
            this.cosBeta = Geometry.cosBeta(d.getTrackDistance() * 1000.0);
            this.traverse = d.getInterval() / 2.0;
        }

        @Override
        public double range(double t) {
            return Geometry.orbitalRange(orbitRadius,
                    Geometry.cosGamma(cosBeta, omega, t, traverse));
        }

        @Override
        public double rangeRate(double t) {
            return Geometry.orbitalRangeRate(orbitRadius, cosBeta, omega, t,
                    traverse, range(t));
        }

        @Override
        public double closestApproachTime() {
            return traverse;
        }

        /** Угловая скорость движения по орбите, рад/с. */
        public double angularRate() {
            return omega;
        }

        /** Орбитальная скорость объекта, м/с. */
        public double orbitalSpeed() {
            return omega * orbitRadius;
        }

        /** Радиус орбиты, м. */
        public double getOrbitRadius() {
            return orbitRadius;
        }

        /**
         * Центральный угол между направлениями из центра Земли на измерительный
         * пункт и на объект, рад. Через него выражается наклонная дальность,
         * им же удобно строить чертёж пролёта.
         */
        public double centralAngle(double t) {
            double cosGamma = Geometry.cosGamma(cosBeta, omega, t, traverse);
            return Math.acos(Math.max(-1.0, Math.min(1.0, cosGamma)));
        }

        /** Наименьший центральный угол, достигаемый в траверзе, рад. */
        public double minCentralAngle() {
            return Math.acos(Math.max(-1.0, Math.min(1.0, cosBeta)));
        }
    }
}
