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
 *       скорости при m = 0 (они не определены и должны быть NaN);</li>
 *   <li>при измерении одной скорости ошибки дальности не конечны – поэтому
 *       их столбцы в интерфейсе и отчёте не показываются (если ядро когда-либо
 *       начнёт выдавать здесь конечные числа, проверка об этом скажет);</li>
 *   <li>при измерении одной дальности числа совпадают с эталоном, снятым
 *       до доработки интерфейса под скорость (файл эталона).</li>
 * </ol>
 * Кроме того, выводится справочная сверка случайной ошибки скорости при
 * m = 1 с формулой σV/√N: при измерении одной скорости и полиноме первой
 * степени оценка скорости есть среднее N измерений. Расхождение с формулой
 * ошибкой проверки не считается – это замечание к расчётному ядру для автора.
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

    /** Исходные данные для бортового измерителя: разумные значения. */
    private static InputData base(InputData.Measurer measurer, InputData.Measured measured) {
        InputData d = new InputData();
        d.setMeasurer(measurer);
        d.setMeasured(measured);
        d.setTraverseDistance(5.0);
        d.setRelativeSpeed(300.0);
        return d;
    }

    public static void main(String[] args) throws IOException {
        Path file = Paths.get(args.length > 0 ? args[0]
                : "src/test/resources/ru/vka/upo/core/Эталон_только_дальность.txt");
        boolean write = Boolean.getBoolean("write");

        int checked = 0;
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
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
            speedNote(notes, measurer);
        }

        System.out.printf("Режим расчёта: %s%n", MODE);
        System.out.printf("Проверено строк таблиц: %d%n", checked);

        if (write) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, rangeLines, StandardCharsets.UTF_8);
            System.out.printf("Эталон по дальности записан: %s (%d строк)%n", file, rangeLines.size());
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
            System.out.println("Справочно (замечания к ядру, ошибкой проверки не считаются):");
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
            if (anyFinite && MODE == Processor.Mode.ANCHOR) {
                failures.add(at + ": ошибки дальности при измерении одной скорости "
                        + "оказались конечными – пересмотреть скрытие столбцов дальности");
            }
        }
        // ошибки скорости выводятся при любом признаке (при одной дальности –
        // дифференцированием полинома); при m = 0 в режиме «в момент
        // привязки» они не определены и должны быть NaN
        boolean undefined = degree == 0 && MODE == Processor.Mode.ANCHOR;
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
     * Справочная сверка: при измерении одной скорости и m = 1 оценка скорости
     * есть среднее N измерений, её СКО должно быть σV/√N.
     */
    private static void speedNote(List<String> notes, InputData.Measurer measurer) {
        InputData d = base(measurer, InputData.Measured.VELOCITY);
        d.setDegree(1);
        ErrorRow r = new Processor(d).setMode(Processor.Mode.ANCHOR).table().get(0);
        int n = d.getSampleSize();
        double expected = d.getSigmaVelocity() / Math.sqrt(n);
        double rel = (r.getSpeedRandom() - expected) / expected;
        if (Math.abs(rel) > 1e-6) {
            notes.add(String.format(Locale.ROOT,
                    "%s, только скорость, m = 1, N = %d: EVS = %.6g м/с, по формуле σV/√N = %.6g м/с "
                    + "(σV/√(N−1) = %.6g); расхождение %+.2f %%",
                    measurer, n, r.getSpeedRandom(), expected,
                    d.getSigmaVelocity() / Math.sqrt(n - 1), 100 * rel));
        }
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
