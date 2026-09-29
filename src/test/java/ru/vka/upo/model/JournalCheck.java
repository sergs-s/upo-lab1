package ru.vka.upo.model;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Проверка журнала обучающихся ({@link Journal}, {@link JournalRecord}) на
 * временной папке.
 *
 * Проверяется: запись и чтение совпадают (данные, тетрадь, выводы,
 * оценка); ключ обучающегося не зависит от регистра, лишних пробелов
 * и «ё»; файл с изменённой оценкой, с подставленной чужой фамилией,
 * скопированный под именем другого обучающегося, обрезанный, пустой,
 * мусорный – не читается и удаляется; при другом пароле преподавателя
 * запись удаляется; запись старше 30 дней удаляется при чистке, моложе –
 * остаётся; посторонние файлы в папке журнала не трогаются; при
 * test.journal = false журнал не создаётся (проверяется в отдельном
 * процессе, запущенном во временной папке с настроечным файлом).
 *
 * Запуск (не входит в поставку, только для отработки):
 * <pre>
 * java -cp target/classes:target/check ru.vka.upo.model.JournalCheck
 * </pre>
 */
public final class JournalCheck {

    private static final String PASSWORD = "kaf33";
    private static final long DAY = 24L * 60 * 60 * 1000;
    private static int failures;

    private JournalCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--disabled".equals(args[0])) {
            disabledChild();
            return;
        }
        Path dir = Files.createTempDirectory("journal-check");
        Path folder = dir.resolve("БД");
        Journal j = new Journal(folder, PASSWORD);

        // ключ обучающегося
        check("ключ без учёта регистра, пробелов и «ё»",
                Journal.key("  Семёнов   И.И. ", " 321а ").equals(Journal.key("семенов и.и.", "321А")));
        check("разные группы – разные ключи",
                !Journal.key("Иванов И.И.", "321").equals(Journal.key("Иванов И.И.", "322")));

        // запись и чтение
        JournalRecord a = sample("Семёнов И.И.", "321", 4);
        check("запись удалась (папка создана)", j.save(a) && Files.isDirectory(folder));
        Path fileA = j.fileFor(a.key());
        check("имя файла – SHA-256 ключа, фамилии в имени нет",
                fileA.getFileName().toString().matches("[0-9a-f]{64}\\.properties"));
        String text = new String(Files.readAllBytes(fileA), StandardCharsets.UTF_8);
        check("файл читаемый (UTF-8, русские буквы как есть)", text.contains("Семёнов И.И."));
        check("оценки открытым текстом в файле нет", !text.contains("score = 4")
                && !text.contains("score=4"));
        JournalRecord back = j.load("семенов  и.и.", "321");
        check("запись читается по ключу", back != null);
        check("прочитанное совпадает с записанным", back != null && same(a, back));
        check("оценка восстановлена", back != null && Integer.valueOf(4).equals(back.getScore()));
        check("пункты и выводы подсчитываются", back != null
                && back.readyCount() == 2 && back.conclusionCount() == 1);

        // запись без оценки (контроль отключён)
        JournalRecord n = sample("Петров П.П.", "321", null);
        j.save(n);
        JournalRecord nb = j.load("Петров П.П.", "321");
        check("запись без оценки читается, оценки нет", nb != null && nb.getScore() == null);

        // посторонний файл в папке журнала
        Path foreign = folder.resolve("прочти меня.txt");
        Files.write(foreign, "посторонний файл".getBytes(StandardCharsets.UTF_8));

        // изменённая оценка: подмена зашифрованного блока
        tamper(j, "Сидоров С.С.", "321", 2, "score.data", null);
        // подставленная чужая фамилия
        tamper(j, "Кузнецов К.К.", "321", 5, "student.name", "Смирнов С.С.");
        // испорченный проверочный код
        tamper(j, "Попов П.П.", "321", 3, "score.check", null);
        // изменённое время создания
        tamper(j, "Васильев В.В.", "321", 4, "created", "12345");

        // файл, скопированный под именем другого обучающегося
        JournalRecord failed = sample("Орлов О.О.", "322", 2);
        JournalRecord good = sample("Лебедев Л.Л.", "322", 5);
        j.save(failed);
        j.save(good);
        Files.copy(j.fileFor(good.key()), j.fileFor(failed.key()),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        check("копия чужой записи не читается", j.load("Орлов О.О.", "322") == null);
        check("копия чужой записи удалена", !Files.exists(j.fileFor(failed.key())));
        check("подлинная запись цела", j.load("Лебедев Л.Л.", "322") != null);

        // обрезанный, пустой, мусорный файлы
        damaged(j, "Морозов М.М.", "обрезанный", bytes -> java.util.Arrays.copyOf(bytes, bytes.length / 2));
        damaged(j, "Новиков Н.Н.", "пустой", bytes -> new byte[0]);
        damaged(j, "Фёдоров Ф.Ф.", "мусорный", bytes -> {
            byte[] g = new byte[3000];
            new java.util.Random(1).nextBytes(g);
            return g;
        });
        damaged(j, "Волков В.В.", "с чужим форматом", bytes ->
                "format = 99\nstudent.name = Волков В.В.\n".getBytes(StandardCharsets.UTF_8));

        // другой пароль преподавателя
        JournalRecord p = sample("Зайцев З.З.", "323", 4);
        j.save(p);
        Journal other = new Journal(folder, "другой пароль");
        check("при другом пароле запись не читается", other.load("Зайцев З.З.", "323") == null);
        check("при другом пароле запись удалена", !Files.exists(j.fileFor(p.key())));

        // чистка по сроку
        long now = System.currentTimeMillis();
        JournalRecord old = sample("Старый С.С.", "324", 4);
        old.setCreated(now - 40 * DAY);
        old.setModified(now - 31 * DAY);
        JournalRecord fresh = sample("Свежий С.С.", "324", 4);
        fresh.setCreated(now - 40 * DAY);
        fresh.setModified(now - 29 * DAY);
        j.save(old);
        j.save(fresh);
        int removed = j.purge(now);
        check("запись старше 30 дней удалена", !Files.exists(j.fileFor(old.key())));
        check("запись моложе 30 дней осталась", Files.exists(j.fileFor(fresh.key())));
        check("чистка сообщила об удалении", removed >= 1);

        // список и удаление всех записей
        List<JournalRecord> all = j.list();
        check("в списке только действительные записи", all.size() == 4);
        check("удаление записи", j.delete(fresh) && j.load("Свежий С.С.", "324") == null);
        int deleted = j.deleteAll();
        check("очистка журнала удалила все записи", deleted == 3 && j.list().isEmpty());
        check("посторонний файл не тронут", Files.exists(foreign));
        check("папка журнала не удалена", Files.isDirectory(folder));

        // запись в недоступную папку не бросает исключений
        Path blocker = dir.resolve("не папка");
        Files.write(blocker, new byte[] {1});
        Journal broken = new Journal(blocker.resolve("БД"), PASSWORD);
        boolean ok;
        try {
            ok = !broken.save(sample("Ошибкин О.О.", "1", 4)) && broken.list().isEmpty()
                    && broken.load("Ошибкин О.О.", "1") == null && broken.purge() == 0;
        } catch (RuntimeException e) {
            ok = false;
        }
        check("недоступная папка: неуспех без исключений", ok);

        // test.journal = false – в отдельном процессе с настроечным файлом
        checkDisabled();

        deleteTree(dir.toFile());
        System.out.println();
        System.out.println(failures == 0 ? "Все проверки пройдены." : "Ошибок: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Запись-образец: исходные данные, два заполненных пункта, вывод. */
    static JournalRecord sample(String name, String group, Integer score) {
        JournalRecord r = new JournalRecord();
        r.setName(name);
        r.setGroup(group);
        r.setListNumber(7);
        r.setVariantNumber(7);
        long now = System.currentTimeMillis();
        r.setCreated(now - 1000);
        r.setModified(now);
        r.setScore(score);
        r.setScreen("notebook");
        InputData d = VariantTable.byListNumber(7).toInputData();
        d.setMeasured(InputData.Measured.BOTH);
        d.setMeasurer(InputData.Measurer.AIRBORNE);
        d.setDegree(3);
        d.setSampleSize(25);
        d.setStep(0.05);
        d.setAnchor(13);
        r.setData(d);
        Notebook nb = r.getNotebook();
        nb.setChosenDegree(3);
        for (Notebook.Item item : new Notebook.Item[] {Notebook.Item.A, Notebook.Item.B}) {
            Notebook.Page page = nb.page(item);
            page.setSourceRow(2);
            for (Notebook.Line l : page.getLines()) {
                l.set(Notebook.Quantity.RANGE, 1.5 + l.getParameter(), 2.25, 3.125e-7);
                l.set(Notebook.Quantity.SPEED, 0.001 * l.getParameter(), 0.0125, 1.0 / 3);
                l.setMode(item.apply(d, l.getParameter(), 3));
                l.setSuspicious(l.getParameter() == 1);
            }
        }
        nb.page(Notebook.Item.A).setConclusion("С ростом m случайная ошибка растёт,\n"
                + "динамическая = убывает: # ! \\ «кавычки»");
        nb.page(Notebook.Item.C).getLines().add(new Notebook.Line(17));
        return r;
    }

    /** Совпадение двух записей по всем полям (через их запись в строки). */
    static boolean same(JournalRecord a, JournalRecord b) {
        Properties pa = new Properties();
        Properties pb = new Properties();
        a.write(pa);
        b.write(pb);
        boolean eq = new TreeMap<Object, Object>(pa).equals(new TreeMap<Object, Object>(pb));
        if (!eq) {
            System.out.println("    записано: " + new TreeMap<Object, Object>(pa));
            System.out.println("    прочитано: " + new TreeMap<Object, Object>(pb));
        }
        return eq && (a.getScore() == null ? b.getScore() == null : a.getScore().equals(b.getScore()));
    }

    /** Меняет одну строку файла записи и проверяет, что запись удаляется. */
    private static void tamper(Journal j, String name, String group, int score,
            String key, String value) throws IOException {
        JournalRecord r = sample(name, group, score);
        j.save(r);
        Path f = j.fileFor(r.key());
        List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
        List<String> out = new ArrayList<String>();
        for (String line : lines) {
            if (line.startsWith(key + " = ")) {
                String v = line.substring(key.length() + 3);
                if (value == null) {
                    // меняем один символ зашифрованного блока
                    char c = v.charAt(10);
                    v = v.substring(0, 10) + (c == 'A' ? 'B' : 'A') + v.substring(11);
                } else {
                    v = value;
                }
                line = key + " = " + v;
            }
            out.add(line);
        }
        Files.write(f, out, StandardCharsets.UTF_8);
        check("изменено поле " + key + ": запись не читается", j.load(name, group) == null);
        check("изменено поле " + key + ": файл удалён", !Files.exists(f));
    }

    private interface Damage {
        byte[] apply(byte[] bytes);
    }

    private static void damaged(Journal j, String name, String what, Damage damage)
            throws IOException {
        JournalRecord r = sample(name, "325", 4);
        j.save(r);
        Path f = j.fileFor(r.key());
        Files.write(f, damage.apply(Files.readAllBytes(f)));
        check(what + " файл не читается и удалён",
                j.load(name, "325") == null && !Files.exists(f));
    }

    /** Запускает эту же программу в отдельном процессе во временной папке. */
    private static void checkDisabled() throws Exception {
        Path dir = Files.createTempDirectory("journal-off");
        Files.write(dir.resolve("settings.properties"),
                "test.journal = false\n".getBytes(StandardCharsets.UTF_8));
        // запись, которая при включённом журнале была бы удалена как мусор
        Files.createDirectories(dir.resolve("db"));
        Path junk = dir.resolve("db").resolve(repeat('a', 64) + ".properties");
        Files.write(junk, "мусор".getBytes(StandardCharsets.UTF_8));
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder pb = new ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-cp",
                System.getProperty("java.class.path"), JournalCheck.class.getName(), "--disabled");
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringWriter sw = new StringWriter();
        try (java.io.Reader in = new java.io.InputStreamReader(proc.getInputStream(),
                StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int k;
            while ((k = in.read(buf)) > 0) {
                sw.write(buf, 0, k);
            }
        }
        int code = proc.waitFor();
        // последняя строка вывода – ответ дочернего процесса (выше могут быть
        // сообщения самой Java о параметрах запуска)
        String[] out = sw.toString().trim().split("\\R");
        check("test.journal = false: журнал не создаётся (" + out[out.length - 1] + ")",
                code == 0);
        check("test.journal = false: файлы в папке журнала не тронуты", Files.exists(junk));
        deleteTree(dir.toFile());
    }

    /** Дочерний процесс: при test.journal = false журнала нет. */
    private static void disabledChild() {
        boolean off = !Settings.journal() && Journal.standard() == null;
        System.out.print(off ? "журнал отключён" : "журнал включён");
        System.exit(off ? 0 : 1);
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteTree(k);
            }
        }
        f.delete();
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  так  " : "  НЕТ  ") + what);
        if (!ok) {
            failures++;
        }
    }
}
