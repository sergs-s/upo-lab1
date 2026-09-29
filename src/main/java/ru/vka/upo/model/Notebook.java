package ru.vka.upo.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Рабочая тетрадь обучающегося.
 *
 * Программа сюда ничего не записывает: числа обучающийся переносит из таблицы
 * результатов сам, как переносил бы их в тетрадь на бумаге. По занесённым
 * числам строится график и пишется вывод по пункту задания. Смысл в том,
 * чтобы сохранить исследовательскую часть работы: программа считает и
 * рисует, но что считать, что выписать и что из этого следует – решает
 * обучающийся.
 */
public class Notebook {

    /** Пункт задания: какой параметр режима обработки в нём меняется. */
    public enum Item {

        A("а", "m", "степень аппроксимирующего полинома",
                new double[] {0, 1, 2, 3, 4}, 2, 49, 0.1, 25),
        B("б", "Δt", "шаг измерений, с",
                new double[] {0.01, 0.05, 0.1, 0.5, 1.0}, 3, 49, 0.1, 25),
        // в пункте в момент привязки не закреплён числом: по заданию он
        // каждый раз переносится в середину выборки, поэтому здесь стоит 0 –
        // признак того, что привязка вычисляется по объёму выборки
        C("в", "N", "объём выборки",
                new double[] {5, 13, 25, 37, 49}, 3, 49, 1.0, 0),
        D("г", "M0", "номер точки привязки",
                new double[] {5, 13, 25, 37, 49}, 3, 49, 1.0, 25);

        private final String letter;
        private final String parameter;
        private final String parameterTitle;
        private final double[] defaults;
        private final int degree;
        private final int sampleSize;
        private final double step;
        private final int anchor;

        Item(String letter, String parameter, String parameterTitle, double[] defaults,
                int degree, int sampleSize, double step, int anchor) {
            this.letter = letter;
            this.parameter = parameter;
            this.parameterTitle = parameterTitle;
            this.defaults = defaults;
            this.degree = degree;
            this.sampleSize = sampleSize;
            this.step = step;
            this.anchor = anchor;
        }

        public String getLetter() {
            return letter;
        }

        /** Обозначение изменяемого параметра. */
        public String getParameter() {
            return parameter;
        }

        public String getParameterTitle() {
            return parameterTitle;
        }

        /** Значения параметра, предусмотренные заданием. */
        public double[] getDefaults() {
            return defaults.clone();
        }

        /** По оси значений параметра удобнее логарифмический масштаб. */
        public boolean isLogParameter() {
            return this == B;
        }

        /**
         * Исходные данные для расчёта при заданном значении изменяемого
         * параметра: остальные параметры режима в пункте закреплены.
         *
         * В пунктах б, в и г степень полинома не задана заранее: по заданию
         * берётся та, которая в пункте а дала наименьшую полную ошибку.
         * Её обучающийся определяет сам и указывает в рабочей тетради.
         *
         * @param chosenDegree степень, принятая по итогам пункта а
         */
        public InputData apply(InputData base, double value, int chosenDegree) {
            return apply(base, value, chosenDegree, Settings.anchorMiddleInItemC());
        }

        /**
         * То же самое, но с явно заданной привязкой в пункте в): при
         * {@code middleInC} момент привязки переносится в середину выборки
         * и меняется вместе с N, иначе остаётся в начале интервала
         * усреднения (M0 = 1). Выбор делается в режиме преподавателя, а
         * значение по умолчанию берётся из настройки item.c.anchor.
         *
         * @param middleInC привязка в пункте в) к середине выборки
         */
        public InputData apply(InputData base, double value, int chosenDegree,
                boolean middleInC) {
            InputData d = base.clone();
            d.setDegree(this == A ? degree : chosenDegree);
            d.setSampleSize(sampleSize);
            d.setStep(step);
            d.setAnchor(anchor);
            switch (this) {
                case A: d.setDegree((int) Math.round(value)); break;
                case B: d.setStep(value); break;
                case C:
                    // по руководству к работе при каждом изменении объёма
                    // выборки привязка переносится в середину выборки: иначе
                    // она осталась бы на краю расширяющегося окна усреднения
                    // и в исследуемую зависимость вмешалось бы влияние
                    // второго параметра. Привязку к началу интервала
                    // (M0 = 1), как считала прежняя программа, оставляет
                    // настройка item.c.anchor – см. Settings
                    d.setSampleSize((int) Math.round(value));
                    if (middleInC) {
                        d.setAnchorToMiddle();
                    } else {
                        d.setAnchor(1);
                    }
                    break;
                default: d.setAnchor((int) Math.round(value)); break;
            }
            return d;
        }

        /**
         * Отвечает ли режим обработки, заданный обучающимся, этому пункту
         * задания: все параметры, которые в пункте закреплены, совпадают
         * с заданными, а изменяемый параметр может быть любым.
         *
         * Однозначно определить пункт по режиму обработки нельзя: например,
         * режим m = 3, N = 49, Δt = 1 с, M0 = 25 подходит и под пункт б
         * (там Δt как раз меняется, а M0 = 25 закреплено), и под пункт г
         * (там меняется M0, а Δt = 1 с закреплено). Поэтому этот признак
         * служит лишь подсказкой: какой именно опыт проводится, решает
         * обучающийся, и пункт он выбирает сам.
         *
         * @param chosenDegree степень, принятая по итогам пункта а
         */
        public boolean matches(InputData d, int chosenDegree) {
            if (this != A && d.getDegree() != chosenDegree) {
                return false;
            }
            if (this != C && d.getSampleSize() != sampleSize) {
                return false;
            }
            if (this != B && Math.abs(d.getStep() - step) > 1e-9) {
                return false;
            }
            if (this == C) {
                // здесь привязка не закреплена числом: по заданию она должна
                // стоять в середине выборки (см. InputData.setAnchorToMiddle),
                // а при item.c.anchor = start – в начале интервала усреднения
                return d.getAnchor() == (Settings.anchorMiddleInItemC()
                        ? (d.getSampleSize() + 1) / 2 : 1);
            }
            return this == D || d.getAnchor() == anchor;
        }

        /**
         * Значение изменяемого в этом пункте параметра при заданном режиме
         * обработки: именно оно служит в тетради подписью строки и точкой
         * по оси абсцисс на графике.
         */
        public double parameterValue(InputData d) {
            switch (this) {
                case A: return d.getDegree();
                case B: return d.getStep();
                case C: return d.getSampleSize();
                default: return d.getAnchor();
            }
        }

        /** Описание закреплённых параметров режима – для шапки таблицы. */
        public String fixedDescription(int chosenDegree) {
            StringBuilder sb = new StringBuilder("постоянны: ");
            if (this != A) {
                sb.append("m = ").append(chosenDegree)
                  .append(" (из пункта а), ");
            }
            if (this != C) {
                sb.append("N = ").append(sampleSize).append(", ");
            }
            if (this != B) {
                sb.append("Δt = ").append(trim(step)).append(" с, ");
            }
            if (this == C) {
                sb.append(Settings.anchorMiddleInItemC()
                        ? "M0 в середине выборки, "
                        : "M0 = 1 (в начале интервала усреднения), ");
            } else if (this != D) {
                sb.append("M0 = ").append(anchor).append(", ");
            }
            sb.setLength(sb.length() - 2);
            return sb.toString();
        }

        private static String trim(double v) {
            return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        }

        @Override
        public String toString() {
            return "пункт " + letter + ": " + parameterTitle;
        }
    }

    /**
     * Оцениваемая величина, по ошибкам которой ведутся таблица и график
     * тетради: дальность или радиальная скорость.
     *
     * Какие из них выписываются, определяется измеряемыми параметрами
     * (признаком скорости) в исходных данных: при измерении одной дальности –
     * только ошибки дальности, при измерении одной скорости – только ошибки
     * скорости (дальность в этом случае не наблюдаема), при измерении обоих
     * параметров – и те и другие. Ошибки с разными единицами на одном
     * графике не совмещаются.
     */
    public enum Quantity {

        RANGE("дальности", "м", "ERD", "ERS", "ER"),
        SPEED("радиальной скорости", "м/с", "EVD", "EVS", "EV");

        private final String genitive;
        private final String unit;
        private final String dynamicName;
        private final String randomName;
        private final String totalName;

        Quantity(String genitive, String unit, String dynamicName,
                String randomName, String totalName) {
            this.genitive = genitive;
            this.unit = unit;
            this.dynamicName = dynamicName;
            this.randomName = randomName;
            this.totalName = totalName;
        }

        /** Название величины в родительном падеже: «ошибки … дальности». */
        public String getGenitive() {
            return genitive;
        }

        /** Единица измерения ошибок: «м» или «м/с». */
        public String getUnit() {
            return unit;
        }

        /** Обозначение динамической составляющей: ERD или EVD. */
        public String getDynamicName() {
            return dynamicName;
        }

        /** Обозначение случайной составляющей: ERS или EVS. */
        public String getRandomName() {
            return randomName;
        }

        /** Обозначение полной ошибки: ER или EV. */
        public String getTotalName() {
            return totalName;
        }

        /** Подпись оси ошибок на графике: «ошибка, м» или «ошибка, м/с». */
        public String axisTitle() {
            return "ошибка, " + unit;
        }

        /**
         * Определена ли ошибка этой величины при заданной степени полинома.
         *
         * При измерении дальности (одной или вместе со скоростью) m – степень
         * полинома дальности, оценка скорости берётся из коэффициента при
         * первой степени, поэтому при m = 0 она не определена. При измерении
         * одной скорости m – степень полинома самой скорости (как в прежней
         * программе), и при m = 0 оценка скорости – среднее измерений.
         */
        public boolean isDefined(int degree, InputData.Measured measured) {
            return this == RANGE || degree >= 1 || measured == InputData.Measured.VELOCITY;
        }

        /** Величины, ошибки которых выписываются при заданных измеряемых параметрах. */
        public static List<Quantity> of(InputData.Measured measured) {
            List<Quantity> out = new ArrayList<>();
            if (measured == null || measured.hasRange()) {
                out.add(RANGE);
            }
            if (measured != null && measured.hasVelocity()) {
                out.add(SPEED);
            }
            return out;
        }

        /**
         * Величина, по полной ошибке которой в пункте а выбирается степень
         * полинома для пунктов б, в и г: при измерении дальности (одной или
         * вместе со скоростью) – по ER, как в практикуме; при измерении одной
         * скорости – по EV.
         */
        public static Quantity forDegreeChoice(InputData.Measured measured) {
            return measured == InputData.Measured.VELOCITY ? SPEED : RANGE;
        }
    }

    /** Одна строка тетради: значение параметра и выписанные обучающимся числа. */
    public static class Line {

        private double parameter;
        private Double rangeDynamic;
        private Double rangeRandom;
        private Double rangeTotal;
        private Double speedDynamic;
        private Double speedRandom;
        private Double speedTotal;
        /** Признак того, что число расходится с расчётом программы. */
        private boolean suspicious;

        /**
         * Режим обработки, при котором получены эти числа: степень полинома,
         * объём выборки, шаг измерений и момент привязки. Запоминается тогда,
         * когда числа попадают в тетрадь, и идёт в отчёт: там должно стоять
         * ровно то, что задавал обучающийся, а не то, что предписано пунктом
         * задания. Пусто, если числа занесены помимо расчёта.
         */
        private Integer degree;
        private Integer sampleSize;
        private Double step;
        private Integer anchor;

        public Line(double parameter) {
            this.parameter = parameter;
        }

        public double getParameter() {
            return parameter;
        }

        public void setParameter(double parameter) {
            this.parameter = parameter;
        }

        public Double getRangeDynamic() {
            return rangeDynamic;
        }

        public void setRangeDynamic(Double v) {
            this.rangeDynamic = v;
        }

        public Double getRangeRandom() {
            return rangeRandom;
        }

        public void setRangeRandom(Double v) {
            this.rangeRandom = v;
        }

        public Double getRangeTotal() {
            return rangeTotal;
        }

        public void setRangeTotal(Double v) {
            this.rangeTotal = v;
        }

        public Double getSpeedDynamic() {
            return speedDynamic;
        }

        public void setSpeedDynamic(Double v) {
            this.speedDynamic = v;
        }

        public Double getSpeedRandom() {
            return speedRandom;
        }

        public void setSpeedRandom(Double v) {
            this.speedRandom = v;
        }

        public Double getSpeedTotal() {
            return speedTotal;
        }

        public void setSpeedTotal(Double v) {
            this.speedTotal = v;
        }

        /** Динамическая ошибка заданной величины: ERD или EVD. */
        public Double getDynamic(Quantity q) {
            return q == Quantity.SPEED ? speedDynamic : rangeDynamic;
        }

        /** Случайная ошибка заданной величины: ERS или EVS. */
        public Double getRandom(Quantity q) {
            return q == Quantity.SPEED ? speedRandom : rangeRandom;
        }

        /** Полная ошибка заданной величины: ER или EV. */
        public Double getTotal(Quantity q) {
            return q == Quantity.SPEED ? speedTotal : rangeTotal;
        }

        /** Заносит три числа заданной величины. */
        public void set(Quantity q, Double dynamic, Double random, Double total) {
            if (q == Quantity.SPEED) {
                speedDynamic = dynamic;
                speedRandom = random;
                speedTotal = total;
            } else {
                rangeDynamic = dynamic;
                rangeRandom = random;
                rangeTotal = total;
            }
        }

        /** Стирает все выписанные числа строки – и дальности, и скорости. */
        public void clearNumbers() {
            set(Quantity.RANGE, null, null, null);
            set(Quantity.SPEED, null, null, null);
        }

        public boolean isSuspicious() {
            return suspicious;
        }

        public void setSuspicious(boolean suspicious) {
            this.suspicious = suspicious;
        }

        /** Все три числа выписаны. */
        public boolean isFilled() {
            return rangeDynamic != null && rangeRandom != null && rangeTotal != null;
        }

        /** Все три числа заданной величины выписаны. */
        public boolean isFilled(Quantity q) {
            return getDynamic(q) != null && getRandom(q) != null && getTotal(q) != null;
        }

        /**
         * Степень полинома, при которой получены числа этой строки: запомненная
         * вместе с ними, а если режим не запомнен – в пункте а сам изменяемый
         * параметр, в остальных пунктах степень, принятая по итогам пункта а.
         */
        public int degree(Item item, int chosenDegree) {
            if (degree != null) {
                return degree;
            }
            return item == Item.A ? (int) Math.round(parameter) : chosenDegree;
        }

        /**
         * Выписаны ли все числа, которые требуются при заданных измеряемых
         * параметрах. Ошибки скорости при m = 0 и измерении дальности не
         * определены и не требуются.
         */
        public boolean isComplete(InputData.Measured measured, Item item, int chosenDegree) {
            boolean any = false;
            for (Quantity q : Quantity.of(measured)) {
                if (!q.isDefined(degree(item, chosenDegree), measured)) {
                    continue;
                }
                if (!isFilled(q)) {
                    return false;
                }
                any = true;
            }
            return any;
        }

        /** Выписано ли хоть одно число строки – дальности или скорости. */
        public boolean hasAnyNumber() {
            return rangeDynamic != null || rangeRandom != null || rangeTotal != null
                    || speedDynamic != null || speedRandom != null || speedTotal != null;
        }

        /** Запоминает режим обработки, при котором получены эти числа. */
        public void setMode(InputData d) {
            degree = d == null ? null : d.getDegree();
            sampleSize = d == null ? null : d.getSampleSize();
            step = d == null ? null : d.getStep();
            anchor = d == null ? null : d.getAnchor();
        }

        /** Известен ли режим, при котором получены эти числа. */
        public boolean hasMode() {
            return degree != null && sampleSize != null
                    && step != null && anchor != null;
        }

        public Integer getDegree() {
            return degree;
        }

        public Integer getSampleSize() {
            return sampleSize;
        }

        public Double getStep() {
            return step;
        }

        public Integer getAnchor() {
            return anchor;
        }

        /** Исходные данные с запомненным режимом обработки. */
        public InputData toInputData(InputData base) {
            InputData d = base.clone();
            d.setDegree(degree);
            d.setSampleSize(sampleSize);
            d.setStep(step);
            d.setAnchor(anchor);
            return d;
        }
    }

    /** Содержимое одного пункта задания. */
    public static class Page {

        private final List<Line> lines = new ArrayList<>();
        private String conclusion = "";
        /** Номер строки таблицы результатов, из которой берутся числа. */
        private int sourceRow = 1;

        public List<Line> getLines() {
            return lines;
        }

        public String getConclusion() {
            return conclusion;
        }

        public void setConclusion(String conclusion) {
            this.conclusion = conclusion == null ? "" : conclusion;
        }

        public int getSourceRow() {
            return sourceRow;
        }

        public void setSourceRow(int sourceRow) {
            this.sourceRow = sourceRow;
        }

        /**
         * Строка, отвечающая заданному значению изменяемого параметра.
         *
         * Если такой строки ещё нет – значит обучающийся взял значение
         * не из числа предусмотренных заданием, – она заводится и ставится
         * на своё место по возрастанию параметра, чтобы точки на графике
         * шли по порядку. Уже занесённые числа при этом не трогаются.
         */
        public Line lineFor(double value) {
            for (Line l : lines) {
                // сравнение с допуском: шаг измерений – дробное число,
                // и точного равенства здесь ожидать нельзя
                double scale = Math.max(Math.abs(value), 1e-9);
                if (Math.abs(l.getParameter() - value) / scale < 1e-6) {
                    return l;
                }
            }
            Line added = new Line(value);
            int at = 0;
            while (at < lines.size() && lines.get(at).getParameter() < value) {
                at++;
            }
            lines.add(at, added);
            return added;
        }

        /**
         * Описание режима обработки для шапки таблицы в отчёте.
         *
         * Берётся из того, что обучающийся задавал на самом деле: вместе
         * с числами запоминается режим, при котором они получены. Параметр,
         * который в этом пункте меняется, не повторяется – он и так стоит
         * в первом столбце таблицы. Параметр, менявшийся вместе с ним
         * (в пункте в это момент привязки, переносимый в середину выборки),
         * выводится всеми своими значениями. Если режим не запомнен ни
         * в одной строке, остаётся описание по заданию.
         *
         * @param chosenDegree степень, принятая по итогам пункта а
         */
        public String conditions(Item item, int chosenDegree) {
            return conditions(item, chosenDegree, InputData.Measured.RANGE);
        }

        /**
         * То же с учётом измеряемых параметров: в описание идут строки, где
         * выписаны все числа, требуемые при этих параметрах (при измерении
         * одной скорости – ошибки скорости, при измерении обоих параметров –
         * и дальности, и скорости). Смысл описания от этого не меняется.
         *
         * @param measured измеряемые параметры по исходным данным
         */
        public String conditions(Item item, int chosenDegree, InputData.Measured measured) {
            List<Line> known = new ArrayList<>();
            for (Line l : lines) {
                if (l.isComplete(measured, item, chosenDegree) && l.hasMode()
                        && modeFits(l, item)) {
                    known.add(l);
                }
            }
            if (known.isEmpty()) {
                return item.fixedDescription(chosenDegree);
            }
            StringBuilder same = new StringBuilder();
            StringBuilder varied = new StringBuilder();
            for (Item p : new Item[] {Item.A, Item.C, Item.B, Item.D}) {
                if (p == item) {
                    continue;
                }
                List<String> v = distinct(known, p);
                String unit = p == Item.B ? " с" : "";
                if (v.size() == 1) {
                    if (same.length() > 0) {
                        same.append(", ");
                    }
                    same.append(p.getParameter()).append(" = ")
                        .append(v.get(0)).append(unit);
                    if (p == Item.A && v.get(0).equals(String.valueOf(chosenDegree))) {
                        same.append(" (из пункта а)");
                    }
                } else {
                    if (varied.length() > 0) {
                        varied.append("; ");
                    }
                    varied.append(p.getParameter()).append(" = ");
                    for (int i = 0; i < v.size(); i++) {
                        varied.append(i > 0 ? ", " : "").append(v.get(i));
                    }
                    varied.append(unit);
                }
            }
            StringBuilder sb = new StringBuilder();
            if (same.length() > 0) {
                sb.append("постоянны: ").append(same);
            }
            if (varied.length() > 0) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append("вместе с ").append(item.getParameter())
                  .append(" менялся ").append(varied);
            }
            return sb.toString();
        }

        /**
         * Относится ли запомненный режим именно к этой строке: значение
         * изменяемого в пункте параметра в режиме должно совпадать со
         * значением в первом столбце строки. Если обучающийся заносил числа
         * не сразу после расчёта, а позже и все подряд, у строк окажется
         * один и тот же (последний) режим – такие строки в описание условий
         * не берутся.
         */
        private static boolean modeFits(Line l, Item item) {
            double v;
            switch (item) {
                case A: v = l.getDegree(); break;
                case B: v = l.getStep(); break;
                case C: v = l.getSampleSize(); break;
                default: v = l.getAnchor(); break;
            }
            double scale = Math.max(Math.abs(l.getParameter()), 1e-9);
            return Math.abs(v - l.getParameter()) / scale < 1e-6;
        }

        /** Значения одного параметра режима по строкам, без повторов. */
        private static List<String> distinct(List<Line> src, Item parameter) {
            List<String> out = new ArrayList<>();
            for (Line l : src) {
                String s;
                switch (parameter) {
                    case A: s = String.valueOf(l.getDegree()); break;
                    case B: s = num(l.getStep()); break;
                    case C: s = String.valueOf(l.getSampleSize()); break;
                    default: s = String.valueOf(l.getAnchor()); break;
                }
                if (!out.contains(s)) {
                    out.add(s);
                }
            }
            return out;
        }

        private static String num(double v) {
            return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        }

        /** Заполнена ли страница настолько, чтобы строить график. */
        public boolean isReady() {
            int filled = 0;
            for (Line l : lines) {
                if (l.isFilled()) {
                    filled++;
                }
            }
            return filled >= 2;
        }

        /**
         * Заполнена ли страница настолько, чтобы строить графики по всем
         * величинам, которые выписываются при заданных измеряемых параметрах:
         * по каждой из них не меньше двух заполненных строк. Строки, где
         * ошибка скорости не определена (m = 0), для скорости не считаются.
         *
         * @param measured     измеряемые параметры по исходным данным
         * @param chosenDegree степень, принятая по итогам пункта а
         */
        public boolean isReady(InputData.Measured measured, Item item, int chosenDegree) {
            for (Quantity q : Quantity.of(measured)) {
                if (filledCount(q, item, chosenDegree, measured) < 2) {
                    return false;
                }
            }
            return true;
        }

        /** Число строк, где выписаны все три числа заданной величины. */
        public int filledCount(Quantity q, Item item, int chosenDegree,
                InputData.Measured measured) {
            int filled = 0;
            for (Line l : lines) {
                if (q.isDefined(l.degree(item, chosenDegree), measured) && l.isFilled(q)) {
                    filled++;
                }
            }
            return filled;
        }
    }

    private final Map<Item, Page> pages = new EnumMap<>(Item.class);

    /**
     * Степень полинома, принятая по итогам пункта а: та, при которой полная
     * ошибка оказалась наименьшей. По заданию она используется во всех
     * последующих пунктах.
     */
    private int chosenDegree = 2;

    public int getChosenDegree() {
        return chosenDegree;
    }

    public void setChosenDegree(int chosenDegree) {
        this.chosenDegree = chosenDegree;
    }

    public Notebook() {
        for (Item item : Item.values()) {
            Page p = new Page();
            for (double v : item.getDefaults()) {
                p.getLines().add(new Line(v));
            }
            pages.put(item, p);
        }
    }

    public Page page(Item item) {
        return pages.get(item);
    }

    /** Сколько пунктов задания заполнено настолько, чтобы строить график. */
    public int readyCount() {
        int n = 0;
        for (Page p : pages.values()) {
            if (p.isReady()) {
                n++;
            }
        }
        return n;
    }

    /** То же с учётом измеряемых параметров (см. {@link Page#isReady(InputData.Measured, Item, int)}). */
    public int readyCount(InputData.Measured measured) {
        int n = 0;
        for (Map.Entry<Item, Page> e : pages.entrySet()) {
            if (e.getValue().isReady(measured, e.getKey(), chosenDegree)) {
                n++;
            }
        }
        return n;
    }
}
