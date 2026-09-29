package ru.vka.upo.model;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Настройки, которые задаются не в программе, а в файле рядом с ней.
 *
 * Файл называется «Настройки.properties» (запасное имя латиницей –
 * settings.properties) и кладётся в ту же папку, что и программа. Если файла
 * нет, действуют значения по умолчанию. Кодировка файла UTF-8, строки вида
 *
 * <pre>
 * # пароль преподавателя
 * teacher.password = kaf33
 * </pre>
 */
public final class Settings {

    /** Имя настроечного файла. */
    public static final String FILE_NAME = "Настройки.properties";

    /** Запасное имя на случай, если система не умеет русские имена файлов. */
    public static final String FILE_NAME_ASCII = "settings.properties";

    /** Пароль преподавателя, действующий, пока не задан в настроечном файле. */
    public static final String DEFAULT_TEACHER_PASSWORD = "kaf33";

    private static Properties cache;

    private Settings() {
    }

    private static synchronized Properties props() {
        if (cache == null) {
            cache = new Properties();
            Path p = path();
            if (p != null) {
                try (Reader in = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    cache.load(in);
                } catch (IOException | IllegalArgumentException e) {
                    // повреждённый или нечитаемый файл: работаем на значениях
                    // по умолчанию, программу из-за этого не останавливаем
                }
            }
        }
        return cache;
    }

    /** Перечитывает настроечный файл. */
    public static synchronized void reload() {
        cache = null;
    }

    /** Путь к настроечному файлу или null, если его нет рядом с программой. */
    public static Path path() {
        for (String name : new String[] {FILE_NAME, FILE_NAME_ASCII}) {
            try {
                Path p = Paths.get(name).toAbsolutePath();
                if (Files.isReadable(p)) {
                    return p;
                }
            } catch (InvalidPathException e) {
                // кодировка имён файлов в системе не поддерживает это имя
            }
        }
        return search();
    }

    /**
     * Поиск настроечного файла просмотром текущего каталога.
     *
     * Нужен потому, что имя файла написано кириллицей, а имена файлов Java
     * читает в кодировке, которую сообщает система (свойство
     * sun.jnu.encoding). Если система запущена с локалью не UTF-8 (например,
     * LANG=C, что бывает при запуске из служб и из терминала без настроенной
     * локали), обращение по имени «Настройки.properties» файла не находит,
     * хотя файл лежит рядом. Просмотр каталога от кодировки имени не зависит:
     * система отдаёт имя и путь в одном и том же виде, и по такому пути файл
     * открывается.
     *
     * Настроечным считается файл с расширением .properties, в котором есть
     * хотя бы одна из известных настроек: так посторонний файл настроек,
     * случайно оказавшийся рядом, за наш принят не будет.
     */
    private static Path search() {
        Path dir = Paths.get("").toAbsolutePath();
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String name = p.getFileName().toString().toLowerCase();
                if (!name.endsWith(".properties") || !Files.isReadable(p)) {
                    continue;
                }
                Properties test = new Properties();
                try (Reader in = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    test.load(in);
                } catch (IOException | IllegalArgumentException e) {
                    continue;
                }
                for (String key : KNOWN_KEYS) {
                    if (test.containsKey(key)) {
                        return p;
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // каталог недоступен для просмотра: работаем на значениях
            // по умолчанию, программу из-за этого не останавливаем
        }
        return null;
    }

    /** Настройки, по которым узнаётся настроечный файл этой программы. */
    private static final String[] KNOWN_KEYS = {
        "teacher.password", "error.mode", "window.fullscreen",
        "test.enabled", "notebook.transfer", "noise.montecarlo", "noise.trials",
        "item.c.anchor", "test.journal"
    };

    /** Значение настройки или заданное значение по умолчанию. */
    public static String get(String key, String fallback) {
        String v = props().getProperty(key);
        return v == null || v.trim().isEmpty() ? fallback : v.trim();
    }

    /** Пароль преподавателя. */
    public static String teacherPassword() {
        return get("teacher.password", DEFAULT_TEACHER_PASSWORD);
    }

    /** Проверка пароля преподавателя. */
    public static boolean checkTeacherPassword(String entered) {
        return entered != null && teacherPassword().equals(entered.trim());
    }

    /**
     * Способ вычисления динамической составляющей ошибки, заданный
     * настройкой error.mode. Допустимые значения: anchor (по умолчанию),
     * averaged, legacy; описание – в {@link ru.vka.upo.core.Processor.Mode}.
     * При незнакомом значении принимается anchor.
     */
    public static ru.vka.upo.core.Processor.Mode errorMode() {
        String v = get("error.mode", "anchor").toLowerCase();
        if (v.startsWith("legacy") || v.startsWith("прежн")) {
            return ru.vka.upo.core.Processor.Mode.LEGACY;
        }
        if (v.startsWith("averaged") || v.startsWith("средн")) {
            return ru.vka.upo.core.Processor.Mode.AVERAGED;
        }
        return ru.vka.upo.core.Processor.Mode.ANCHOR;
    }

    /**
     * Куда ставится момент привязки в пункте в) при автоматическом расчёте
     * в режиме преподавателя, заданный настройкой item.c.anchor.
     *
     * В пункте в) меняется объём выборки N, а момент привязки M0 числом
     * не закреплён. Допустимые значения настройки:
     *
     *   middle – M0 переносится в середину выборки и меняется вместе с N
     *            (значение по умолчанию, как предписывает руководство
     *            к работе: иначе привязка осталась бы на краю
     *            расширяющегося интервала усреднения);
     *
     *   start  – M0 = 1, то есть привязка остаётся в начале интервала
     *            усреднения при любом N (так считалось в прежней
     *            программе; предусмотрено для сопоставления с ней).
     *
     * При незнакомом значении принимается middle. На работу обучающегося
     * настройка не влияет: режим обработки он задаёт вручную сам.
     */
    public static boolean anchorMiddleInItemC() {
        String v = get("item.c.anchor", "middle").trim().toLowerCase();
        return !(v.startsWith("start") || v.startsWith("нач") || v.equals("1"));
    }

    /**
     * Вид главного окна, заданный настройкой window.fullscreen. По умолчанию
     * (true либо настройка не задана) окно разворачивается на весь экран,
     * без рамки, без системных кнопок и без возможности свернуть – выход
     * только по кнопке «Выход». При false окно открывается как обычное:
     * с заголовком, изменяемого размера, сворачиваемое и разворачиваемое,
     * закрываемое обычным способом.
     */
    public static boolean fullScreen() {
        String v = get("window.fullscreen", "true").trim().toLowerCase();
        return !(v.equals("false") || v.equals("0") || v.equals("нет") || v.equals("off"));
    }

    /**
     * Разрешён ли перенос чисел из таблицы результатов в рабочую тетрадь
     * мышью, заданный настройкой notebook.transfer.
     *
     * По умолчанию (false либо настройка не задана) перенос запрещён: числа
     * обучающийся переписывает сам, как переписывал бы их в тетрадь на
     * бумаге – в этом состоит исследовательская часть работы. При true
     * в таблице результатов по правой кнопке мыши появляется меню, из
     * которого строку можно занести в выбранный пункт задания без набора
     * вручную. Это предусмотрено для занятий, где важно успеть пройти все
     * четыре пункта, и для показа работы преподавателем.
     */
    public static boolean notebookTransfer() {
        String v = get("notebook.transfer", "false").trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("да") || v.equals("on");
    }

    /**
     * Проводится ли входной контроль знаний, заданный настройкой
     * test.enabled. По умолчанию (true либо настройка не задана) обучающийся
     * после экрана приветствия проходит контроль, как предусмотрено
     * методикой. При false экран контроля пропускается, и обучающийся сразу
     * переходит к вводу исходных данных; в отчёте это отмечается отдельной
     * строкой вместо оценки. Режима преподавателя это не касается: у него
     * контроля нет в любом случае – это отдельная роль для проверочного
     * расчёта всех пунктов задания сразу, а не способ пропустить контроль
     * обучающемуся.
     */
    public static boolean testEnabled() {
        String v = get("test.enabled", "true").trim().toLowerCase();
        return !(v.equals("false") || v.equals("0") || v.equals("нет") || v.equals("off"));
    }

    /**
     * Ведётся ли журнал обучающихся, заданный настройкой test.journal. По
     * умолчанию (true либо настройка не задана) программа запоминает
     * обучающегося, его оценку за входной контроль и всю его работу в папке
     * «БД» рядом с программой (см. {@link Journal}): после вылета или
     * закрытия программы повторно проходить контроль не нужно, работа
     * продолжается с того же места, а получивший неудовлетворительную
     * оценку к контролю повторно не допускается, пока преподаватель не
     * удалит его запись. При false журнал не ведётся, не читается и на
     * работу не влияет.
     */
    public static boolean journal() {
        String v = get("test.journal", "true").trim().toLowerCase();
        return !(v.equals("false") || v.equals("0") || v.equals("нет") || v.equals("off"));
    }

    /**
     * Заменить ли в режиме {@link ru.vka.upo.core.Processor.Mode#ANCHOR}
     * аналитический расчёт ошибок аппроксимацией истинной кривой прямым
     * статистическим моделированием, заданным настройкой noise.montecarlo.
     *
     * По умолчанию (false либо настройка не задана) действует обычный,
     * аналитический расчёт: динамическая ошибка – невязка аппроксимации
     * истинной, безошибочной траектории, случайная – значение формулы
     * раздела 4.2 руководства через корреляционную матрицу. Это и есть
     * расчёт лабораторной работы; так же было устроено и в прежней
     * программе (см. записки «Откуда берутся ошибки в лабораторной
     * работе» и «Аналитический расчёт и метод Монте-Карло»).
     *
     * При true для каждой строки таблицы вместо этого проводится {@link
     * #monteCarloTrials()} независимых опытов: измерения набираются
     * датчиком случайных чисел с заданным СКО (как это делает кнопка
     * «Другая реализация шума»), по ним методом наименьших квадратов
     * строится оценка в момент привязки, и по накопленным опытам берутся
     * среднее (динамическая ошибка) и среднее квадратическое отклонение
     * (случайная ошибка). Численно, как показано в упомянутой записке,
     * результат при достаточном числе опытов совпадает с аналитическим
     * в пределах статистической погрешности моделирования – ни то, ни
     * другое не более «правильно», расчёт лишь становится медленнее и
     * зависимым от числа опытов. Настройка сделана ради наглядности
     * и проверки, а не как замена основного способа расчёта.
     *
     * На режимы {@link ru.vka.upo.core.Processor.Mode#AVERAGED} и {@link
     * ru.vka.upo.core.Processor.Mode#LEGACY} эта настройка не действует:
     * они воспроизводят прежнюю программу и по построению аналитические.
     */
    public static boolean noiseMonteCarlo() {
        String v = get("noise.montecarlo", "false").trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("да") || v.equals("on");
    }

    /**
     * Число независимых опытов на строку таблицы при включённой настройке
     * noise.montecarlo. По умолчанию 20000 – это занимает на современной
     * машине долю секунды на строку и даёт погрешность оценки случайной
     * ошибки порядка 0,5 % (относительная погрешность оценки среднего
     * квадратического отклонения по N опытам составляет примерно
     * 1/√(2N)). При нечисловом или неположительном значении настройки
     * принимается значение по умолчанию.
     */
    public static int monteCarloTrials() {
        String v = get("noise.trials", "20000").trim();
        try {
            int n = Integer.parseInt(v);
            return n > 0 ? n : 20000;
        } catch (NumberFormatException e) {
            return 20000;
        }
    }
}
