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
        B("б", "T", "шаг измерений, с",
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
         * режим m = 3, N = 49, T = 1 с, M0 = 25 подходит и под пункт б
         * (там T как раз меняется, а M0 = 25 закреплено), и под пункт г
         * (там меняется M0, а T = 1 с закреплено). Поэтому этот признак
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
                sb.append("T = ").append(trim(step)).append(" с, ");
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

    /** Одна строка тетради: значение параметра и выписанные обучающимся числа. */
    public static class Line {

        private double parameter;
        private Double rangeDynamic;
        private Double rangeRandom;
        private Double rangeTotal;
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
            List<Line> known = new ArrayList<>();
            for (Line l : lines) {
                if (l.isFilled() && l.hasMode() && modeFits(l, item)) {
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
}
