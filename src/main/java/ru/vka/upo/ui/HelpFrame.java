package ru.vka.upo.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * Справка «Как это считается»: метод наименьших квадратов и обе
 * составляющие ошибки оценивания.
 *
 * Формулы программа набирает сама (см. {@link MathText}) и вставляет в текст
 * картинками, поэтому корень выглядит корнем, а дробь дробью. Поясняющие
 * рисунки чертит {@link MathFigures}. Ни внешних библиотек, ни доступа
 * в сеть не требуется – справка работает и в компьютерном классе.
 *
 * Кнопка «Открыть в браузере» сохраняет ту же страницу отдельным файлом:
 * в браузере она крупнее, её удобно листать рядом с программой и печатать.
 *
 * Отдельное окно (JFrame), а не JDialog: так оно разворачивается и
 * восстанавливается штатной кнопкой в заголовке, как любое обычное окно
 * Windows. При этом ведёт себя как модальное: одновременно открыт только
 * один экземпляр, и на время его показа главное окно программы недоступно.
 */
public class HelpFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Кегль, которым набираются формулы-картинки. */
    private static final int FORMULA_SIZE = 26;

    /** Формулы справки: имя файла и выражение. */
    private static final Map<String, String> FORMULAS = new LinkedHashMap<>();

    static {
        FORMULAS.put("model", "r_{i} = R(t_{i}) + \\Delta_{i},"
                + "    M[\\Delta_{i}] = 0,    M[\\Delta_{i}^{2}] = \\sigma^{2}");
        FORMULAS.put("poly", "r(s) = \\alpha_{0} + \\alpha_{1}s + \\alpha_{2}s^{2}"
                + " + \\ldots + \\alpha_{m}s^{m},    s = t - t_{0}");
        FORMULAS.put("j", "J = \\Delta_{1}^{2} + \\Delta_{2}^{2} + \\ldots"
                + " + \\Delta_{N}^{2} = \\Sigma_{i}(r_{i} - r(s_{i}))^{2} \\to \\min");
        FORMULAS.put("zero", "J(\\alpha_{0}) = \\Sigma_{i}(r_{i} - \\alpha_{0})^{2},"
                + "     \\frac{dJ}{d\\alpha_{0}} = -2\\Sigma_{i}(r_{i} - \\alpha_{0}) = 0");
        FORMULAS.put("average", "\\alpha_{0} = \\frac{1}{N}\\Sigma_{i} r_{i}");
        FORMULAS.put("sigma", "\\sigma_{\\alpha_{0}} = \\frac{\\sigma}{\\sqrt{N}}");
        FORMULAS.put("matrix", "r = A\\alpha + \\Delta,"
                + "     J(\\alpha) = (r - A\\alpha)^{T}(r - A\\alpha)");
        FORMULAS.put("deriv", "\\frac{\\partial J}{\\partial \\alpha}"
                + " = -2A^{T}(r - A\\alpha) = 0");
        FORMULAS.put("normal", "A^{T}A\\alpha* = A^{T}r,"
                + "     \\alpha* = (A^{T}A)^{-1}A^{T}r");
        FORMULAS.put("cov", "K = \\sigma^{2}(A^{T}A)^{-1}");
        FORMULAS.put("total", "ER = \\sqrt{ERD^{2} + ERS^{2}}");
    }

    private static Path folder;

    /** Логическая ширина каждой формулы: картинка начерчена вдвое крупнее. */
    private static final Map<String, Integer> WIDTHS = new LinkedHashMap<>();

    /** Единственный открытый экземпляр окна – второй не заводится. */
    private static HelpFrame instance;

    /**
     * Запоминается отдельно: у JFrame нет настоящего AWT-владельца
     * (getOwner() тут всегда вернёт null, т.к. конструктор не вызывает
     * super(owner)), поэтому признак полноэкранного режима параметром
     * owner конструктора не передать в другие методы иначе как полем.
     */
    private boolean fullScreenOwner;

    private HelpFrame(Window owner) {
        super("Сведения из теории. Аппроксимация измерений методом наименьших квадратов");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        fullScreenOwner = owner instanceof MainFrame && ((MainFrame) owner).isFullScreenMode();

        JTextPane text = new JTextPane();
        text.setContentType("text/html");
        text.setEditable(false);
        text.setText(html(true));
        text.setCaretPosition(0);

        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        // наглядный показ сглаживания измерений полиномом – отдельное окно
        JButton smoothing = new JButton("Наглядно: сглаживание измерений");
        smoothing.addActionListener(e -> SmoothingFrame.show(this));
        buttons.add(smoothing);
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
        setSize(new Dimension(900, 760));
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

    /** Открыта ли справка из программы, развёрнутой на весь экран. */
    boolean isFullScreenOwner() {
        return fullScreenOwner;
    }

    /**
     * Показывает справку. Если она уже открыта, второй раз не создаётся –
     * уже открытое окно просто выводится на передний план.
     */
    public static void show(Component parent) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        if (instance != null && instance.isDisplayable()) {
            instance.bringToFront();
            return;
        }
        final Window ownerFinal = owner;
        instance = new HelpFrame(owner);
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
            Path page = files().resolve("Сведения из теории. МНК.html");
            Files.write(page, html(false).getBytes(StandardCharsets.UTF_8));
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(page.toUri());
            } else {
                JOptionPane.showMessageDialog(this, "Страница сохранена:\n" + page,
                        "Справка", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(this,
                    "Открыть в браузере не удалось: " + e.getMessage(),
                    "Справка", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Временная папка с рисунками и формулами; создаётся один раз за сеанс. */
    private static synchronized Path files() throws IOException {
        if (folder != null && Files.isDirectory(folder)) {
            return folder;
        }
        Path dir = Files.createTempDirectory("upo-lab1-help");
        dir.toFile().deleteOnExit();
        write(dir, "mnk0.png", MathFigures.average());
        write(dir, "mnk1.png", MathFigures.residuals());
        write(dir, "mnk2.png", MathFigures.minimum());
        write(dir, "mnk3.png", MathFigures.components());
        for (Map.Entry<String, String> e : FORMULAS.entrySet()) {
            BufferedImage img = MathText.render(e.getValue(), FORMULA_SIZE);
            write(dir, "f_" + e.getKey() + ".png", img);
            WIDTHS.put(e.getKey(), img.getWidth() / MathText.SCALE);
        }
        folder = dir;
        return dir;
    }

    /**
     * Записывает картинку в двух видах: крупном (как начерчена) и уменьшенном
     * ровно вдвое. Крупный идёт в браузер и на печать, уменьшенный – в окно
     * программы: качественно уменьшенная копия читается заметно чётче, чем
     * та же картинка, сжатая средствами показа.
     */
    private static void write(Path dir, String name, BufferedImage img)
            throws IOException {
        Path file = dir.resolve(name);
        ImageIO.write(img, "png", file.toFile());
        file.toFile().deleteOnExit();

        int w = Math.max(1, img.getWidth() / MathFigures.SCALE);
        int h = Math.max(1, img.getHeight() / MathFigures.SCALE);
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        Path smallFile = dir.resolve(small(name));
        ImageIO.write(small, "png", smallFile.toFile());
        smallFile.toFile().deleteOnExit();
    }

    /** Имя уменьшенной копии. */
    private static String small(String name) {
        return name.replace(".png", "_1x.png");
    }

    private static String base() {
        try {
            return files().toUri().toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Формула отдельной строкой. В окне программы берётся уменьшенная копия
     * в натуральную величину – без пересчёта размера она не мылится.
     */
    private static String f(String name, boolean screen) {
        String file = "f_" + name + (screen ? "_1x" : "") + ".png";
        Integer w = WIDTHS.get(name);
        return "<p align='center'><img src='" + base() + file + "'"
                + (screen || w == null ? "" : " width='" + w + "'") + "></p>";
    }

    /** Рисунок отдельной строкой. */
    private static String pic(String name, int width, boolean screen) {
        return screen
                ? "<p align='center'><img src='" + base() + name + "_1x.png'></p>"
                : "<p align='center'><img src='" + base() + name + ".png' width='"
                        + width + "'></p>";
    }

    /** Текст пособия; screen – набор для окна программы, иначе для браузера. */
    private static String html(boolean screen) {
        int width = screen ? 660 : 760;
        String style = screen
                ? "font-family:serif; font-size:15pt; margin:16px"
                : "font-family:Georgia,serif; font-size:14pt; margin:40px auto; "
                + "max-width:920px; line-height:1.45";
        String h1 = screen ? "20pt" : "19pt";
        String h2 = screen ? "17pt" : "16pt";

        return "<html><head><meta charset='utf-8'>"
        + "<title>Аппроксимация измерений методом наименьших квадратов</title></head>"
        + "<body style='" + style + "'>"

        + "<h1 style='font-size:" + h1 + "'>Как формируется оценка</h1>"
        + "<p style='color:#555'>Сведения из теории к лабораторной работе "
        + "«Исследование эффективности устройств предварительной обработки». "
        + "Используемый математический аппарат опирается на две составляющие: "
        + "производную и систему линейных уравнений. Простейший случай – "
        + "полином нулевой степени – рассматривается без матричной записи.</p>"

        + "<h2 style='font-size:" + h2 + "'>1. Постановка задачи</h2>"
        + "<p>Измерительное средство (РЛС, командно-измерительная система или "
        + "иной измеритель) за интервал усреднения выполняет N измерений "
        + "дальности в моменты t<sub>1</sub>, t<sub>2</sub>, …, t<sub>N</sub>. "
        + "Результат каждого измерения содержит случайную ошибку:</p>"
        + f("model", screen)
        + "<p>где R(t) – истинное значение текущего навигационного параметра, "
        + "σ – среднее квадратическое отклонение ошибки измерения. Истинная "
        + "зависимость R(t) неизвестна и подлежит восстановлению по "
        + "совокупности зашумлённых отсчётов. Задача устройства "
        + "предварительной обработки состоит в получении оценки параметра, "
        + "точность которой выше точности единичного измерения.</p>"

        + "<h2 style='font-size:" + h2 + "'>2. Полином нулевой степени: "
        + "среднее арифметическое</h2>"
        + "<p>Примем простейшую модель: на интервале усреднения изменением "
        + "дальности пренебрегаем, то есть все измерения относятся к одному "
        + "значению α<sub>0</sub>. Аппроксимирующая зависимость вырождается "
        + "в горизонтальную прямую.</p>"
        + pic("mnk0", width, screen)
        + "<p>Положение прямой определяется из условия наименьшей суммы "
        + "квадратов отклонений. Неизвестная одна, поэтому достаточно "
        + "приравнять нулю производную критерия:</p>"
        + f("zero", screen)
        + "<p>Из полученного равенства следует &sum;r<sub>i</sub> = "
        + "N·α<sub>0</sub>, откуда</p>"
        + f("average", screen)
        + "<p>Оценкой служит среднее арифметическое результатов измерений. "
        + "Матричный аппарат для этого случая не требуется. Случайная ошибка "
        + "такой оценки определяется известным соотношением:</p>"
        + f("sigma", screen)
        + "<p>то есть усреднение N отсчётов уменьшает случайную составляющую "
        + "в &radic;N раз. Одновременно проявляется противоположный эффект: "
        + "горизонтальная прямая не отражает движения объекта, и с ростом "
        + "длительности интервала усреднения расхождение модели с истинной "
        + "зависимостью нарастает. Обе составляющие ошибки оценивания "
        + "присутствуют, таким образом, уже в простейшей модели.</p>"

        + "<h2 style='font-size:" + h2 + "'>3. Полиномиальная модель "
        + "движения</h2>"
        + "<p>Для учёта движения объекта истинная зависимость приближается "
        + "отрезком ряда Тейлора – полиномом степени m:</p>"
        + f("poly", screen)
        + "<p>Отсчёт времени ведётся от момента привязки t<sub>0</sub>, чем "
        + "определяется физический смысл коэффициентов: α<sub>0</sub> – "
        + "оценка дальности в момент привязки, α<sub>1</sub> – оценка "
        + "радиальной скорости, 2α<sub>2</sub> – оценка радиального "
        + "ускорения. При m = 0 модель сводится к рассмотренной выше, при "
        + "m = 1 движение полагается равномерным, при m = 2 – "
        + "равноускоренным.</p>"

        + "<h2 style='font-size:" + h2 + "'>4. Критерий наименьших "
        + "квадратов</h2>"
        + "<p>Число измерений превышает число неизвестных коэффициентов, "
        + "поэтому система уравнений переопределена и точного решения "
        + "не имеет. В качестве наилучшего принимается приближение, "
        + "доставляющее минимум сумме квадратов отклонений измерений "
        + "от аппроксимирующей кривой.</p>"
        + pic("mnk1", width, screen)
        + f("j", screen)
        + "<p>Выбор квадратичного критерия обусловлен тремя обстоятельствами. "
        + "Отклонения имеют оба знака, и их алгебраическая сумма не "
        + "характеризует качество приближения. Квадратичная функция "
        + "дифференцируема, что позволяет отыскать минимум аналитически. "
        + "Наконец, при нормальном законе распределения ошибок измерений "
        + "оценка по этому критерию совпадает с оценкой максимального "
        + "правдоподобия.</p>"

        + "<h2 style='font-size:" + h2 + "'>5. Матричная форма записи</h2>"
        + "<p>При m + 1 неизвестных запись уравнений по отдельности "
        + "нерациональна. Введём вектор измерений r, вектор оцениваемых "
        + "коэффициентов α и матрицу A размерности N × (m + 1), i-я "
        + "строка которой содержит степени относительного времени "
        + "1, s<sub>i</sub>, s<sub>i</sub><sup>2</sup>, …, "
        + "s<sub>i</sub><sup>m</sup>:</p>"
        + f("matrix", screen)
        + "<p>Каждая строка матрицы A отвечает одному измерению, каждый "
        + "столбец – одному оцениваемому коэффициенту, а её элементы "
        + "определяются только моментами измерений: ни сами измерения, "
        + "ни их ошибки в матрицу не входят. Иначе говоря, A задаётся "
        + "расстановкой измерений во времени – объёмом выборки N, шагом Δt "
        + "и положением момента привязки, то есть тем самым режимом "
        + "обработки, который в настоящей работе и исследуется. "
        + "В руководстве к работе эта матрица так и называется – матрицей A; "
        + "в математической статистике для неё принято название матрицы "
        + "плана, поскольку она описывает план измерительного эксперимента.</p>"

        + "<h2 style='font-size:" + h2 + "'>6. Определение оценки</h2>"
        + "<p>Критерий J квадратичен по вектору неизвестных, матрица его "
        + "вторых производных положительно определена, следовательно, "
        + "экстремум единственный и является минимумом. Условие минимума – "
        + "равенство нулю производной критерия по вектору коэффициентов.</p>"
        + pic("mnk2", width, screen)
        + f("deriv", screen)
        + "<p>Отсюда следует система нормальных уравнений и её решение:</p>"
        + f("normal", screen)
        + "<p>Это система m + 1 линейных уравнений с таким же числом "
        + "неизвестных – классический результат Лежандра и Гаусса. Оценка "
        + "линейна относительно измерений: каждый коэффициент представляет "
        + "собой взвешенную сумму отсчётов с весами, определяемыми только "
        + "расстановкой измерений во времени. При m = 0 матрица "
        + "A<sup>T</sup>A вырождается в число N, и решение переходит "
        + "в среднее арифметическое, полученное в разделе 2.</p>"

        + "<h2 style='font-size:" + h2 + "'>7. Случайная составляющая "
        + "ошибки</h2>"
        + "<p>Вследствие линейности оценки ошибки измерений переносятся "
        + "на неё по тому же закону. Корреляционная матрица ошибок "
        + "оценивания коэффициентов:</p>"
        + f("cov", screen)
        + "<p>Корень квадратный из первого диагонального элемента даёт "
        + "случайную ошибку оценки дальности ERS, из второго – случайную "
        + "ошибку оценки радиальной скорости EVS. В соотношение входят "
        + "объём выборки, шаг измерений, степень полинома и точность "
        + "измерений; от параметров траектории случайная составляющая "
        + "не зависит.</p>"

        + "<h2 style='font-size:" + h2 + "'>8. Динамическая составляющая "
        + "ошибки</h2>"
        + "<p>Полиномиальная модель приближает истинную зависимость лишь "
        + "приблизительно. Расхождение модели с истинным значением параметра "
        + "в момент привязки, существующее и при отсутствии шума измерений, "
        + "называется динамической ошибкой ERD. Она возрастает с увеличением "
        + "длительности интервала усреднения и с понижением степени полинома "
        + "и не зависит от точности измерений.</p>"
        + pic("mnk3", width, screen)
        + "<p>Составляющие статистически независимы, поэтому суммируются их "
        + "квадраты:</p>"
        + f("total", screen)
        + "<p>Этим определяется содержание работы. Увеличение интервала "
        + "усреднения T<sub>у</sub> = (N − 1)Δt, то есть объёма выборки N "
        + "либо шага Δt, снижает случайную "
        + "составляющую пропорционально 1/&radic;N и одновременно повышает "
        + "динамическую. Повышение степени полинома снижает динамическую "
        + "составляющую, но увеличивает случайную, поскольку полином высокой "
        + "степени воспроизводит не движение объекта, а шум измерений. "
        + "Оптимальным является режим обработки, обеспечивающий минимум "
        + "полной ошибки; он определяется расчётом для конкретных условий "
        + "измерений.</p>"

        + "<h2 style='font-size:" + h2 + "'>9. Измерение радиальной "
        + "скорости</h2>"
        + "<p>Измеритель может определять не только дальность, но и "
        + "радиальную скорость V – производную дальности по времени. "
        + "Порядок обработки зависит от того, какие параметры измеряются.</p>"
        + "<p><b>Дальность и скорость вместе.</b> Обе величины описывают "
        + "одно и то же движение, поэтому обрабатываются совместно, одним "
        + "полиномом дальности степени m. Измерение скорости связано с его "
        + "коэффициентами через производную: соответствующая строка матрицы A "
        + "содержит 0, 1, 2s<sub>i</sub>, …, m·s<sub>i</sub><sup>m−1</sup>. "
        + "Точность измерений дальности и скорости различна, поэтому в "
        + "критерий наименьших квадратов каждое отклонение входит с весом, "
        + "обратно пропорциональным дисперсии соответствующего измерения "
        + "(1/σ<sub>R</sub><sup>2</sup> или 1/σ<sub>V</sub><sup>2</sup>). "
        + "Оценка скорости берётся из коэффициента α<sub>1</sub>; её "
        + "динамическая и случайная ошибки – EVD и EVS, полная – EV. "
        + "Полином скорости здесь на единицу ниже степенью, чем полином "
        + "дальности, поэтому при m = 0 скорость не оценивается.</p>"
        + "<p><b>Только скорость.</b> Постоянная составляющая дальности "
        + "α<sub>0</sub> на скорость не влияет и по одним измерениям скорости "
        + "не определяется: дальность в этом случае не оценивается. "
        + "Измерения скорости сглаживаются собственным полиномом по времени</p>"
        + "<p align='center'>V(t) = β<sub>0</sub> + β<sub>1</sub>s + "
        + "β<sub>2</sub>s<sup>2</sup> + … + β<sub>m</sub>s<sup>m</sup>,</p>"
        + "<p>и степень m – это степень полинома самой скорости. Всё "
        + "сказанное в разделах 2–8 о дальности переносится на скорость "
        + "без изменений: при m = 0 оценка скорости есть среднее "
        + "арифметическое измерений выборки, её случайная ошибка "
        + "EVS = σ<sub>V</sub>/&radic;N; с ростом степени полинома "
        + "динамическая ошибка EVD убывает, а случайная EVS растёт. Степень "
        + "полинома для пунктов б, в и г в этом случае выбирается по "
        + "наименьшей полной ошибке скорости EV.</p>"
        + "<p>Таким образом, смысл степени m зависит от измеряемых параметров: "
        + "при измерении дальности (одной или вместе со скоростью) это "
        + "степень полинома дальности, при измерении одной скорости – "
        + "степень полинома скорости. Так же была устроена и прежняя "
        + "программа лабораторной работы.</p>"

        + "</body></html>";
    }
}
