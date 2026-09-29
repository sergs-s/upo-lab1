package ru.vka.upo.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import ru.vka.upo.core.ErrorRow;
import ru.vka.upo.core.Processor;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Journal;
import ru.vka.upo.model.JournalRecord;
import ru.vka.upo.model.Notebook;
import ru.vka.upo.model.Question;
import ru.vka.upo.model.TestSession;

/**
 * Сценарий работы с журналом обучающихся через интерфейс программы.
 *
 * Экраны создаются настоящие (главное окно, панели), действия выполняются
 * вызовом обработчиков кнопок, окна сообщений закрываются автоматически,
 * их текст проверяется. Нужен экран (например, Xvfb).
 *
 * Шаги: вход обучающегося, контроль с оценкой 4, заполнение двух пунктов,
 * закрытие программы; повторный вход – контроля нет, тетрадь восстановлена;
 * вход другого обучающегося, контроль с оценкой 2, повторный вход – не
 * допускается; удаление записи преподавателем – контроль снова доступен;
 * полное завершение работы с сохранением отчёта – запись удалена.
 *
 * Программа сама перезапускает себя во временной папке с настроечным
 * файлом (контроль и журнал включены), чтобы не трогать папку проекта.
 *
 * <pre>
 * xvfb-run java -cp target/classes:target/check ru.vka.upo.ui.JournalScenario
 * </pre>
 */
public final class JournalScenario {

    private static int failures;
    /** Тексты закрытых автоматически окон сообщений. */
    private static final List<String> messages = Collections.synchronizedList(new ArrayList<String>());
    /** Куда сохранить отчёт, когда откроется окно выбора файла. */
    private static volatile File reportFile;

    private JournalScenario() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Нужен экран (например, xvfb-run): сценарий не выполнен");
            System.exit(1);
        }
        if (args.length == 0 || !"--child".equals(args[0])) {
            System.exit(relaunch());
        }
        startCloser();
        SwingUtilities.invokeAndWait(() -> {
            try {
                scenario();
            } catch (Exception e) {
                failures++;
                System.out.println("ОШИБКА: " + e);
                e.printStackTrace(System.out);
            }
        });
        System.out.println();
        System.out.println(failures == 0 ? "Все проверки пройдены." : "Ошибок: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    private static int relaunch() throws Exception {
        Path dir = Files.createTempDirectory("journal-scenario");
        Files.write(dir.resolve("settings.properties"), ("test.enabled = true\n"
                + "test.journal = true\nwindow.fullscreen = false\nteacher.password = kaf33\n").getBytes(StandardCharsets.UTF_8));
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder pb = new ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-cp",
                System.getProperty("java.class.path"), JournalScenario.class.getName(), "--child");
        pb.directory(dir.toFile());
        pb.inheritIO();
        int code = pb.start().waitFor();
        deleteTree(dir.toFile());
        return code;
    }

    private static void scenario() throws Exception {
        // 1. вход, контроль с оценкой 4, два пункта тетради, закрытие
        MainFrame f = frame();
        login(f, "Иванов И.И.", "101", 3);
        check("после входа – экран приветствия", card(f).equals(MainFrame.CARD_WELCOME));
        call(field(f, "welcomePanel"), "btnStartActionPerformed");
        check("контроль предложен", card(f).equals(MainFrame.CARD_TEST));
        passTest(f, 4);
        check("после контроля – ввод данных", card(f).equals(MainFrame.CARD_INPUT));
        call(field(f, "inputPanel"), "btnComputeActionPerformed");
        // числа и выводы заносятся до показа тетради: при показе они
        // попадают в поля экрана, как если бы их набрал обучающийся
        fill(f, Notebook.Item.A, "Выбрана степень 3.");
        fill(f, Notebook.Item.B, "");
        f.showCard(MainFrame.CARD_NOTEBOOK);
        f.getNotebookPanel().store();
        String before = snapshot(f.getNotebook());
        close(f);
        Journal j = Journal.standard();
        check("папка журнала «БД» создана", Files.isDirectory(Journal.defaultFolder())
                && Journal.defaultFolder().getFileName().toString().equals("БД"));
        JournalRecord rec = j.load("Иванов И.И.", "101");
        check("запись есть, оценка 4", rec != null && Integer.valueOf(4).equals(rec.getScore()));

        // 2. повторный вход: контроля нет, тетрадь восстановлена
        f = frame();
        messages.clear();
        login(f, "  иванов   и.и. ", "101", 5);
        check("сообщение о восстановлении", has("Работа восстановлена: входной контроль "
                + "пройден ранее, оценка 4. Продолжайте с того места, где остановились."));
        check("сказано, что номер по списку взят из журнала", has("Номер по списку взят из журнала: 3"));
        check("возврат в тетрадь, где остановился", card(f).equals(MainFrame.CARD_NOTEBOOK));
        check("оценка восстановлена", Integer.valueOf(4).equals(f.getStudent().getTestScore()));
        check("номер по списку из журнала", f.getStudent().getListNumber() == 3);
        check("тетрадь восстановлена", snapshot(f.getNotebook()).equals(before));
        f.showCard(MainFrame.CARD_WELCOME);
        call(field(f, "welcomePanel"), "btnStartActionPerformed");
        check("«Начать» не ведёт к повторному контролю", card(f).equals(MainFrame.CARD_INPUT));
        close(f);

        // 3. неудовлетворительная оценка: повторно не допускается
        f = frame();
        login(f, "Петров П.П.", "101", 4);
        call(field(f, "welcomePanel"), "btnStartActionPerformed");
        passTest(f, 2);
        close(f);
        f = frame();
        messages.clear();
        login(f, "Петров П.П.", "101", 4);
        check("сообщение о непройденном контроле", has("Входной контроль ранее не пройден "
                + "(оценка 2). Повторить его можно только с разрешения преподавателя."));
        check("дальше экрана входа не пускает", card(f).equals(MainFrame.CARD_LOGIN));

        // 4. преподаватель удаляет запись – контроль снова доступен
        teacher(f);
        check("вход преподавателя", card(f).equals(MainFrame.CARD_TEACHER));
        JournalFrame jf = JournalFrame.createHidden(f.getJournalKeeper().getJournal());
        check("в журнале две записи", jf.rowCount() == 2);
        JournalRecord petrov = j.load("Петров П.П.", "101");
        jf.deleteRecords(Collections.singletonList(petrov));
        check("после удаления – одна запись", jf.rowCount() == 1);
        jf.dispose();
        check("журнал не заводит записи преподавателю", j.list().size() == 1);
        call(field(f, "teacherPanel"), "btnBackActionPerformed");
        login(f, "Петров П.П.", "101", 4);
        call(field(f, "welcomePanel"), "btnStartActionPerformed");
        check("после удаления записи контроль доступен", card(f).equals(MainFrame.CARD_TEST));
        close(f);

        // 5. полное завершение работы с отчётом – запись удалена
        f = frame();
        messages.clear();
        login(f, "Иванов И.И.", "101", 3);
        for (Notebook.Item item : Notebook.Item.values()) {
            fill(f, item, "Вывод по пункту " + item.getLetter() + ".");
        }
        f.showCard(MainFrame.CARD_NOTEBOOK);
        Path out = Files.createTempFile("отчёт", ".docx");
        reportFile = out.toFile();
        call(f.getNotebookPanel(), "btnReportActionPerformed");
        check("отчёт сохранён", Files.size(out) > 1000);
        check("строка состояния: работа завершена", status(f).contains(
                "Работа завершена, запись в журнале удалена."));
        f.showCard(MainFrame.CARD_RESULT);
        // удаление идёт в потоке журнала: дожидаемся его завершения
        f.getJournalKeeper().shutdown();
        check("запись журнала удалена", !Files.exists(j.fileFor(Journal.key("Иванов И.И.", "101"))));
        check("после завершения запись не появляется снова", j.load("Иванов И.И.", "101") == null);
        f.dispose();
        Files.deleteIfExists(out);
    }

    // ---------------------------------------------------------- действия

    private static MainFrame frame() {
        MainFrame f = new MainFrame();
        f.setVisible(true);
        return f;
    }

    /** Закрытие программы: то же, что делает «Выход» до завершения процесса. */
    private static void close(MainFrame f) {
        f.getJournalKeeper().shutdown();
        f.dispose();
    }

    private static void login(MainFrame f, String name, String group, int list) throws Exception {
        f.showCard(MainFrame.CARD_LOGIN);
        Object lp = field(f, "loginPanel");
        ((javax.swing.JRadioButton) field(lp, "rbStudent")).setSelected(true);
        ((JTextField) field(lp, "txtName")).setText(name);
        ((JTextField) field(lp, "txtGroup")).setText(group);
        ((JSpinner) field(lp, "spnListNumber")).setValue(list);
        call(lp, "btnEnterActionPerformed");
    }

    private static void teacher(MainFrame f) throws Exception {
        f.showCard(MainFrame.CARD_LOGIN);
        Object lp = field(f, "loginPanel");
        ((javax.swing.JRadioButton) field(lp, "rbTeacher")).setSelected(true);
        ((javax.swing.JPasswordField) field(lp, "pwdTeacher")).setText("kaf33");
        call(lp, "btnEnterActionPerformed");
    }

    /** Отвечает на вопросы контроля так, чтобы набрать заданную оценку. */
    private static void passTest(MainFrame f, int score) throws Exception {
        Object tp = field(f, "testPanel");
        TestSession s = (TestSession) field(tp, "session");
        for (int i = 0; i < s.getCount(); i++) {
            Question q = s.getCurrentQuestion();
            int right = 1;
            for (int o = 1; o <= q.getOptions().size(); o++) {
                if (q.isCorrect(o)) {
                    right = o;
                }
            }
            s.answer(i < score ? right : (right == 1 ? 2 : 1));
            s.next();
        }
        Method m = tp.getClass().getDeclaredMethod("finish");
        m.setAccessible(true);
        m.invoke(tp);
    }

    /** Заполняет пункт тетради числами первой строки расчёта, как обучающийся. */
    private static void fill(MainFrame f, Notebook.Item item, String conclusion) {
        Notebook nb = f.getNotebook();
        InputData base = f.getInputData();
        Notebook.Page page = nb.page(item);
        for (Notebook.Line l : page.getLines()) {
            InputData d = item.apply(base, l.getParameter(), nb.getChosenDegree());
            ErrorRow r = new Processor(d).table().get(0);
            l.set(Notebook.Quantity.RANGE, r.getRangeDynamic(), r.getRangeRandom(), r.getRangeTotal());
            l.setMode(d);
        }
        page.setConclusion(conclusion);
        f.saveJournal();
    }

    private static String snapshot(Notebook nb) {
        JournalRecord r = new JournalRecord();
        r.setNotebook(nb);
        java.util.Properties p = new java.util.Properties();
        r.write(p);
        for (String k : new String[] {"created", "modified", "screen"}) {
            p.remove(k);
        }
        return new java.util.TreeMap<Object, Object>(p).toString();
    }

    private static String card(MainFrame f) throws Exception {
        return (String) field(f, "currentCard");
    }

    private static String status(MainFrame f) throws Exception {
        return ((javax.swing.JLabel) field(f, "lblStatus")).getText();
    }

    private static boolean has(String text) {
        synchronized (messages) {
            for (String m : messages) {
                if (m.replace('\n', ' ').contains(text)) {
                    return true;
                }
            }
        }
        System.out.println("    сообщения: " + messages);
        return false;
    }

    /**
     * Поток, закрывающий окна сообщений (и запоминающий их текст) и
     * отвечающий на окно выбора файла отчёта.
     */
    private static void startCloser() {
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    for (Window w : Window.getWindows()) {
                        if (!(w instanceof JDialog) || !w.isShowing()) {
                            continue;
                        }
                        JFileChooser fc = find(w, JFileChooser.class);
                        if (fc != null && reportFile != null) {
                            fc.setSelectedFile(reportFile);
                            fc.approveSelection();
                            continue;
                        }
                        JOptionPane op = find(w, JOptionPane.class);
                        if (op != null) {
                            messages.add(String.valueOf(op.getMessage()));
                            Object[] options = op.getOptions();
                            op.setValue(options != null && options.length > 0
                                    ? options[0] : Integer.valueOf(JOptionPane.OK_OPTION));
                            w.dispose();
                        }
                    }
                });
            }
        }, "закрытие окон сообщений");
        t.setDaemon(true);
        t.start();
    }

    private static <T> T find(Component c, Class<T> type) {
        if (type.isInstance(c)) {
            return type.cast(c);
        }
        if (c instanceof Container) {
            for (Component k : ((Container) c).getComponents()) {
                T found = find(k, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Object field(Object o, String name) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Field fl = c.getDeclaredField(name);
                fl.setAccessible(true);
                return fl.get(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void call(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name, java.awt.event.ActionEvent.class);
        m.setAccessible(true);
        m.invoke(o, (Object) null);
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
