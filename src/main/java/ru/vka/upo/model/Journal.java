package ru.vka.upo.model;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Журнал обучающихся: по одному файлу на обучающегося в папке «БД» рядом
 * с программой (запасное имя латиницей – db).
 *
 * Зачем он нужен. Если программа вылетела или её закрыли, обучающийся,
 * уже получивший на входном контроле положительную оценку, при повторном
 * входе контроль заново не проходит и продолжает работу с того места,
 * где остановился. Получивший неудовлетворительную оценку к контролю
 * повторно не допускается, пока преподаватель не удалит его запись.
 *
 * Имя файла – шестнадцатеричный SHA-256 от ключа обучающегося (фамилия
 * с инициалами и группа, см. {@link #key}) с расширением .properties:
 * фамилии в именах файлов нет. Содержимое – читаемый текст «ключ =
 * значение» в UTF-8. Запись атомарная: во временный файл в той же папке,
 * затем перемещение с заменой.
 *
 * Оценка хранится зашифрованной: блок «оценка, ключ обучающегося, время
 * создания» шифруется AES ключом, полученным из пароля преподавателя,
 * и снабжается проверочным кодом HMAC-SHA256. Подставить оценку, переписать
 * её на другого человека или скопировать файл под именем другого
 * обучающегося без пароля преподавателя нельзя. При смене пароля прежние
 * записи не расшифровываются и удаляются.
 *
 * Журнал никогда не останавливает программу: запись, которая не читается,
 * не разбирается или не проходит проверку, удаляется молча – считается,
 * что её нет и не было. Ошибки записи возвращаются признаком неуспеха,
 * исключений наружу не выходит. Удаляются только файлы записей журнала:
 * саму папку и посторонние файлы в ней журнал не трогает.
 */
public final class Journal {

    /** Имя папки журнала. */
    public static final String FOLDER_NAME = "БД";

    /** Запасное имя папки на случай, если система не умеет русские имена. */
    public static final String FOLDER_NAME_ASCII = "db";

    /** Записи, не менявшиеся дольше этого срока, удаляются при запуске, дни. */
    public static final int MAX_AGE_DAYS = 30;

    /** Постоянная «соль» программы при получении ключа из пароля. */
    private static final String SALT = "upo-lab1: журнал обучающихся, ВКА им. А.Ф. Можайского";

    /** Имя файла записи: 64 шестнадцатеричные цифры и расширение. */
    private static final String FILE_PATTERN = "[0-9a-f]{64}\\.properties";

    private static final long DAY = 24L * 60 * 60 * 1000;

    private final Path folder;
    private final byte[] cipherKey;
    private final byte[] macKey;

    /**
     * @param folder   папка журнала (создаётся при первой записи)
     * @param password пароль преподавателя, из которого получается ключ
     */
    public Journal(Path folder, String password) {
        this.folder = folder;
        byte[] base = sha256((SALT + "\n" + (password == null ? "" : password))
                .getBytes(StandardCharsets.UTF_8));
        this.cipherKey = Arrays.copyOf(sha256(concat(base, "шифр")), 16);
        this.macKey = sha256(concat(base, "проверка"));
    }

    /**
     * Журнал программы: папка рядом с программой, ключ из пароля
     * преподавателя; null, если журнал отключён настройкой test.journal.
     */
    public static Journal standard() {
        if (!Settings.journal()) {
            return null;
        }
        return new Journal(defaultFolder(), Settings.teacherPassword());
    }

    /**
     * Папка журнала рядом с программой.
     *
     * Как и у настроечного файла (см. {@link Settings}), русское имя может
     * оказаться непредставимым в кодировке имён файлов системы. Тогда
     * ищется уже существующая папка с записями журнала просмотром текущего
     * каталога, а если её нет – берётся запасное имя латиницей.
     */
    public static Path defaultFolder() {
        Path here = Paths.get("").toAbsolutePath();
        Path ascii = here.resolve(FOLDER_NAME_ASCII);
        if (Files.isDirectory(ascii)) {
            return ascii;
        }
        try {
            return here.resolve(FOLDER_NAME);
        } catch (InvalidPathException e) {
            // имя непредставимо: ищем папку, где уже лежат записи журнала
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(here)) {
            for (Path d : dirs) {
                if (Files.isDirectory(d) && hasRecords(d)) {
                    return d;
                }
            }
        } catch (IOException | RuntimeException e) {
            // каталог недоступен для просмотра
        }
        return ascii;
    }

    private static boolean hasRecords(Path dir) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path f : files) {
                if (f.getFileName().toString().matches(FILE_PATTERN)) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return false;
    }

    public Path getFolder() {
        return folder;
    }

    // ------------------------------------------------------------ ключ

    /**
     * Ключ обучающегося: фамилия с инициалами и группа без учёта регистра,
     * без пробелов по краям, с одним пробелом внутри вместо нескольких,
     * «ё» считается «е». Номер по списку в ключ не входит.
     */
    public static String key(String name, String group) {
        return normalize(name) + "|" + normalize(group);
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFC)
                .toLowerCase(new Locale("ru"))
                .replace('ё', 'е')
                .replace(' ', ' ')
                .trim();
        return n.replaceAll("\\s+", " ");
    }

    /** Файл записи обучающегося с заданным ключом. */
    public Path fileFor(String key) {
        return folder.resolve(hex(sha256(key.getBytes(StandardCharsets.UTF_8))) + ".properties");
    }

    // ------------------------------------------------------------ чтение

    /**
     * Запись обучающегося или null, если её нет. Недействительная запись
     * удаляется и считается отсутствующей.
     */
    public JournalRecord load(String name, String group) {
        try {
            return read(fileFor(key(name, group)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Читает и проверяет файл записи; при любой неудаче удаляет его и возвращает null. */
    private JournalRecord read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        JournalRecord r = null;
        try {
            byte[] bytes;
            try (InputStream in = Files.newInputStream(file)) {
                bytes = readAll(in, 4 * 1024 * 1024);
            }
            Properties p = new Properties();
            try (Reader reader = new StringReader(decode(bytes))) {
                p.load(reader);
            }
            r = JournalRecord.read(p);
            String key = r.key();
            if (!fileFor(key).getFileName().equals(file.getFileName())) {
                r = null;   // файл скопирован под именем другого обучающегося
            } else if (!unseal(p, r, key)) {
                r = null;   // оценка подделана или ключ шифрования иной
            } else if (!valid(r)) {
                r = null;
            }
        } catch (IOException | RuntimeException | GeneralSecurityException e) {
            r = null;
        }
        if (r == null) {
            deleteQuietly(file);
        }
        return r;
    }

    private static boolean valid(JournalRecord r) {
        InputData d = r.getData();
        return r.getCreated() > 0 && r.getModified() >= r.getCreated() - DAY
                && r.getListNumber() >= 0 && r.getVariantNumber() >= 0
                // пределы – те же, что у полей режима обработки на экране ввода
                && d.getSampleSize() >= 2 && d.getSampleSize() <= 999
                && d.getDegree() >= 0 && d.getDegree() <= 12
                && d.getAnchor() >= 1 && d.getAnchor() <= 999;
    }

    /** Строгое декодирование UTF-8: испорченный текст – недействительная запись. */
    private static String decode(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new IOException(e);
        }
    }

    private static byte[] readAll(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > limit) {
                throw new IOException("слишком большой файл");
            }
        }
        return out.toByteArray();
    }

    /**
     * Все действительные записи журнала. Недействительные при этом
     * удаляются. Если папки нет или она недоступна – пустой список.
     */
    public List<JournalRecord> list() {
        List<JournalRecord> out = new ArrayList<JournalRecord>();
        for (Path f : files()) {
            JournalRecord r = read(f);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    /** Файлы записей журнала в папке (только они, посторонние не берутся). */
    private List<Path> files() {
        List<Path> out = new ArrayList<Path>();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder)) {
            for (Path f : ds) {
                if (f.getFileName().toString().matches(FILE_PATTERN) && Files.isRegularFile(f)) {
                    out.add(f);
                }
            }
        } catch (IOException | RuntimeException e) {
            // папка недоступна: записей как бы нет
        }
        return out;
    }

    /**
     * Удаляет недействительные записи и записи, не менявшиеся дольше
     * {@link #MAX_AGE_DAYS} дней. Вызывается при запуске программы.
     *
     * @return число удалённых файлов
     */
    public int purge() {
        return purge(System.currentTimeMillis());
    }

    /** То же для заданного текущего времени (для проверочных программ). */
    public int purge(long now) {
        int removed = 0;
        for (Path f : files()) {
            JournalRecord r = read(f);
            if (r == null) {
                removed++;
            } else if (now - r.getModified() > MAX_AGE_DAYS * DAY) {
                if (deleteQuietly(f)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    // ------------------------------------------------------------ запись

    /**
     * Записывает запись в журнал (создаёт папку, если её нет).
     *
     * @return true, если запись удалась
     */
    public boolean save(JournalRecord r) {
        Path tmp = null;
        try {
            Files.createDirectories(folder);
            String key = r.key();
            Properties p = new Properties();
            r.write(p);
            seal(p, r, key);
            String text = format(p, r);
            tmp = Files.createTempFile(folder, ".journal-", ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp);
                    Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                w.write(text);
            }
            Path target = fileFor(key);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | UnsupportedOperationException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
            return true;
        } catch (IOException | RuntimeException | GeneralSecurityException e) {
            return false;
        } finally {
            if (tmp != null) {
                deleteQuietly(tmp);
            }
        }
    }

    /**
     * Текст записи: строки по алфавиту ключей, в UTF-8 без экранирования
     * русских букв – файл можно прочитать глазами.
     */
    private static String format(Properties p, JournalRecord r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Журнал обучающихся, лабораторная работа «Предварительная обработка»\n");
        sb.append("# ").append(r.getName()).append(", группа ").append(r.getGroup())
          .append("; изменено ").append(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
                  .format(new Date(r.getModified()))).append('\n');
        sb.append("# Файл создан программой, вручную не править: оценка защищена\n");
        Map<String, String> sorted = new TreeMap<String, String>();
        for (String k : p.stringPropertyNames()) {
            sorted.put(k, p.getProperty(k));
        }
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            sb.append(escape(e.getKey(), true)).append(" = ")
              .append(escape(e.getValue(), false)).append('\n');
        }
        return sb.toString();
    }

    /** Экранирование по правилам формата properties, кроме букв не латиницы. */
    private static String escape(String s, boolean key) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\f': sb.append("\\f"); break;
                case '=': case ':': case '#': case '!':
                    sb.append('\\').append(c);
                    break;
                case ' ':
                    sb.append(key || i == 0 ? "\\ " : " ");
                    break;
                default:
                    if (c < 0x20 || c == 0x7F) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /** Удаляет запись обучающегося. */
    public boolean delete(JournalRecord r) {
        return deleteQuietly(fileFor(r.key()));
    }

    /**
     * Удаляет все записи журнала (только файлы записей).
     *
     * @return число удалённых файлов
     */
    public int deleteAll() {
        int n = 0;
        for (Path f : files()) {
            if (deleteQuietly(f)) {
                n++;
            }
        }
        return n;
    }

    private static boolean deleteQuietly(Path f) {
        try {
            return Files.deleteIfExists(f);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------- защита оценки

    /** Шифрует блок «оценка, ключ, время создания» и добавляет проверочный код. */
    private void seal(Properties p, JournalRecord r, String key) throws GeneralSecurityException {
        String block = (r.getScore() == null ? "-" : String.valueOf(r.getScore()))
                + "\n" + key + "\n" + r.getCreated();
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cipherKey, "AES"), new IvParameterSpec(iv));
        byte[] enc = c.doFinal(block.getBytes(StandardCharsets.UTF_8));
        byte[] data = concat(iv, enc);
        p.setProperty("score.data", Base64.getEncoder().encodeToString(data));
        p.setProperty("score.check", Base64.getEncoder().encodeToString(mac(data, key)));
    }

    /**
     * Проверяет проверочный код, расшифровывает блок оценки и сверяет ключ
     * и время создания с открытыми полями записи; при успехе заносит оценку.
     */
    private boolean unseal(Properties p, JournalRecord r, String key)
            throws GeneralSecurityException {
        String dataText = p.getProperty("score.data");
        String checkText = p.getProperty("score.check");
        if (dataText == null || checkText == null) {
            return false;
        }
        byte[] data = Base64.getDecoder().decode(dataText.trim());
        byte[] check = Base64.getDecoder().decode(checkText.trim());
        if (data.length < 32 || !MessageDigest.isEqual(check, mac(data, key))) {
            return false;
        }
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cipherKey, "AES"),
                new IvParameterSpec(Arrays.copyOf(data, 16)));
        String block = new String(c.doFinal(data, 16, data.length - 16), StandardCharsets.UTF_8);
        String[] parts = block.split("\n", -1);
        if (parts.length != 3 || !parts[1].equals(key)
                || !parts[2].equals(String.valueOf(r.getCreated()))) {
            return false;
        }
        if ("-".equals(parts[0])) {
            r.setScore(null);
        } else {
            int score = Integer.parseInt(parts[0]);
            if (score < 0 || score > QuestionBank.QUESTIONS_PER_SESSION) {
                return false;
            }
            r.setScore(score);
        }
        return true;
    }

    private byte[] mac(byte[] data, String key) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(macKey, "HmacSHA256"));
        m.update(data);
        m.update(key.getBytes(StandardCharsets.UTF_8));
        return m.doFinal();
    }

    // ------------------------------------------------------------ прочее

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] concat(byte[] a, String b) {
        return concat(a, b.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x & 0xFF));
        }
        return sb.toString();
    }
}
