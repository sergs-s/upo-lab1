package ru.vka.upo.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Notebook;

/**
 * Проверка расчёта при разных измеряемых параметрах и видах измерителя.
 *
 * Для наземного и бортового измерителя и для каждого признака скорости
 * (только дальность, только радиальная скорость, дальность и скорость)
 * считаются таблицы результатов по всем значениям изменяемого параметра
 * всех четырёх пунктов задания, при степени для пунктов б, в, г m = 2 и 3.
 * Проверяется:
 * <ol>
 *   <li>в выводимых столбцах нет NaN и бесконечностей, кроме ошибок
 *       скорости при m = 0, когда измеряется дальность (m – степень полинома
 *       дальности, скорость при m = 0 не оценивается, должно быть NaN);</li>
 *   <li>при измерении одной скорости ошибки дальности не определены (NaN),
 *       а ошибки скорости определены при любой m, в том числе m = 0: там m –
 *       степень полинома самой скорости, как в прежней программе;</li>
 *   <li>при измерении одной дальности числа совпадают с эталоном, снятым
 *       до доработки интерфейса под скорость (файл эталона).</li>
 * </ol>
 * Кроме того, при измерении одной скорости результат сверяется с прогоном
 * прежней программы (Obr rabot.exe под Wine, вариант 1: наземный
 * измеритель, H = 1000 км, до трассы 50 км, σV = 0,1 м/с, N = 49,
 * Δt = 0,1 с, M0 = 25, первая строка таблицы): EVS при m = 0…3 и EVD
 * при m = 0 и 1 в режиме «как в прежней программе».
 *
 * Режим расчёта – по умолчанию «в момент привязки»; задаётся ключом
 * -Dmode=anchor | averaged | legacy.
 *
 * Запуск (не входит в поставку, только для отработки):
 * <pre>
 * javac -encoding UTF-8 -d target/check -cp target/classes \
 *       src/test/java/ru/vka/upo/core/MeasuredCheck.java
 * java -cp target/classes:target/check ru.vka.upo.core.MeasuredCheck \
 *       src/test/resources/ru/vka/upo/core/Эталон_только_дальность.txt
 * </pre>
 * С ключом -Dwrite=true эталон не сверяется, а записывается заново в тот же
 * файл (так он и был снят на прежней версии программы).
 */
public final class MeasuredCheck {

    private MeasuredCheck() {
    }

    private static final Processor.Mode MODE = mode(System.getProperty("mode", "anchor"));

    private static Processor.Mode mode(String v) {
        if ("legacy".equalsIgnoreCase(v)) {
            return Processor.Mode.LEGACY;
        }
        if ("averaged".equalsIgnoreCase(v)) {
            return Processor.Mode.AVERAGED;
        }
        return Processor.Mode.ANCHOR;
    }

    /** Исходные данные: значения по умолчанию с заданными признаками. */
    private static InputData base(InputData.Measurer measurer, InputData.Measured measured) {
        InputData d = new InputData();
        d.setMeasurer(measurer);
        d.setMeasured(measured);
        return d;
    }

    public static void main(String[] args) throws IOException {
        Path file = Paths.get(args.length > 0 ? args[0]
                : "src/test/resources/ru/vka/upo/core/Эталон_только_дальность.txt");
        boolean write = Boolean.getBoolean("write");

        int checked = 0;
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        legacyVelocity(failures, notes);
        List<String> rangeLines = new ArrayList<>();

        for (InputData.Measurer measurer : InputData.Measurer.values()) {
            for (InputData.Measured measured : InputData.Measured.values()) {
                for (int chosen = 2; chosen <= 3; chosen++) {
                    for (Notebook.Item item : Notebook.Item.values()) {
                        if (item == Notebook.Item.A && chosen != 2) {
                            continue; // в пункте а степень и есть параметр
                        }
                        for (double value : item.getDefaults()) {
                            InputData d = item.apply(base(measurer, measured), value, chosen, true);
                            String where = String.format(Locale.ROOT, "%s, %s, пункт %s, %s = %s, m = %d",
                                    measurer, measured, item.getLetter(), item.getParameter(),
                                    num(value), d.getDegree());
                            List<ErrorRow> rows = new Processor(d).setMode(MODE).table();
                            for (int i = 0; i < rows.size(); i++) {
                                ErrorRow r = rows.get(i);
                                checked++;
                                String at = where + ", строка " + (i + 1);
                                check(failures, at, measured, d.getDegree(), r);
                                if (measured == InputData.Measured.RANGE) {
                                    rangeLines.add(String.format(Locale.ROOT,
                                            "%s|%s|%s|%d|%d|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g",
                                            measurer.name(), item.name(), num(value), chosen, i + 1,
                                            r.getTime(), r.getRangeDynamic(), r.getRangeRandom(),
                                            r.getRangeTotal(), r.getSpeedDynamic(),
                                            r.getSpeedRandom(), r.getSpeedTotal()));
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.printf("Режим расчёта: %s%n", MODE);
        System.out.printf("Проверено строк таблиц: %d%n", checked);

        if (write) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, rangeLines, StandardCharsets.UTF_8);
            System.out.printf("Эталон по дальности записан: %s (%d строк)%n", file, rangeLines.size());
        } else if (MODE != Processor.Mode.ANCHOR) {
            // эталон снят в режиме «в момент привязки»
            System.out.println("Сверка с эталоном по дальности: только в режиме «в момент привязки»");
        } else if (!Files.exists(file)) {
            failures.add("нет файла эталона " + file);
        } else {
            List<String> ref = Files.readAllLines(file, StandardCharsets.UTF_8);
            int diff = 0;
            if (ref.size() != rangeLines.size()) {
                failures.add("в эталоне " + ref.size() + " строк, в расчёте " + rangeLines.size());
            }
            for (int i = 0; i < Math.min(ref.size(), rangeLines.size()); i++) {
                if (!ref.get(i).equals(rangeLines.get(i))) {
                    diff++;
                    if (diff <= 10) {
                        failures.add("расхождение с эталоном:\n    было  " + ref.get(i)
                                + "\n    стало " + rangeLines.get(i));
                    }
                }
            }
            System.out.printf("Сверка с эталоном по дальности: строк %d, расхождений %d%n",
                    rangeLines.size(), diff);
        }

        if (!notes.isEmpty()) {
            System.out.println();
            System.out.println("Сверка с прежней программой (измеряется только скорость):");
            for (String n : notes) {
                System.out.println("  " + n);
            }
        }

        System.out.println();
        if (failures.isEmpty()) {
            System.out.println("Все проверки пройдены.");
        } else {
            System.out.println("Ошибки (" + failures.size() + "):");
            for (String f : failures) {
                System.out.println("  " + f);
            }
            System.exit(1);
        }
    }

    /** Проверка одной строки таблицы на NaN и бесконечности. */
    private static void check(List<String> failures, String at,
            InputData.Measured measured, int degree, ErrorRow r) {
        if (!finite(r.getTime())) {
            failures.add(at + ": момент времени не конечен");
        }
        double[] range = {r.getRangeDynamic(), r.getRangeRandom(), r.getRangeTotal()};
        double[] speed = {r.getSpeedDynamic(), r.getSpeedRandom(), r.getSpeedTotal()};
        if (measured.hasRange()) {
            for (double v : range) {
                if (!finite(v)) {
                    failures.add(at + ": ошибка дальности не конечна (" + v + ")");
                    break;
                }
            }
        } else {
            // дальность по одним измерениям скорости не наблюдаема:
            // столбцы дальности не показываются, пока это так
            boolean anyFinite = false;
            for (double v : range) {
                anyFinite |= finite(v);
            }
            if (anyFinite) {
                failures.add(at + ": ошибки дальности при измерении одной скорости "
                        + "должны быть не определены (NaN)");
            }
        }
        // ошибки скорости выводятся при любом признаке (при одной дальности –
        // дифференцированием полинома); при m = 0 в режиме «в момент
        // привязки» они не определены и должны быть NaN
        boolean undefined = degree == 0 && MODE == Processor.Mode.ANCHOR
                && measured != InputData.Measured.VELOCITY;
        for (double v : speed) {
            if (undefined && !Double.isNaN(v)) {
                failures.add(at + ": при m = 0 ошибка скорости должна быть не определена, а равна " + v);
                break;
            }
            if (!undefined && !finite(v)) {
                failures.add(at + ": ошибка скорости не конечна (" + v + ")");
                break;
            }
        }
    }

    /**
     * Сверка с прежней программой при измерении одной скорости. Числа сняты
     * прогоном Obr rabot.exe (признак траектории 1, признак скорости 1,
     * вариант 1, σV = 0,1 м/с, N = 49, Δt = 0,1 с, M0 = 25), первая строка.
     * Случайная составляющая от способа расчёта динамической не зависит и
     * должна совпасть в любом режиме; динамическая сверяется в режиме
     * «как в прежней программе» при m = 0 и 1 – при больших степенях там
     * «полка» одинарной точности, совпадающая лишь по порядку величины.
     */
    private static void legacyVelocity(List<String> failures, List<String> notes) {
        double[] evs = {0.01429, 0.01429, 0.02144, 0.02144};
        double[] evd = {55.27, 0.2262};
        for (int m = 0; m <= 3; m++) {
            InputData d = new InputData();
            d.setMeasurer(InputData.Measurer.GROUND);
            d.setMeasured(InputData.Measured.VELOCITY);
            d.setOrbitHeight(1000.0);
            d.setTrackDistance(50.0);
            d.setSigmaVelocity(0.1);
            d.setDegree(m);
            ErrorRow r = new Processor(d).setMode(Processor.Mode.LEGACY).table().get(0);
            double relS = r.getSpeedRandom() / evs[m] - 1;
            String line = String.format(Locale.ROOT,
                    "m = %d: EVS %.4g м/с (прежняя %.4g, %+.2f %%)", m,
                    r.getSpeedRandom(), evs[m], 100 * relS);
            if (Math.abs(relS) > 5e-4) {
                failures.add("расхождение с прежней программой по EVS: " + line);
            }
            if (m < evd.length) {
                double relD = r.getSpeedDynamic() / evd[m] - 1;
                line += String.format(Locale.ROOT, "; EVD %.4g м/с (прежняя %.4g, %+.2f %%)",
                        r.getSpeedDynamic(), evd[m], 100 * relD);
                if (Math.abs(relD) > 2e-3) {
                    failures.add("расхождение с прежней программой по EVD: " + line);
                }
            }
            notes.add(line);
        }
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
