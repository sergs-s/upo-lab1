package ru.vka.upo.core;

/**
 * Строка таблицы ошибок оценивания: результат обработки одной выборки
 * измерений, привязанный к моменту времени t₀.
 */
public class ErrorRow {

    private final double time;          // момент привязки, с
    private final double windowStart;   // начало интервала усреднения, с
    private final double windowEnd;     // конец интервала усреднения, с
    private final double rangeDynamic;  // ERD, м
    private final double rangeRandom;   // ERS, м
    private final double speedDynamic;  // EVD, м/с
    private final double speedRandom;   // EVS, м/с
    private final boolean insideInterval;

    public ErrorRow(double time, double windowStart, double windowEnd,
            double rangeDynamic, double rangeRandom,
            double speedDynamic, double speedRandom, boolean insideInterval) {
        this.time = time;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.rangeDynamic = rangeDynamic;
        this.rangeRandom = rangeRandom;
        this.speedDynamic = speedDynamic;
        this.speedRandom = speedRandom;
        this.insideInterval = insideInterval;
    }

    public double getTime() {
        return time;
    }

    public double getWindowStart() {
        return windowStart;
    }

    public double getWindowEnd() {
        return windowEnd;
    }

    /** ERD – динамическая ошибка оценивания дальности, м. */
    public double getRangeDynamic() {
        return rangeDynamic;
    }

    /** ERS – случайная ошибка (СКО) оценивания дальности, м. */
    public double getRangeRandom() {
        return rangeRandom;
    }

    /** ER – полная средняя квадратическая ошибка оценивания дальности, м. */
    public double getRangeTotal() {
        return Math.hypot(rangeDynamic, rangeRandom);
    }

    /** EVD – динамическая ошибка оценивания радиальной скорости, м/с. */
    public double getSpeedDynamic() {
        return speedDynamic;
    }

    /** EVS – случайная ошибка (СКО) оценивания радиальной скорости, м/с. */
    public double getSpeedRandom() {
        return speedRandom;
    }

    /** EV – полная средняя квадратическая ошибка оценивания скорости, м/с. */
    public double getSpeedTotal() {
        return Math.hypot(speedDynamic, speedRandom);
    }

    /** Интервал усреднения целиком лежит внутри интервала измерений. */
    public boolean isInsideInterval() {
        return insideInterval;
    }
}
