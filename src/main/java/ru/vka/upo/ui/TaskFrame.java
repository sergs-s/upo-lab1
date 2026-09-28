package ru.vka.upo.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.Graphics2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Окно «Задание на работу»: что и в каком порядке исследуется, какие
 * таблицы предстоит заполнить и какие графики по ним построить.
 *
 * Явного деления на пункты в самой программе нет – пункты различаются
 * только сочетанием параметров режима обработки, поэтому порядок работы
 * собран здесь в одном месте, чтобы обучающийся видел его целиком.
 *
 * Отдельное окно (JFrame), а не JDialog: так оно разворачивается и
 * восстанавливается штатной кнопкой в заголовке, как SummaryFrame и
 * HelpFrame. Ведёт себя как модальное: один экземпляр, главное окно
 * программы на время его показа недоступно.
 */
public class TaskFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static Path folder;

    /** Единственный открытый экземпляр окна – второй не заводится. */
    private static TaskFrame instance;

    /**
     * Запоминается отдельно: у JFrame нет настоящего AWT-владельца
     * (getOwner() тут всегда вернёт null, т.к. конструктор не вызывает
     * super(owner)), поэтому признак полноэкранного режима параметром
     * owner конструктора не передать в другие методы иначе как полем.
     */
    private boolean fullScreenOwner;

    private TaskFrame(Window owner) {
        super("Задание на лабораторную работу");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        fullScreenOwner = owner instanceof MainFrame && ((MainFrame) owner).isFullScreenMode();

        JTextPane text = new JTextPane();
        text.setContentType("text/html");
        text.setEditable(false);
        text.setText(html());
        text.setCaretPosition(0);

        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        // в полноэкранном режиме кнопка «Открыть в браузере» не нужна:
        // и так всё видно во весь экран, а сам браузер всё равно окажется
        // позади главного окна программы и будет недоступен
        if (!fullScreenOwner) {
            JButton browser = new JButton("Открыть в браузере");
            browser.addActionListener(e -> openInBrowser());
            buttons.add(new JLabel("Страницу можно открыть отдельным окном браузера "
                    + "и распечатать   "));
            buttons.add(browser);
        }
        buttons.add(close);

        setLayout(new BorderLayout(8, 8));
        add(new JScrollPane(text), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(new Dimension(820, 700));
        setLocationRelativeTo(owner);
        Emblem.applyTo(this);

        // окно модальное: сворачивать его незачем, а свёрнутое при
        // отключённом главном окне программы выглядело бы так, будто
        // программа зависла, поэтому попытку свернуть сразу отменяем
        addWindowStateListener(e -> {
            if ((e.getNewState() & Frame.ICONIFIED) != 0) {
                setExtendedState(e.getOldState());
                JOptionPane.showMessageDialog(this,
                        "Это окно нельзя свернуть, можно только закрыть.",
                        "Предварительная обработка", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // если сама программа развёрнута на весь экран (настройка
        // window.fullscreen = true), это окно тоже открывается без рамки
        // и сразу на весь экран; кнопка «Закрыть» внизу уже есть, поэтому
        // отдельной кнопки для этого случая не требуется
        if (fullScreenOwner) {
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    /**
     * Показывает задание. Если оно уже открыто, второй раз не создаётся –
     * уже открытое окно просто выводится на передний план.
     */
    public static void show(Component parent) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        if (instance != null && instance.isDisplayable()) {
            instance.bringToFront();
            return;
        }
        final Window ownerFinal = owner;
        instance = new TaskFrame(owner);
        instance.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                instance = null;
                if (ownerFinal != null) {
                    ownerFinal.setEnabled(true);
                    ownerFinal.toFront();
                }
            }
        });
        if (ownerFinal != null) {
            ownerFinal.setEnabled(false);
        }
        instance.setVisible(true);
        instance.bringToFront();
    }

    /**
     * Принудительно выводит окно поверх владельца. В обычном режиме –
     * кратким включением/выключением "поверх всех окон": этого достаточно,
     * чтобы операционная система вывела окно вперёд, а сам флаг не остаётся
     * висеть постоянно (иначе стало бы невозможно переключиться на другую
     * программу). В полноэкранном же режиме главное окно программы само
     * держит "поверх всех окон" постоянно (см. MainFrame.customize()) –
     * против этого временное включение бессильно: как только оно снимается,
     * главное окно тут же перекрывает открытое поверх него. Поэтому здесь
     * флаг оставляется включённым на всё время показа окна.
     */
    private void bringToFront() {
        setAlwaysOnTop(true);
        toFront();
        requestFocus();
        if (!fullScreenOwner) {
            setAlwaysOnTop(false);
        }
    }

    private void openInBrowser() {
        try {
            Path page = figures().resolve("Задание на лабораторную работу.html");
            Files.write(page, html().getBytes(StandardCharsets.UTF_8));
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(page.toUri());
            } else {
                JOptionPane.showMessageDialog(this, "Страница сохранена:\n" + page,
                        "Задание на работу", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(this,
                    "Открыть в браузере не удалось: " + e.getMessage(),
                    "Задание на работу", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Временная папка с эскизом графика. */
    private static synchronized Path figures() throws IOException {
        if (folder != null && Files.isDirectory(folder)) {
            return folder;
        }
        Path dir = Files.createTempDirectory("upo-lab1-task");
        dir.toFile().deleteOnExit();
        BufferedImage img = MathFigures.sketch();
        Path file = dir.resolve("sketch.png");
        ImageIO.write(img, "png", file.toFile());
        file.toFile().deleteOnExit();
        // уменьшенная вдвое копия для показа в окне: без пересчёта размера
        // средствами показа рисунок остаётся чётким
        int w = img.getWidth() / MathFigures.SCALE;
        int h = img.getHeight() / MathFigures.SCALE;
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.drawImage(img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        Path smallFile = dir.resolve("sketch_1x.png");
        ImageIO.write(small, "png", smallFile.toFile());
        smallFile.toFile().deleteOnExit();
        folder = dir;
        return dir;
    }

    /** Образец таблицы: шапка и пустые строки со значениями параметра. */
    private static String table(String parameter, String[] values) {
        StringBuilder sb = new StringBuilder(
                "<table border='1' cellspacing='0' cellpadding='4' width='96%'>"
                + "<tr bgcolor='#EEF3F8'><th>" + parameter + "</th>"
                + "<th>ERD, м<br>динамическая</th>"
                + "<th>ERS, м<br>случайная</th>"
                + "<th>ER, м<br>полная</th></tr>");
        for (String v : values) {
            sb.append("<tr><td align='center'>").append(v)
              .append("</td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td></tr>");
        }
        return sb.append("</table>").toString();
    }

    private static String html() {
        String base;
        try {
            base = figures().toUri().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return "<html><body style='font-family:serif; font-size:14pt; margin:14px'>"

        + "<h1 style='font-size:16pt'>Задание на лабораторную работу</h1>"
        + "<p>Требуется исследовать, как точность оценивания дальности зависит "
        + "от параметров режима обработки, и по итогам выбрать режим, дающий "
        + "наименьшую полную ошибку. Исследование состоит из четырёх пунктов; "
        + "в каждом меняется один параметр, остальные закреплены.</p>"

        + "<p><b>Порядок работы одинаков во всех пунктах:</b> задать режим "
        + "на экране ввода данных, нажать «Рассчитать», выписать в рабочую "
        + "тетрадь <b>значения ERD, ERS и ER из первой строки</b> полученной "
        + "таблицы, повторить для следующего значения параметра. Когда все "
        + "пять строк таблицы заполнены – построить график и записать вывод.</p>"

        + "<p style='color:#555'>Первая строка берётся не произвольно: так "
        + "предписывает руководство к работе (п. 4.3). Прочие строки той же "
        + "таблицы показывают, как ошибки меняются по интервалу измерений, "
        + "и в графики не идут.</p>"

        + "<p>Если по варианту измеряется и радиальная скорость, в тетрадь "
        + "из той же первой строки выписываются также <b>EVD, EVS и EV</b> "
        + "и строятся графики ошибок оценивания скорости (при измерении одной "
        + "скорости – только они, и степень в пункте а выбирается по наименьшей "
        + "EV). При m = 0 скорость полиномом не оценивается: эти ячейки "
        + "остаются пустыми.</p>"

        + "<h2 style='font-size:13pt'>Пункт а. Влияние степени полинома m</h2>"
        + "<p>Постоянны: N = 49, Δt = 0,1 с, M0 = 25. Степень менять от 0 до 4.</p>"
        + table("m", new String[] {"0", "1", "2", "3", "4"})
        + "<p><b>Строим:</b> зависимость ERD, ERS и ER от m. Из этого пункта "
        + "берётся степень полинома, давшая наименьшую полную ошибку: она "
        + "используется во всех последующих пунктах.</p>"

        + "<h2 style='font-size:13pt'>Пункт б. Влияние шага измерений Δt</h2>"
        + "<p>Постоянны: N = 49, M0 = 25, степень m – из пункта а.</p>"
        + table("Δt, с", new String[] {"0,01", "0,05", "0,1", "0,5", "1"})
        + "<p><b>Строим:</b> зависимость ERD, ERS и ER от Δt, по оси шага "
        + "удобен логарифмический масштаб.</p>"

        + "<h2 style='font-size:13pt'>Пункт в. Влияние объёма выборки N</h2>"
        + "<p>Постоянны: Δt = 1 с, степень m – из пункта а. Момент привязки "
        + "при каждом изменении объёма выборки переносится в середину выборки "
        + "(M0 = 3, 7, 13, 19 и 25 соответственно); в программе для этого "
        + "служит кнопка «в середину» рядом с полем момента привязки. Иначе "
        + "привязка осталась бы на краю расширяющегося окна усреднения и в "
        + "зависимость вмешалось бы влияние второго параметра.</p>"
        + table("N", new String[] {"5", "13", "25", "37", "49"})
        + "<p><b>Строим:</b> зависимость ERD, ERS и ER от N.</p>"

        + "<h2 style='font-size:13pt'>Пункт г. Влияние момента привязки M0</h2>"
        + "<p>Постоянны: N = 49, Δt = 1 с, степень m – из пункта а.</p>"
        + table("M0", new String[] {"5", "13", "25", "37", "49"})
        + "<p><b>Строим:</b> зависимость ERD, ERS и ER от номера точки "
        + "привязки.</p>"

        + "<h2 style='font-size:13pt'>Как выглядит результат</h2>"
        + "<p>На каждом графике три кривые. Случайная ошибка ERS и "
        + "динамическая ERD ведут себя противоположно, а полная ER, как корень "
        + "из суммы их квадратов, имеет наименьшее значение где-то посередине. "
        + "Именно это положение и отвечает наилучшему режиму обработки.</p>"
        + "<p align='center'><img src='" + base + "sketch_1x.png'></p>"

        + "<h2 style='font-size:13pt'>Что должно получиться в итоге</h2>"
        + "<p>Четыре заполненные таблицы, четыре графика и вывод по каждому "
        + "пункту, а в конце – значения m, Δt, N и M0, дающие наивысшую "
        + "точность оценивания для заданной траектории. Всё это программа "
        + "соберёт в отчёт по кнопке «Сохранить отчёт» в рабочей тетради.</p>"

        + "</body></html>";
    }
}
