package ru.vka.upo.model;

/**
 * Исходные данные для расчёта: параметры траектории и условий измерений
 * и параметры режима обработки.
 *
 * Обозначения соответствуют практикуму 3718-23 и прежней программе:
 * P0 – признак траектории, KV – признак измеряемых параметров,
 * TK – интервал измерений, m – степень аппроксимирующего полинома,
 * N – объём выборки, T – шаг измерений, M0 – момент привязки,
 * NIP – расстояние до трассы, HV – высота орбиты, SR и SV – СКО
 * измерений дальности и скорости. Траверзного расстояния TR и
 * относительной скорости V0 прежней программы нет: при бортовом
 * измерителе он и объект лишь меняются местами (см. core.Trajectory),
 * и траектория задаётся теми же высотой орбиты и расстоянием до трассы.
 */
public class InputData implements Cloneable {

    /** Признак траектории: измеритель наземный или бортовой. */
    public enum Measurer {
        GROUND("наземный измеритель"),
        AIRBORNE("бортовой измеритель");

        private final String title;

        Measurer(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** Признак скорости: какие параметры измеряются. */
    public enum Measured {
        RANGE("только дальность"),
        VELOCITY("только радиальная скорость"),
        BOTH("дальность и радиальная скорость");

        private final String title;

        Measured(String title) {
            this.title = title;
        }

        public boolean hasRange() {
            return this != VELOCITY;
        }

        public boolean hasVelocity() {
            return this != RANGE;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private Measurer measurer = Measurer.GROUND;
    private Measured measured = Measured.RANGE;

    /** Интервал измерений TK, с. */
    private double interval = 180.0;
    /** Высота орбиты HV, км. */
    private double orbitHeight = 1000.0;
    /** Расстояние до трассы NIP, км: от точки на поверхности Земли (измерителя
     *  или, при бортовом измерителе, объекта) до проекции орбиты. */
    private double trackDistance = 50.0;

    /** СКО измерения дальности, м. */
    private double sigmaRange = 10.0;
    /**
     * СКО измерения радиальной скорости, м/с. Значение по умолчанию 0,01 м/с –
     * типовое для наземных измерительных пунктов, измеряющих КА (практикум
     * кафедры «РНСиК», 2018, лабораторная работа по вторичной обработке).
     */
    private double sigmaVelocity = 0.01;

    /** Степень аппроксимирующего полинома m. */
    private int degree = 0;
    /** Объём выборки N. */
    private int sampleSize = 49;
    /** Шаг измерений Δt, с. */
    private double step = 0.1;
    /** Момент привязки M0: номер измерения в выборке, нумерация с единицы. */
    private int anchor = 25;

    /** Длительность интервала усреднения Tу = (N − 1)Δt, с. */
    public double averagingInterval() {
        return (sampleSize - 1) * step;
    }

    /** Наибольшая допустимая степень полинома при текущем объёме выборки. */
    public int maxDegree() {
        return Math.max(0, sampleSize - 1);
    }

    /** Устанавливает привязку в середину выборки. */
    public void setAnchorToMiddle() {
        anchor = (sampleSize + 1) / 2;
    }

    public Measurer getMeasurer() {
        return measurer;
    }

    public void setMeasurer(Measurer measurer) {
        this.measurer = measurer;
    }

    public Measured getMeasured() {
        return measured;
    }

    public void setMeasured(Measured measured) {
        this.measured = measured;
    }

    public double getInterval() {
        return interval;
    }

    public void setInterval(double interval) {
        this.interval = interval;
    }

    public double getOrbitHeight() {
        return orbitHeight;
    }

    public void setOrbitHeight(double orbitHeight) {
        this.orbitHeight = orbitHeight;
    }

    public double getTrackDistance() {
        return trackDistance;
    }

    public void setTrackDistance(double trackDistance) {
        this.trackDistance = trackDistance;
    }

    public double getSigmaRange() {
        return sigmaRange;
    }

    public void setSigmaRange(double sigmaRange) {
        this.sigmaRange = sigmaRange;
    }

    public double getSigmaVelocity() {
        return sigmaVelocity;
    }

    public void setSigmaVelocity(double sigmaVelocity) {
        this.sigmaVelocity = sigmaVelocity;
    }

    public int getDegree() {
        return degree;
    }

    public void setDegree(int degree) {
        this.degree = degree;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    public double getStep() {
        return step;
    }

    public void setStep(double step) {
        this.step = step;
    }

    public int getAnchor() {
        return anchor;
    }

    public void setAnchor(int anchor) {
        this.anchor = anchor;
    }

    @Override
    public InputData clone() {
        try {
            return (InputData) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
