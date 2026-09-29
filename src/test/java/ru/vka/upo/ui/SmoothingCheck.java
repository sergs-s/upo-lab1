package ru.vka.upo.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import ru.vka.upo.math.LeastSquares;
import ru.vka.upo.model.InputData;

/**
 * Проверка расчёта окна «Сглаживание измерений дальности полиномом»
 * ({@link SmoothingModel}) и отрисовки самого окна.
 *
 * Проверяется: угол места на входе и выходе из зоны равен 7° (с точностью
 * не хуже 1e-6 рад), зона симметрична относительно траверза; коэффициенты
 * полинома участка совпадают с {@link LeastSquares} и с прямой формулой;
 * при нулевом шуме «измеренная − истинная» равна нулю, а «истинная −
 * полином» – разности истинной дальности и полинома по истинным значениям;
 * полином на графике совпадает с полиномом, построенным по «завышенным»
 * точкам графика.
 *
 * Если есть экран (например, под Xvfb), окно отрисовывается без показа
 * в начальном состоянии и после 10, 35 и 60 тактов; картинки кладутся
 * в папку, заданную первым аргументом (по умолчанию – текущая).
 *
 * Запуск (не входит в поставку, только для отработки):
 * <pre>
 * java -cp target/classes:target/check ru.vka.upo.ui.SmoothingCheck [папка]
 * </pre>
 */
public final class SmoothingCheck {

    private static int failures;

    private SmoothingCheck() {
    }

    public static void main(String[] args) throws Exception {
        InputData d = SmoothingModel.variantOne();
        SmoothingModel m = new SmoothingModel(d, 12345L);
        double limit = Math.toRadians(SmoothingModel.MIN_ELEVATION_DEG);
        double traverse = m.getOrbit().closestApproachTime();

        System.out.printf(Locale.ROOT, "Вариант 1: H = %.0f км, до трассы %.0f км, СКО %.0f м%n",
                d.getOrbitHeight(), d.getTrackDistance(), d.getSigmaRange());
        System.out.printf(Locale.ROOT, "Зона видимости: %.1f с (%.2f мин), шаг измерений %.2f с%n",
                m.duration(), m.duration() / 60, m.duration() / (SmoothingModel.COUNT - 1));

        check("угол места на входе 7°", Math.abs(m.elevation(m.getEntry()) - limit) < 1e-6);
        check("угол места на выходе 7°", Math.abs(m.elevation(m.getExit()) - limit) < 1e-6);
        check("до входа объект ниже 7°", m.elevation(m.getEntry() - 1) < limit);
        check("зона симметрична относительно траверза",
                Math.abs((m.getEntry() + m.getExit()) / 2 - traverse) < 1e-6);
        check("первое измерение на входе, последнее на выходе",
                m.time(0) == m.getEntry()
                && Math.abs(m.time(SmoothingModel.COUNT - 1) - m.getExit()) < 1e-9);

        // коэффициенты участков: LeastSquares и прямая формула
        double worstLsq = 0;
        double worstDirect = 0;
        for (int s = 0; s < SmoothingModel.SEGMENTS; s++) {
            int n = SmoothingModel.SEGMENT;
            double[] t = new double[n];
            double[] v = new double[n];
            boolean[] vel = new boolean[n];
            double[] w = new double[n];
            for (int k = 0; k < n; k++) {
                int i = s * n + k;
                t[k] = m.time(i) - m.segmentStart(s);
                v[k] = m.measured(i);
                w[k] = 1.0;
            }
            double scale = t[n - 1];
            LeastSquares lsq = LeastSquares.forPolynomial(t, vel, w, 1, scale);
            double[] c = lsq.solve(v, w);
            double[] ours = m.coefficients(s);
            worstLsq = Math.max(worstLsq, Math.max(Math.abs(c[0] - ours[0]),
                    Math.abs(c[1] / scale - ours[1]) * scale));
            // прямая формула через суммы
            double st = 0, sv = 0, stt = 0, stv = 0;
            for (int k = 0; k < n; k++) {
                st += t[k];
                sv += v[k];
                stt += t[k] * t[k];
                stv += t[k] * v[k];
            }
            double a1 = (n * stv - st * sv) / (n * stt - st * st);
            double a0 = (sv - a1 * st) / n;
            worstDirect = Math.max(worstDirect, Math.max(Math.abs(a0 - ours[0]),
                    Math.abs(a1 - ours[1]) * scale));
        }
        System.out.printf(Locale.ROOT, "Расхождение с LeastSquares %.3g м, с прямой формулой %.3g м%n",
                worstLsq, worstDirect);
        check("коэффициенты совпадают с LeastSquares", worstLsq < 1e-4);
        check("коэффициенты совпадают с прямой формулой", worstDirect < 1e-4);

        // нулевой шум
        InputData quiet = d.clone();
        quiet.setSigmaRange(0.0);
        SmoothingModel z = new SmoothingModel(quiet, 1L);
        double worstNoise = 0;
        double worstErr = 0;
        for (int i = 0; i < SmoothingModel.COUNT; i++) {
            int s = SmoothingModel.segmentOf(i);
            worstNoise = Math.max(worstNoise, Math.abs(z.measured(i) - z.trueRange(i)));
            worstErr = Math.max(worstErr, Math.abs(z.error(i)
                    - (z.trueRange(i) - z.truePolynomial(s, z.time(i)))));
        }
        check("при нулевом шуме столбец 4 равен нулю", worstNoise == 0);
        check("при нулевом шуме столбец 7 – истинная − полином по истинным", worstErr < 1e-6);

        // согласованность графика и таблицы: полином по «завышенным» точкам
        double worstDisplay = 0;
        for (int s = 0; s < SmoothingModel.SEGMENTS; s++) {
            int n = SmoothingModel.SEGMENT;
            double st = 0, sv = 0, stt = 0, stv = 0;
            for (int k = 0; k < n; k++) {
                int i = s * n + k;
                double t = m.time(i) - m.segmentStart(s);
                double v = m.displayMeasured(i);
                st += t;
                sv += v;
                stt += t * t;
                stv += t * v;
            }
            double a1 = (n * stv - st * sv) / (n * stt - st * st);
            double a0 = (sv - a1 * st) / n;
            for (int k = 0; k < n; k++) {
                int i = s * n + k;
                double direct = a0 + a1 * (m.time(i) - m.segmentStart(s));
                worstDisplay = Math.max(worstDisplay,
                        Math.abs(direct - m.displayPolynomial(s, m.time(i))));
            }
        }
        System.out.printf(Locale.ROOT, "Полином на графике: расхождение %.3g м%n", worstDisplay);
        check("полином на графике построен по точкам графика", worstDisplay < 1e-3);

        for (int s = 0; s < SmoothingModel.SEGMENTS; s++) {
            System.out.printf(Locale.ROOT, "  участок %d: СКО(полином − измеренная) %.1f м, "
                    + "СКО(истинная − полином) %.1f м%n", s + 1, m.residualRms(s), m.errorRms(s));
        }

        if (!GraphicsEnvironment.isHeadless()) {
            final String dir = args.length > 0 ? args[0] : ".";
            SwingUtilities.invokeAndWait(() -> {
                try {
                    render(dir);
                } catch (Exception e) {
                    failures++;
                    System.out.println("ОШИБКА отрисовки: " + e);
                    e.printStackTrace(System.out);
                }
            });
        } else {
            System.out.println("Экрана нет: отрисовка окна пропущена");
        }

        System.out.println();
        System.out.println(failures == 0 ? "Все проверки пройдены." : "Ошибок: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Отрисовка окна без показа после заданного числа тактов. */
    private static void render(String dir) throws Exception {
        int[] ticks = {0, 10, 35, 60};
        int[][] sizes = {{1200, 850}, {1000, 700}};
        for (int[] size : sizes) {
            SmoothingFrame f = SmoothingFrame.createHidden(7L);
            f.setSize(size[0], size[1]);
            int done = 0;
            for (int t : ticks) {
                // после 35 тактов объект на полпути к следующему измерению
                f.advance(t - done, t == 35 ? 0.5 : 0);
                done = t;
                // окно получает ресурсы экрана, но не показывается
                f.addNotify();
                f.validate();
                layout(f);
                Container c = f.getContentPane();
                BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                ((javax.swing.JComponent) c).print(img.getGraphics());
                File out = new File(dir, "smoothing_" + size[0] + "_" + t + ".png");
                ImageIO.write(img, "png", out);
                System.out.println("Отрисовано: " + out.getPath());
            }
            f.dispose();
        }
    }

    private static void layout(Component c) {
        if (c instanceof Container) {
            ((Container) c).doLayout();
            for (Component k : ((Container) c).getComponents()) {
                layout(k);
            }
        }
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  так  " : "  НЕТ  ") + what);
        if (!ok) {
            failures++;
        }
    }
}
