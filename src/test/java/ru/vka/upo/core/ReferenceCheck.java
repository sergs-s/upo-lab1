package ru.vka.upo.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import ru.vka.upo.model.InputData;

/**
 * Сверка расчётного ядра с эталонным прогоном прежней программы.
 *
 * Эталон снят автоматическим прогоном Obr rabot.exe по всем десяти
 * вариантам задания и хранится в файле «Эталон_ЛР1_полные_таблицы.csv»
 * (920 строк: пять строк таблицы для каждого расчёта).
 *
 * Сверка ведётся в режиме совместимости: динамическая ошибка вычисляется
 * так же, как её вычисляла прежняя программа. Расхождения по случайной
 * составляющей должны быть нулевыми – это чистая математика; по
 * динамической допускаются доли процента, поскольку прежняя программа
 * считала в одинарной точности и печатала четыре значащие цифры.
 *
 * Запуск (не входит в поставку, только для отработки):
 * <pre>
 * javac -encoding UTF-8 -d target/check -cp target/classes \
 *       src/test/java/ru/vka/upo/core/ReferenceCheck.java
 * java -cp target/classes:target/check ru.vka.upo.core.ReferenceCheck путь/к/эталону.csv
 * </pre>
 */
public final class ReferenceCheck {

    /** Допустимое относительное расхождение по случайной составляющей. */
    private static final double TOL_RANDOM = 0.005;
    /** Допустимое относительное расхождение по динамической составляющей. */
    private static final double TOL_DYNAMIC = 0.02;
    /** Значения меньше этого порога не сверяются: у прежней программы там
     *  сказывается «пол» одинарной точности (около метра). */
    private static final double FLOOR = Double.parseDouble(System.getProperty("floor", "2"));

    private ReferenceCheck() {
    }

    private static final class Ref {
        int variant;
        String item;
        String param;
        String value;
        int row;
        double erd;
        double evd;
        double ers;
        double evs;
    }

    /**
     * Режим расчёта, с которым идёт сверка: задаётся ключом -Dmode=averaged
     * (по умолчанию) или -Dmode=legacy.
     */
    private static final Processor.Mode MODE =
            "legacy".equalsIgnoreCase(System.getProperty("mode", "averaged"))
                    ? Processor.Mode.LEGACY : Processor.Mode.AVERAGED;

    public static void main(String[] args) throws IOException {
        Path file = Paths.get(args.length > 0 ? args[0]
                : "Эталон_ЛР1_полные_таблицы.csv");
        List<Ref> refs = read(file);
        System.out.printf("Эталонных строк: %d%n%n", refs.size());

        int checkedR = 0;
        int checkedD = 0;
        int badR = 0;
        int badD = 0;
        double worstR = 0;
        double worstD = 0;
        String worstDwhere = "";

        for (Ref f : refs) {
            InputData d = setup(f);
            if (d == null) {
                continue;
            }
            List<ErrorRow> table = new Processor(d).setMode(MODE).table();
            ErrorRow got = table.get(f.row - 1);

            double dr = rel(got.getRangeRandom(), f.ers);
            if (f.ers > 0) {
                checkedR++;
                worstR = Math.max(worstR, dr);
                if (dr > TOL_RANDOM) {
                    badR++;
                    if (badR <= 10) {
                        System.out.printf("ERS  в%2d %s %s=%s стр.%d: эталон %.4g, расчёт %.4g (%+.2f%%)%n",
                                f.variant, f.item, f.param, f.value, f.row,
                                f.ers, got.getRangeRandom(), 100 * (got.getRangeRandom() / f.ers - 1));
                    }
                }
            }
            if (f.evs > 0) {
                checkedR++;
                double dv = rel(got.getSpeedRandom(), f.evs);
                worstR = Math.max(worstR, dv);
                if (dv > TOL_RANDOM) {
                    badR++;
                    if (badR <= 10) {
                        System.out.printf("EVS  в%2d %s %s=%s стр.%d: эталон %.4g, расчёт %.4g (%+.2f%%)%n",
                                f.variant, f.item, f.param, f.value, f.row,
                                f.evs, got.getSpeedRandom(), 100 * (got.getSpeedRandom() / f.evs - 1));
                    }
                }
            }
            if (f.erd > FLOOR) {
                checkedD++;
                double dd = rel(got.getRangeDynamic(), f.erd);
                if (dd > worstD) {
                    worstD = dd;
                    worstDwhere = String.format("в%d %s %s=%s стр.%d",
                            f.variant, f.item, f.param, f.value, f.row);
                }
                if (dd > TOL_DYNAMIC) {
                    badD++;
                    if (badD <= 10) {
                        System.out.printf("ERD  в%2d %s %s=%s стр.%d: эталон %.4g, расчёт %.4g (%+.2f%%)%n",
                                f.variant, f.item, f.param, f.value, f.row,
                                f.erd, got.getRangeDynamic(), 100 * (got.getRangeDynamic() / f.erd - 1));
                    }
                }
            }
            if (f.evd > FLOOR) {
                checkedD++;
                double dd = rel(got.getSpeedDynamic(), f.evd);
                if (dd > worstD) {
                    worstD = dd;
                    worstDwhere = String.format("в%d %s %s=%s стр.%d (EVD)",
                            f.variant, f.item, f.param, f.value, f.row);
                }
                if (dd > TOL_DYNAMIC) {
                    badD++;
                    if (badD <= 10) {
                        System.out.printf("EVD  в%2d %s %s=%s стр.%d: эталон %.4g, расчёт %.4g (%+.2f%%)%n",
                                f.variant, f.item, f.param, f.value, f.row,
                                f.evd, got.getSpeedDynamic(), 100 * (got.getSpeedDynamic() / f.evd - 1));
                    }
                }
            }
        }

        System.out.printf("%nСлучайная составляющая: сверено %d, расхождений сверх %.1f%% – %d, наибольшее %.3f%%%n",
                checkedR, 100 * TOL_RANDOM, badR, 100 * worstR);
        System.out.printf("Динамическая составляющая: сверено %d, расхождений сверх %.1f%% – %d, наибольшее %.3f%% (%s)%n",
                checkedD, 100 * TOL_DYNAMIC, badD, 100 * worstD, worstDwhere);
    }

    private static double rel(double got, double ref) {
        if (Double.isNaN(got)) {
            return 1.0;
        }
        return Math.abs(got - ref) / Math.max(Math.abs(ref), 1e-12);
    }

    /** Исходные данные расчёта, которому отвечает эталонная строка. */
    private static InputData setup(Ref f) {
        InputData d = VariantOf(f.variant);
        if (d == null) {
            return null;
        }
        // базовый режим пунктов задания
        d.setDegree(3);
        d.setSampleSize(49);
        d.setStep(1.0);
        d.setAnchor(1);
        switch (f.item) {
            case "a":
                d.setDegree(Integer.parseInt(f.value));
                d.setStep(0.1);
                d.setAnchor(25);
                break;
            case "b":
                d.setStep(Double.parseDouble(f.value));
                d.setAnchor(25);
                break;
            case "c":
                d.setSampleSize(Integer.parseInt(f.value));
                d.setAnchor(1);
                break;
            case "d":
                d.setAnchor(Integer.parseInt(f.value));
                break;
            default:
                return null;
        }
        return d;
    }

    /**
     * Десять вариантов прежнего задания кафедры, при которых снимался эталон.
     * Таблица зашита здесь намеренно: рабочая таблица вариантов программы
     * с тех пор расширена, и сверка не должна от неё зависеть.
     */
    private static final double[][] LEGACY_VARIANTS = {
        //  СКО, м   H, км   до трассы, км
        {10, 1000, 50}, {15, 800, 100}, {25, 600, 150}, {35, 400, 200}, {50, 300, 250},
        {10, 1000, 50}, {15, 800, 100}, {25, 600, 150}, {35, 400, 200}, {50, 300, 250}
    };

    private static InputData VariantOf(int number) {
        if (number < 1 || number > LEGACY_VARIANTS.length) {
            return null;
        }
        double[] r = LEGACY_VARIANTS[number - 1];
        InputData d = new InputData();
        d.setMeasurer(InputData.Measurer.GROUND);
        d.setMeasured(InputData.Measured.RANGE);
        d.setInterval(180.0);
        d.setSigmaRange(r[0]);
        d.setOrbitHeight(r[1]);
        d.setTrackDistance(r[2]);
        d.setTraverseDistance(0.0);
        d.setSigmaVelocity(0.0);
        return d;
    }

    private static List<Ref> read(Path file) throws IOException {
        List<Ref> list = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String line = in.readLine(); // заголовок
            while ((line = in.readLine()) != null) {
                String[] p = line.split(";");
                if (p.length < 12) {
                    continue;
                }
                Ref f = new Ref();
                f.variant = Integer.parseInt(p[0].replace("﻿", "").trim());
                f.item = p[1].trim();
                f.param = p[2].trim();
                f.value = p[3].trim();
                f.row = Integer.parseInt(p[4].trim());
                f.erd = num(p[6]);
                f.evd = num(p[7]);
                f.ers = num(p[8]);
                f.evs = num(p[9]);
                list.add(f);
            }
        }
        return list;
    }

    private static double num(String s) {
        s = s.trim().replace(',', '.');
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
