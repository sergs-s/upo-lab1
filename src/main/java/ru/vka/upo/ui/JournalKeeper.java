package ru.vka.upo.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Journal;
import ru.vka.upo.model.JournalRecord;
import ru.vka.upo.model.Notebook;
import ru.vka.upo.model.Settings;
import ru.vka.upo.model.Student;
import ru.vka.upo.model.VariantTable;

/**
 * Ведение журнала обучающихся со стороны интерфейса: когда запись
 * создаётся, сохраняется, восстанавливается и удаляется (см. {@link Journal}).
 *
 * Запись на диск идёт в отдельном потоке, по одной за раз и в порядке
 * поступления, поэтому интерфейс не ждёт диска (в том числе сетевого).
 * Снимок данных для записи делается в потоке Swing. Если содержимое со
 * времени прошлой записи не изменилось, повторно ничего не пишется.
 * Ошибки журнала работу не останавливают: о неудачной записи сообщает
 * только строка состояния.
 */
final class JournalKeeper {

    private final MainFrame owner;
    /** Журнал или null, если он отключён настройкой test.journal. */
    private final Journal journal;
    private final ExecutorService writer;
    /** Запись, которая ведётся сейчас; null – журнал для текущего входа не ведётся. */
    private JournalRecord active;
    /** Содержимое последней отправленной на запись версии – чтобы не писать одно и то же. */
    private String lastSnapshot;
    /** Экран, показанный последним. */
    private String screen = "";
    /** Ключ обучающегося, работавшего с программой последним. */
    private String lastKey;
    private volatile boolean failed;
    /** Сохранение раз в минуту; null, если журнал отключён. */
    private Timer timer;

    JournalKeeper(MainFrame owner) {
        this.owner = owner;
        this.journal = Journal.standard();
        if (journal == null) {
            writer = null;
            return;
        }
        writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "журнал обучающихся");
            t.setDaemon(true);
            return t;
        });
        // устаревшие и повреждённые записи удаляются при запуске программы
        writer.submit(() -> {
            try {
                journal.purge();
            } catch (RuntimeException ignore) {
                // журнал программу не останавливает
            }
        });
        // раз в минуту – на случай, если изменения не сопровождались
        // сменой экрана или нажатием кнопки
        timer = new Timer(60000, e -> {
            owner.storeNotebookPage();
            save();
        });
        timer.setRepeats(true);
        timer.start();
    }

    /** Ведётся ли журнал вообще (настройка test.journal). */
    boolean isEnabled() {
        return journal != null;
    }

    /** Запись обучающегося из журнала или null (в том числе при отключённом журнале). */
    JournalRecord find(Student s) {
        if (journal == null || s == null || s.isTeacher()) {
            return null;
        }
        return journal.load(s.getName(), s.getGroup());
    }

    /**
     * Вход нового пользователя. Преподаватель журнал не ведёт; при входе
     * другого обучающегося тетрадь прежнего очищается, чтобы его числа
     * не попали в чужую запись.
     */
    void onLogin(Student s) {
        active = null;
        lastSnapshot = null;
        if (journal == null || s == null || s.isTeacher()) {
            return;
        }
        String key = Journal.key(s.getName(), s.getGroup());
        if (lastKey != null && !lastKey.equals(key)) {
            JournalRecord.copy(new Notebook(), owner.getNotebook());
        }
        lastKey = key;
    }

    /**
     * Восстанавливает работу обучающегося по записи журнала: сведения
     * о нём, оценку, исходные данные и тетрадь.
     *
     * @return экран, на который следует перейти
     */
    String restore(JournalRecord r) {
        Student s = new Student();
        s.setRole(Student.Role.STUDENT);
        s.setName(r.getName());
        s.setGroup(r.getGroup());
        s.setListNumber(r.getListNumber());
        VariantTable.Variant v = variant(r.getVariantNumber());
        if (v != null) {
            s.setVariant(v);
        }
        s.setTestScore(r.getScore());
        owner.setStudent(s);
        onLogin(s);
        InputData d = r.getData().clone();
        owner.setInputData(d);
        JournalRecord.copy(r.getNotebook(), owner.getNotebook());
        owner.restoreInputFields(d, s.getVariant());
        active = r;
        lastSnapshot = null;
        if (r.getScore() == null && Settings.testEnabled()) {
            return MainFrame.CARD_WELCOME;
        }
        return MainFrame.CARD_NOTEBOOK.equals(r.getScreen())
                ? MainFrame.CARD_NOTEBOOK : MainFrame.CARD_INPUT;
    }

    private static VariantTable.Variant variant(int number) {
        for (VariantTable.Variant v : VariantTable.variants()) {
            if (v.getNumber() == number) {
                return v;
            }
        }
        return null;
    }

    /**
     * Начинает (или продолжает) запись журнала для вошедшего обучающегося:
     * по завершении входного контроля – с оценкой, при отключённом
     * контроле – без оценки.
     */
    void begin(Integer score) {
        Student s = owner.getStudent();
        if (journal == null || s == null || s.isTeacher()) {
            return;
        }
        String key = Journal.key(s.getName(), s.getGroup());
        if (active == null || !active.key().equals(key)) {
            active = new JournalRecord();
            active.setCreated(System.currentTimeMillis());
        }
        active.setScore(score);
        lastSnapshot = null;
        save();
    }

    /** Ведётся ли запись для текущего входа. */
    boolean isActive() {
        return active != null;
    }

    /**
     * Сохранится ли работа при выходе: запись ведётся и последняя попытка
     * записи в журнал удалась.
     */
    boolean keepsWork() {
        return journal != null && active != null && !failed;
    }

    /** Смена экрана: запоминается и сохраняется вместе с работой. */
    void onCard(String card) {
        screen = card;
        save();
    }

    /** Сохраняет работу, если она изменилась со времени прошлой записи. */
    void save() {
        if (journal == null || active == null) {
            return;
        }
        Student s = owner.getStudent();
        if (s == null || s.isTeacher()) {
            return;
        }
        final JournalRecord r = new JournalRecord();
        r.setName(s.getName());
        r.setGroup(s.getGroup());
        r.setListNumber(s.getListNumber());
        r.setVariantNumber(s.getVariantNumber());
        r.setCreated(active.getCreated());
        r.setScore(active.getScore());
        r.setScreen(screen.isEmpty() ? active.getScreen() : screen);
        r.setData(owner.getInputData());
        r.setNotebook(owner.getNotebook());
        java.util.Properties p = new java.util.Properties();
        r.write(p);
        p.remove("modified");
        String snapshot = new java.util.TreeMap<Object, Object>(p).toString();
        if (snapshot.equals(lastSnapshot)) {
            return;
        }
        lastSnapshot = snapshot;
        r.setModified(Math.max(System.currentTimeMillis(), r.getCreated()));
        active.setScreen(r.getScreen());
        writer.submit(() -> report(journal.save(r)));
    }

    private void report(boolean ok) {
        if (ok == !failed) {
            return;
        }
        failed = !ok;
        SwingUtilities.invokeLater(() -> owner.setStatus(ok
                ? "Журнал обучающихся снова записывается."
                : "Записать работу в журнал не удалось (папка «БД» недоступна): "
                        + "работа продолжается, но при закрытии программы не сохранится."));
    }

    /**
     * Удаляет запись, если работа завершена: все четыре пункта готовы по
     * текущим измеряемым параметрам, по каждому записан вывод и отчёт
     * сохранён (вызывается после успешной записи отчёта).
     *
     * @return true, если запись удалена
     */
    boolean finishIfComplete() {
        if (journal == null || active == null) {
            return false;
        }
        Notebook nb = owner.getNotebook();
        InputData.Measured measured = owner.getInputData().getMeasured();
        for (Notebook.Item item : Notebook.Item.values()) {
            Notebook.Page page = nb.page(item);
            if (!page.isReady(measured, item, nb.getChosenDegree())
                    || page.getConclusion().trim().isEmpty()) {
                return false;
            }
        }
        final JournalRecord done = active;
        active = null;
        lastSnapshot = null;
        writer.submit(() -> {
            journal.delete(done);
        });
        owner.setStatus("Работа завершена, запись в журнале удалена.");
        return true;
    }

    /** Сохраняет работу и дожидается окончания записи – при выходе из программы. */
    void shutdown() {
        if (journal == null) {
            return;
        }
        owner.storeNotebookPage();
        save();
        timer.stop();
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Журнал программы (для окна журнала в режиме преподавателя). */
    Journal getJournal() {
        return journal;
    }
}
