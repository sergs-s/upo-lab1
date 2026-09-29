package ru.vka.upo.model;

import java.util.List;
import java.util.Properties;

/**
 * Запись журнала обучающихся: кто работает, оценка за входной контроль
 * и вся его работа – исходные данные, рабочая тетрадь, выводы, экран,
 * на котором он остановился.
 *
 * Здесь же перевод записи в набор строк «ключ = значение» и обратно.
 * Отсутствующее при чтении поле получает значение по умолчанию, поэтому
 * поля, которые появятся в исходных данных и тетради потом, чтения старых
 * записей не ломают. Недопустимое значение (не число там, где ждётся число,
 * незнакомый признак) делает запись недействительной: {@link #read} бросает
 * {@link IllegalArgumentException}. Защита оценки – в {@link Journal}.
 */
public class JournalRecord {

    /** Версия формата записи. */
    public static final String FORMAT = "1";

    private String name = "";
    private String group = "";
    private int listNumber;
    private int variantNumber;
    private long created;
    private long modified;
    /** Оценка за входной контроль; null – контроль не проводился. */
    private Integer score;
    /** Экран, на котором обучающийся остановился (имя карточки главного окна). */
    private String screen = "";
    private InputData data = new InputData();
    private final Notebook notebook = new Notebook();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group == null ? "" : group;
    }

    public int getListNumber() {
        return listNumber;
    }

    public void setListNumber(int listNumber) {
        this.listNumber = listNumber;
    }

    public int getVariantNumber() {
        return variantNumber;
    }

    public void setVariantNumber(int variantNumber) {
        this.variantNumber = variantNumber;
    }

    /** Время создания записи, мс от начала эпохи. */
    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    /** Время последнего изменения записи, мс от начала эпохи. */
    public long getModified() {
        return modified;
    }

    public void setModified(long modified) {
        this.modified = modified;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    /** Пройден ли входной контроль с положительной оценкой. */
    public boolean isPassed() {
        return score != null && score >= QuestionBank.PASS_MARK;
    }

    /** Получена ли неудовлетворительная оценка. */
    public boolean isFailed() {
        return score != null && score < QuestionBank.PASS_MARK;
    }

    public String getScreen() {
        return screen;
    }

    public void setScreen(String screen) {
        this.screen = screen == null ? "" : screen;
    }

    public InputData getData() {
        return data;
    }

    public void setData(InputData data) {
        this.data = data == null ? new InputData() : data.clone();
    }

    /** Рабочая тетрадь записи (своя копия, с тетрадью программы не связана). */
    public Notebook getNotebook() {
        return notebook;
    }

    /** Ключ обучающегося: фамилия с инициалами и группа (см. {@link Journal#key}). */
    public String key() {
        return Journal.key(name, group);
    }

    /** Переписывает в запись содержимое тетради. */
    public void setNotebook(Notebook source) {
        copy(source, notebook);
    }

    /** Переписывает содержимое одной тетради в другую. */
    public static void copy(Notebook from, Notebook to) {
        to.setChosenDegree(from.getChosenDegree());
        for (Notebook.Item item : Notebook.Item.values()) {
            Notebook.Page src = from.page(item);
            Notebook.Page dst = to.page(item);
            dst.setConclusion(src.getConclusion());
            dst.setSourceRow(src.getSourceRow());
            dst.getLines().clear();
            for (Notebook.Line l : src.getLines()) {
                dst.getLines().add(copy(l));
            }
        }
    }

    private static Notebook.Line copy(Notebook.Line l) {
        Notebook.Line c = new Notebook.Line(l.getParameter());
        c.set(Notebook.Quantity.RANGE, l.getRangeDynamic(), l.getRangeRandom(), l.getRangeTotal());
        c.set(Notebook.Quantity.SPEED, l.getSpeedDynamic(), l.getSpeedRandom(), l.getSpeedTotal());
        c.setSuspicious(l.isSuspicious());
        if (l.hasMode()) {
            c.setMode(mode(l.getDegree(), l.getSampleSize(), l.getStep(), l.getAnchor()));
        }
        return c;
    }

    private static InputData mode(int degree, int sampleSize, double step, int anchor) {
        InputData d = new InputData();
        d.setDegree(degree);
        d.setSampleSize(sampleSize);
        d.setStep(step);
        d.setAnchor(anchor);
        return d;
    }

    /** Число пунктов, заполненных настолько, чтобы строить график. */
    public int readyCount() {
        return notebook.readyCount(data.getMeasured());
    }

    /** Число пунктов с записанным (непустым) выводом. */
    public int conclusionCount() {
        int n = 0;
        for (Notebook.Item item : Notebook.Item.values()) {
            if (!notebook.page(item).getConclusion().trim().isEmpty()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------ запись

    /** Все поля записи, кроме защищённой оценки, – в набор строк. */
    public void write(Properties p) {
        p.setProperty("format", FORMAT);
        p.setProperty("student.name", name);
        p.setProperty("student.group", group);
        p.setProperty("student.list", String.valueOf(listNumber));
        p.setProperty("student.variant", String.valueOf(variantNumber));
        p.setProperty("created", String.valueOf(created));
        p.setProperty("modified", String.valueOf(modified));
        p.setProperty("screen", screen);

        p.setProperty("input.measurer", data.getMeasurer().name());
        p.setProperty("input.measured", data.getMeasured().name());
        p.setProperty("input.interval", String.valueOf(data.getInterval()));
        p.setProperty("input.orbitHeight", String.valueOf(data.getOrbitHeight()));
        p.setProperty("input.trackDistance", String.valueOf(data.getTrackDistance()));
        p.setProperty("input.sigmaRange", String.valueOf(data.getSigmaRange()));
        p.setProperty("input.sigmaVelocity", String.valueOf(data.getSigmaVelocity()));
        p.setProperty("input.degree", String.valueOf(data.getDegree()));
        p.setProperty("input.sampleSize", String.valueOf(data.getSampleSize()));
        p.setProperty("input.step", String.valueOf(data.getStep()));
        p.setProperty("input.anchor", String.valueOf(data.getAnchor()));

        p.setProperty("notebook.chosenDegree", String.valueOf(notebook.getChosenDegree()));
        for (Notebook.Item item : Notebook.Item.values()) {
            Notebook.Page page = notebook.page(item);
            String k = "page." + item.name() + ".";
            p.setProperty(k + "conclusion", page.getConclusion());
            p.setProperty(k + "sourceRow", String.valueOf(page.getSourceRow()));
            List<Notebook.Line> lines = page.getLines();
            p.setProperty(k + "lines", String.valueOf(lines.size()));
            for (int i = 0; i < lines.size(); i++) {
                Notebook.Line l = lines.get(i);
                String lk = k + "line." + i + ".";
                p.setProperty(lk + "parameter", String.valueOf(l.getParameter()));
                put(p, lk + "rangeDynamic", l.getRangeDynamic());
                put(p, lk + "rangeRandom", l.getRangeRandom());
                put(p, lk + "rangeTotal", l.getRangeTotal());
                put(p, lk + "speedDynamic", l.getSpeedDynamic());
                put(p, lk + "speedRandom", l.getSpeedRandom());
                put(p, lk + "speedTotal", l.getSpeedTotal());
                p.setProperty(lk + "suspicious", String.valueOf(l.isSuspicious()));
                if (l.hasMode()) {
                    p.setProperty(lk + "degree", String.valueOf(l.getDegree()));
                    p.setProperty(lk + "sampleSize", String.valueOf(l.getSampleSize()));
                    p.setProperty(lk + "step", String.valueOf(l.getStep()));
                    p.setProperty(lk + "anchor", String.valueOf(l.getAnchor()));
                }
            }
        }
    }

    private static void put(Properties p, String key, Double v) {
        if (v != null) {
            p.setProperty(key, String.valueOf(v));
        }
    }

    // ------------------------------------------------------------ чтение

    /**
     * Запись из набора строк (без проверки оценки – её проверяет журнал).
     *
     * @throws IllegalArgumentException недопустимое значение какого-либо поля
     */
    public static JournalRecord read(Properties p) {
        if (!FORMAT.equals(p.getProperty("format"))) {
            throw new IllegalArgumentException("незнакомый формат записи");
        }
        JournalRecord r = new JournalRecord();
        r.name = required(p, "student.name");
        r.group = required(p, "student.group");
        r.listNumber = integer(p, "student.list", 0);
        r.variantNumber = integer(p, "student.variant", 0);
        r.created = longValue(p, "created");
        r.modified = longValue(p, "modified");
        r.screen = p.getProperty("screen", "");

        InputData d = new InputData();
        String measurer = p.getProperty("input.measurer");
        if (measurer != null) {
            d.setMeasurer(InputData.Measurer.valueOf(measurer));
        }
        String measured = p.getProperty("input.measured");
        if (measured != null) {
            d.setMeasured(InputData.Measured.valueOf(measured));
        }
        d.setInterval(real(p, "input.interval", d.getInterval()));
        d.setOrbitHeight(real(p, "input.orbitHeight", d.getOrbitHeight()));
        d.setTrackDistance(real(p, "input.trackDistance", d.getTrackDistance()));
        d.setSigmaRange(real(p, "input.sigmaRange", d.getSigmaRange()));
        d.setSigmaVelocity(real(p, "input.sigmaVelocity", d.getSigmaVelocity()));
        d.setDegree(integer(p, "input.degree", d.getDegree()));
        d.setSampleSize(integer(p, "input.sampleSize", d.getSampleSize()));
        d.setStep(real(p, "input.step", d.getStep()));
        d.setAnchor(integer(p, "input.anchor", d.getAnchor()));
        r.data = d;

        Notebook nb = r.notebook;
        nb.setChosenDegree(integer(p, "notebook.chosenDegree", nb.getChosenDegree()));
        for (Notebook.Item item : Notebook.Item.values()) {
            String k = "page." + item.name() + ".";
            if (p.getProperty(k + "lines") == null) {
                continue;   // страница не записана: остаётся как в новой тетради
            }
            Notebook.Page page = nb.page(item);
            page.setConclusion(p.getProperty(k + "conclusion", ""));
            page.setSourceRow(integer(p, k + "sourceRow", page.getSourceRow()));
            int count = integer(p, k + "lines", 0);
            if (count < 0 || count > 1000) {
                throw new IllegalArgumentException("недопустимое число строк");
            }
            page.getLines().clear();
            for (int i = 0; i < count; i++) {
                String lk = k + "line." + i + ".";
                Notebook.Line l = new Notebook.Line(real(p, lk + "parameter", Double.NaN));
                if (Double.isNaN(l.getParameter())) {
                    throw new IllegalArgumentException("нет значения параметра строки");
                }
                l.set(Notebook.Quantity.RANGE, optional(p, lk + "rangeDynamic"),
                        optional(p, lk + "rangeRandom"), optional(p, lk + "rangeTotal"));
                l.set(Notebook.Quantity.SPEED, optional(p, lk + "speedDynamic"),
                        optional(p, lk + "speedRandom"), optional(p, lk + "speedTotal"));
                l.setSuspicious(Boolean.parseBoolean(p.getProperty(lk + "suspicious", "false")));
                if (p.getProperty(lk + "degree") != null) {
                    l.setMode(mode(integer(p, lk + "degree", 0), integer(p, lk + "sampleSize", 0),
                            real(p, lk + "step", 0), integer(p, lk + "anchor", 0)));
                }
                page.getLines().add(l);
            }
        }
        return r;
    }

    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("нет поля " + key);
        }
        return v;
    }

    private static int integer(Properties p, String key, int fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Integer.parseInt(v.trim());
    }

    private static long longValue(Properties p, String key) {
        return Long.parseLong(required(p, key).trim());
    }

    private static double real(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallback;
        }
        double d = Double.parseDouble(v.trim());
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("недопустимое число в поле " + key);
        }
        return d;
    }

    private static Double optional(Properties p, String key) {
        return p.getProperty(key) == null ? null : Double.valueOf(real(p, key, 0));
    }
}
