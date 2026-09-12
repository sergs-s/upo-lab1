package ru.vka.upo.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Таблица вариантов задания.
 *
 * По умолчанию используется встроенная таблица. Если рядом с программой
 * лежит файл {@code Варианты.csv}, таблица читается из него: преподаватель
 * может изменить варианты, не пересобирая программу.
 *
 * Формат файла: разделитель «;», десятичная запятая допускается,
 * строки, начинающиеся с «#», пропускаются. Столбцы:
 * номер; признак траектории (1 наземный, 2 бортовой); признак скорости
 * (0 дальность, 1 скорость, 2 обе); интервал измерений, с; высота орбиты, км;
 * расстояние до трассы, км; траверзное расстояние, км; СКО дальности, м;
 * СКО скорости, м/с.
 */
public final class VariantTable {

    /** Имя файла с вариантами, если он лежит рядом с программой. */
    public static final String FILE_NAME = "Варианты.csv";

    /** Запасное имя на случай, если система не умеет русские имена файлов. */
    public static final String FILE_NAME_ASCII = "variants.csv";

    /** Один вариант задания. */
    public static class Variant {

        private final int number;
        private final InputData data;

        public Variant(int number, InputData data) {
            this.number = number;
            this.data = data;
        }

        public int getNumber() {
            return number;
        }

        /** Копия исходных данных варианта: изменения не портят таблицу. */
        public InputData toInputData() {
            return data.clone();
        }

        @Override
        public String toString() {
            return "Вариант " + number + ": СКО " + trim(data.getSigmaRange()) + " м, H "
                    + trim(data.getOrbitHeight()) + " км, до трассы "
                    + trim(data.getTrackDistance()) + " км";
        }

        private static String trim(double v) {
            return String.format(Locale.ROOT, v == Math.rint(v) ? "%.0f" : "%s", v);
        }
    }

    private static List<Variant> cache;

    private VariantTable() {
    }

    public static synchronized List<Variant> variants() {
        if (cache == null) {
            List<Variant> external = readExternal();
            cache = external != null ? external : builtIn();
        }
        return cache;
    }

    /**
     * Вариант, назначаемый обучающемуся по его номеру в журнале группы.
     *
     * Номеров в журнале может быть больше, чем вариантов в таблице, поэтому
     * счёт идёт по кругу: при двадцати пяти вариантах номер 26 получает
     * первый вариант, номер 27 – второй и так далее.
     *
     * @param listNumber номер по списку в журнале, начиная с единицы
     */
    public static Variant byListNumber(int listNumber) {
        List<Variant> all = variants();
        if (all.isEmpty() || listNumber < 1) {
            return null;
        }
        return all.get((listNumber - 1) % all.size());
    }

    /** Перечитывает таблицу (например, после правки внешнего файла). */
    public static synchronized void reload() {
        cache = null;
    }

    /** Признак того, что таблица прочитана из внешнего файла. */
    public static boolean isExternal() {
        return externalPath() != null;
    }

    /**
     * Путь к внешнему файлу вариантов или null, если его нет.
     *
     * Имя файла русское, а кодировка имён файлов в системе может его
     * не поддерживать, поэтому обращение к пути защищено и предусмотрено
     * запасное имя латиницей.
     */
    public static Path externalPath() {
        for (String name : new String[] {FILE_NAME, FILE_NAME_ASCII}) {
            try {
                Path p = Paths.get(name).toAbsolutePath();
                if (Files.isReadable(p)) {
                    return p;
                }
            } catch (RuntimeException ignore) {
                // имя не представимо в кодировке файловой системы: пробуем следующее
            }
        }
        return null;
    }

    private static List<Variant> readExternal() {
        Path p = externalPath();
        if (p == null) {
            return null;
        }
        List<Variant> list = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Files.newInputStream(p), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.replace("﻿", "").trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(";");
                if (f.length < 9) {
                    continue;
                }
                try {
                    InputData d = new InputData();
                    int number = (int) num(f[0]);
                    d.setMeasurer((int) num(f[1]) == 2
                            ? InputData.Measurer.AIRBORNE : InputData.Measurer.GROUND);
                    int kv = (int) num(f[2]);
                    d.setMeasured(kv == 1 ? InputData.Measured.VELOCITY
                            : kv == 2 ? InputData.Measured.BOTH : InputData.Measured.RANGE);
                    d.setInterval(num(f[3]));
                    d.setOrbitHeight(num(f[4]));
                    d.setTrackDistance(num(f[5]));
                    d.setTraverseDistance(num(f[6]));
                    d.setSigmaRange(num(f[7]));
                    d.setSigmaVelocity(num(f[8]));
                    list.add(new Variant(number, d));
                } catch (NumberFormatException ignore) {
                    // строка с нечисловыми данными пропускается
                }
            }
        } catch (IOException e) {
            return null;
        }
        return list.isEmpty() ? null : Collections.unmodifiableList(list);
    }

    private static double num(String s) {
        return Double.parseDouble(s.trim().replace(',', '.'));
    }

    /**
     * Встроенная таблица вариантов. Наземный измеритель, измеряется только
     * дальность, интервал измерений 180 с.
     *
     * Первые пять вариантов взяты из задания кафедры без изменения. Остальные
     * двадцать получены полным перебором тех же значений трёх параметров:
     * СКО измерений (10, 15, 25, 35 и 50 м), высоты орбиты (1000, 800, 600,
     * 400 и 300 км) и расстояния до трассы полёта (50, 100, 150, 200 и
     * 250 км). Расстояние до трассы расставлено по правилу латинского
     * квадрата, поэтому в таблице нет ни одного повторяющегося сочетания,
     * а каждое значение каждого параметра встречается ровно пять раз.
     * Двадцати пяти вариантов хватает на учебную группу; вариант назначается
     * по номеру обучающегося в журнале.
     */
    private static List<Variant> builtIn() {
        double[][] rows = {
            //  СКО, м   H, км   до трассы, км
            {10, 1000, 50}, {15, 800, 100}, {25, 600, 150}, {35, 400, 200}, {50, 300, 250},
            {10, 800, 250}, {10, 600, 200}, {10, 400, 150}, {10, 300, 100}, {15, 1000, 150},
            {15, 600, 50}, {15, 400, 250}, {15, 300, 200}, {25, 1000, 250}, {25, 800, 200},
            {25, 400, 100}, {25, 300, 50}, {35, 1000, 100}, {35, 800, 50}, {35, 600, 250},
            {35, 300, 150}, {50, 1000, 200}, {50, 800, 150}, {50, 600, 100}, {50, 400, 50}
        };
        List<Variant> list = new ArrayList<>();
        for (int i = 0; i < rows.length; i++) {
            InputData d = new InputData();
            d.setMeasurer(InputData.Measurer.GROUND);
            d.setMeasured(InputData.Measured.RANGE);
            d.setInterval(180.0);
            d.setSigmaRange(rows[i][0]);
            d.setOrbitHeight(rows[i][1]);
            d.setTrackDistance(rows[i][2]);
            d.setTraverseDistance(0.0);
            d.setSigmaVelocity(0.0);
            list.add(new Variant(i + 1, d));
        }
        return Collections.unmodifiableList(list);
    }
}
