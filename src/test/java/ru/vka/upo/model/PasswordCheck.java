package ru.vka.upo.model;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Проверка пароля преподавателя ({@link TeacherPassword}, {@link Settings}).
 *
 * Проверяется: хэш совпадает только со своим паролем, у двух хэшей одного
 * пароля разная «соль», испорченный хэш не принимается; открытый пароль
 * в настроечном файле заменяется хэшем, прочие строки и комментарии файла
 * сохраняются; после смены пароля действует новый, прежний – нет, ключ
 * журнала меняется; без пароля в файле вход невозможен и журнал не ведётся;
 * при отсутствии файла он создаётся при смене пароля.
 *
 * Работа с настроечным файлом проверяется в отдельном процессе во
 * временной папке – файл проекта не затрагивается.
 *
 * <pre>
 * java -cp target/classes:target/check ru.vka.upo.model.PasswordCheck
 * </pre>
 */
public final class PasswordCheck {

    private static int failures;

    private PasswordCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            child(args[0]);
            System.exit(failures == 0 ? 0 : 1);
        }
        String h1 = TeacherPassword.hash("кафедра 33");
        String h2 = TeacherPassword.hash("кафедра 33");
        check("хэш принимает свой пароль", TeacherPassword.verify("кафедра 33", h1));
        check("хэш не принимает чужой пароль", !TeacherPassword.verify("кафедра 34", h1));
        check("у двух хэшей одного пароля разная соль", !h1.equals(h2));
        check("испорченный хэш не принимается", !TeacherPassword.verify("кафедра 33",
                h1.substring(0, h1.length() - 4) + "AAAA") && !TeacherPassword.verify("x", "мусор"));
        check("пароля в хэше открытым текстом нет", !h1.contains("кафедра"));

        run("migrate");
        run("absent");
        run("create");

        System.out.println();
        System.out.println(failures == 0 ? "Все проверки пройдены." : "Ошибок: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Запускает эту же программу в отдельном процессе во временной папке. */
    private static void run(String scenario) throws Exception {
        Path dir = Files.createTempDirectory("password-check");
        if (!"create".equals(scenario)) {
            String text = "# Настройки\r\n# пароль\r\n"
                    + ("migrate".equals(scenario) ? "teacher.password = kaf33\r\n" : "")
                    + "\r\n# контроль\r\ntest.enabled = true\r\n";
            Files.write(dir.resolve("settings.properties"), text.getBytes(StandardCharsets.UTF_8));
        }
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder pb = new ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-cp",
                System.getProperty("java.class.path"), PasswordCheck.class.getName(), scenario);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringWriter out = new StringWriter();
        try (Reader in = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        int code = p.waitFor();
        for (String line : out.toString().split("\\R")) {
            if (line.startsWith("  ")) {
                System.out.println(line);
            }
        }
        if (code != 0) {
            failures++;
        }
        for (File f : dir.toFile().listFiles()) {
            f.delete();
        }
        dir.toFile().delete();
    }

    /** Дочерний процесс: сценарии с настроечным файлом в текущей папке. */
    private static void child(String scenario) throws Exception {
        Path file = new File("settings.properties").toPath().toAbsolutePath();
        if ("migrate".equals(scenario)) {
            check("открытый пароль действует до замены", Settings.checkTeacherPassword("kaf33"));
            String before = Settings.teacherSecret();
            check("открытый пароль заменён хэшем", TeacherPassword.migrate());
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            check("в файле нет открытого пароля", !text.contains("kaf33")
                    && !text.contains("teacher.password ="));
            check("в файле есть хэш", text.contains(TeacherPassword.HASH_KEY + " = pbkdf2-sha256$"));
            check("комментарии, прочие строки и переводы строк сохранены",
                    text.startsWith("# Настройки\r\n# пароль\r\nteacher.password.hash = ")
                    && text.endsWith("\r\n\r\n# контроль\r\ntest.enabled = true\r\n"));
            check("после замены тот же пароль подходит", Settings.checkTeacherPassword("kaf33"));
            check("неверный пароль не подходит", !Settings.checkTeacherPassword("kaf34"));
            check("ключ журнала сменился вместе с записью пароля",
                    !before.equals(Settings.teacherSecret()));
            check("повторная замена ничего не делает", !TeacherPassword.migrate());
            String secret = Settings.teacherSecret();
            check("смена пароля записана", TeacherPassword.change("новый пароль"));
            check("новый пароль подходит", Settings.checkTeacherPassword("новый пароль"));
            check("прежний пароль не подходит", !Settings.checkTeacherPassword("kaf33"));
            check("ключ журнала сменился", !secret.equals(Settings.teacherSecret()));
            String after = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            check("строка хэша одна", after.split(TeacherPassword.HASH_KEY, -1).length == 2);
            check("пустой пароль не записывается", !TeacherPassword.change("  "));
        } else if ("absent".equals(scenario)) {
            check("без пароля в файле пароль не задан", !Settings.isTeacherPasswordSet());
            check("без пароля вход невозможен (встроенного пароля нет)",
                    !Settings.checkTeacherPassword("kaf33") && !Settings.checkTeacherPassword(""));
            check("без пароля журнал не ведётся", Journal.standard() == null);
        } else {
            check("файла настроек нет", Settings.path() == null);
            check("при смене пароля файл создаётся", TeacherPassword.change("abc")
                    && Settings.path() != null && Settings.checkTeacherPassword("abc"));
        }
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  так  " : "  НЕТ  ") + what);
        if (!ok) {
            failures++;
        }
    }
}
