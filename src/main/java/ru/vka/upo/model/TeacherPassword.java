package ru.vka.upo.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Пароль преподавателя: хранится в настроечном файле не открытым текстом,
 * а хэшем (строка teacher.password.hash), из которого пароль восстановить
 * нельзя. Хэш – PBKDF2 с HMAC-SHA256 и случайной «солью».
 *
 * Задать или сбросить забытый пароль можно правкой настроечного файла:
 * удалить строку teacher.password.hash и вписать teacher.password = новый
 * пароль. При запуске программы ({@link #migrate()}) открытый пароль
 * заменяется хэшем, открытая строка стирается. Сменить пароль можно и из
 * режима преподавателя ({@link #change(String)}).
 *
 * Встроенного пароля нет: если в файле не задано ни то, ни другое, вход
 * преподавателя невозможен.
 *
 * Хэш служит и ключом журнала обучающихся (см. {@link Journal}): при смене
 * пароля записи журнала становятся недействительными.
 */
public final class TeacherPassword {

    /** Ключ строки с хэшем пароля. */
    public static final String HASH_KEY = "teacher.password.hash";
    /** Ключ строки с открытым паролем (только для ввода, до первого запуска). */
    public static final String PLAIN_KEY = "teacher.password";

    private static final String SCHEME = "pbkdf2-sha256";
    private static final int ITERATIONS = 120000;
    /** Строка пароля или его хэша в настроечном файле (не комментарий). */
    private static final Pattern LINE = Pattern.compile(
            "^\\s*teacher\\.password(\\.hash)?\\s*[=:\\s].*$|^\\s*teacher\\.password(\\.hash)?\\s*$");

    private TeacherPassword() {
    }

    /** Хэш пароля со случайной «солью»: «pbkdf2-sha256$число$соль$хэш». */
    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        Base64.Encoder b64 = Base64.getEncoder();
        return SCHEME + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$"
                + b64.encodeToString(derive(password, salt, ITERATIONS));
    }

    /** Совпадает ли пароль с хэшем; при испорченном хэше – нет. */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        try {
            String[] p = stored.trim().split("\\$");
            if (p.length != 4 || !SCHEME.equals(p[0])) {
                return false;
            }
            int iterations = Integer.parseInt(p[1]);
            if (iterations < 1 || iterations > 10000000) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Заменяет открытый пароль в настроечном файле его хэшем. Вызывается
     * при запуске программы. Если открытого пароля нет или файл записать
     * нельзя, ничего не меняется (открытый пароль тогда продолжает
     * действовать как есть).
     *
     * @return true, если пароль заменён хэшем
     */
    public static boolean migrate() {
        String plain = Settings.get(PLAIN_KEY, null);
        if (plain == null) {
            return false;
        }
        return write(hash(plain));
    }

    /**
     * Задаёт новый пароль: в настроечный файл записывается его хэш.
     *
     * @return true, если запись удалась
     */
    public static boolean change(String password) {
        return password != null && !password.trim().isEmpty() && write(hash(password.trim()));
    }

    /**
     * Записывает хэш в настроечный файл вместо строк пароля и прежнего
     * хэша; комментарии и прочие настройки сохраняются как есть. Если
     * файла нет, он создаётся рядом с программой.
     */
    private static boolean write(String hashValue) {
        Path file = Settings.path();
        Path tmp = null;
        try {
            List<String> out = new ArrayList<String>();
            String eol = "\n";
            if (file != null) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (text.contains("\r\n")) {
                    eol = "\r\n";
                }
                boolean placed = false;
                for (String line : text.split("\r?\n", -1)) {
                    if (LINE.matcher(line).matches()) {
                        if (!placed) {
                            out.add(HASH_KEY + " = " + hashValue);
                            placed = true;
                        }
                        continue;
                    }
                    out.add(line);
                }
                // split(-1) даёт пустой хвост после последнего перевода строки
                if (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
                    out.remove(out.size() - 1);
                }
                if (!placed) {
                    out.add("");
                    out.add("# Пароль преподавателя (хэш).");
                    out.add(HASH_KEY + " = " + hashValue);
                }
            } else {
                file = newFile();
                out.add("# Настройки программы лабораторной работы «Предварительная обработка».");
                out.add("# Пароль преподавателя (хэш).");
                out.add(HASH_KEY + " = " + hashValue);
            }
            StringBuilder sb = new StringBuilder();
            for (String line : out) {
                sb.append(line).append(eol);
            }
            Path dir = file.toAbsolutePath().getParent();
            tmp = Files.createTempFile(dir, ".settings-", ".tmp");
            Files.write(tmp, sb.toString().getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | UnsupportedOperationException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
            Settings.reload();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignore) {
                    // временный файл не удалён – не страшно
                }
            }
        }
    }

    /** Новый настроечный файл рядом с программой (русское имя или запасное). */
    private static Path newFile() {
        try {
            return Paths.get(Settings.FILE_NAME).toAbsolutePath();
        } catch (InvalidPathException e) {
            return Paths.get(Settings.FILE_NAME_ASCII).toAbsolutePath();
        }
    }
}
