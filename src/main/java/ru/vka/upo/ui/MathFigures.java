package ru.vka.upo.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Пояснительные рисунки к справке о методе наименьших квадратов и к заданию
 * на работу.
 *
 * Рисунки не связаны с расчётом: числа на них взяты условно, чтобы картина
 * читалась с первого взгляда. Программа чертит их сама, поэтому справка
 * не требует ни внешних файлов, ни доступа в сеть.
 *
 * Чертятся они с двукратным запасом по разрешению: на экране картинка
 * показывается вдвое меньше, чем нарисована, и линии с буквами выходят
 * чёткими, а не размытыми. Условные обозначения вынесены под поле графика –
 * так подписи заведомо не налезают на кривые.
 */
public final class MathFigures {

    /** Во сколько раз рисунок чертится крупнее, чем показывается. */
    public static final int SCALE = 2;

    private static final Color AXIS = new Color(0x44, 0x44, 0x44);
    private static final Color CURVE = new Color(0xC0, 0x39, 0x2B);
    private static final Color POINT = new Color(0x1F, 0x4E, 0x79);
    private static final Color RESIDUAL = new Color(0x80, 0x80, 0x80);
    private static final Color ACCENT = new Color(0x2E, 0x86, 0x4B);
    private static final Color TRUTH = new Color(0x9E, 0xC3, 0xE0);
    private static final Color DYNAMIC = new Color(0x8E, 0x44, 0xAD);

    private MathFigures() {
    }

    /** Холст заданного логического размера, начерченный вдвое крупнее. */
    private static BufferedImage canvas(int w, int h) {
        return new BufferedImage(w * SCALE, h * SCALE, BufferedImage.TYPE_INT_RGB);
    }

    private static Graphics2D start(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.scale(SCALE, SCALE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
        return g;
    }

    private static void axes(Graphics2D g, int x0, int y0, int x1, int y1,
            String xName, String yName) {
        g.setColor(AXIS);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Line2D.Double(x0, y0, x0, y1));
        g.draw(new Line2D.Double(x0, y0, x1, y0));
        g.draw(new Line2D.Double(x1, y0, x1 - 7, y0 - 4));
        g.draw(new Line2D.Double(x1, y0, x1 - 7, y0 + 4));
        g.draw(new Line2D.Double(x0, y1, x0 - 4, y1 + 7));
        g.draw(new Line2D.Double(x0, y1, x0 + 4, y1 + 7));
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font(Font.SERIF, Font.ITALIC, 15));
        if (!xName.isEmpty()) {
            g.drawString(xName, x1 - 4, y0 + 18);
        }
        g.drawString(yName, x0 - 24, y1 - 6);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
    }

    /**
     * Условные обозначения одной строкой под полем графика. Подписи стоят
     * не у кривых, а здесь, поэтому пересечься с ними не могут.
     */
    private static int legend(Graphics2D g, int x, int y, int maxX, Color[] colors,
            String[] titles) {
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
        int cx = x;
        int cy = y;
        for (int i = 0; i < colors.length; i++) {
            int need = 26 + g.getFontMetrics().stringWidth(titles[i]);
            if (cx > x && cx + need > maxX) {
                // очередное обозначение не помещается: переносим на строку ниже
                cx = x;
                cy += 20;
            }
            g.setColor(colors[i]);
            g.setStroke(new BasicStroke(3f));
            g.draw(new Line2D.Double(cx, cy - 4, cx + 20, cy - 4));
            g.setStroke(new BasicStroke(1f));
            g.setColor(Color.DARK_GRAY);
            g.drawString(titles[i], cx + 26, cy);
            cx += need + 24;
        }
        return cy;
    }

    /** Пояснение под рисунком; длинная строка переносится по словам. */
    private static void note(Graphics2D g, String text, int x, int y, int maxX) {
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (g.getFontMetrics().stringWidth(candidate) > maxX - x && line.length() > 0) {
                g.drawString(line.toString(), x, cy);
                line = new StringBuilder(word);
                cy += 18;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            g.drawString(line.toString(), x, cy);
        }
    }

    // --------------------------------------------------------- рисунок m=0

    /**
     * Простейшее сглаживание – полином нулевой степени: все измерения
     * заменяются одним числом, их средним арифметическим.
     */
    public static BufferedImage average() {
        int w = 660;
        int h = 320;
        BufferedImage img = canvas(w, h);
        Graphics2D g = start(img);
        int x0 = 60;
        int y0 = 224;
        int x1 = 630;
        int y1 = 36;
        axes(g, x0, y0, x1, y1, "t", "r");

        double level = 130;
        double[] xs = {110, 180, 250, 320, 390, 460, 530};
        double[] dy = {-40, 32, -24, 46, -36, 22, -44};

        g.setColor(CURVE);
        g.setStroke(new BasicStroke(2.6f));
        g.draw(new Line2D.Double(90, level, 560, level));
        g.setStroke(new BasicStroke(1f));

        for (int i = 0; i < xs.length; i++) {
            double ym = level + dy[i];
            g.setColor(RESIDUAL);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10f, new float[] {3f, 3f}, 0f));
            g.draw(new Line2D.Double(xs[i], level, xs[i], ym));
            g.setStroke(new BasicStroke(1f));
            g.setColor(POINT);
            g.fill(new Ellipse2D.Double(xs[i] - 4, ym - 4, 8, 8));
        }

        int ly = legend(g, x0, y0 + 30, x1, new Color[] {POINT, CURVE},
                new String[] {"измерения", "оценка – среднее арифметическое"});
        note(g, "прямая горизонтальна: движения объекта такая модель "
                + "не учитывает вовсе", x0, ly + 24, x1);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------- рисунок невязок

    /**
     * Измерения, аппроксимирующая кривая и отклонения: какую именно сумму
     * квадратов делают наименьшей.
     */
    public static BufferedImage residuals() {
        int w = 660;
        int h = 350;
        BufferedImage img = canvas(w, h);
        Graphics2D g = start(img);
        int x0 = 60;
        int y0 = 254;
        int x1 = 630;
        int y1 = 36;
        axes(g, x0, y0, x1, y1, "t", "r");

        Path2D.Double curve = new Path2D.Double();
        for (int i = 0; i <= 120; i++) {
            double x = 90 + (560 - 90) * i / 120.0;
            double y = model(x);
            if (i == 0) {
                curve.moveTo(x, y);
            } else {
                curve.lineTo(x, y);
            }
        }
        g.setColor(CURVE);
        g.setStroke(new BasicStroke(2.4f));
        g.draw(curve);
        g.setStroke(new BasicStroke(1f));

        double[] xs = {110, 180, 250, 320, 390, 460, 530};
        double[] dy = {-34, 26, -20, 40, -30, 18, -38};
        for (int i = 0; i < xs.length; i++) {
            double yc = model(xs[i]);
            double ym = yc + dy[i];
            g.setColor(RESIDUAL);
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10f, new float[] {3f, 3f}, 0f));
            g.draw(new Line2D.Double(xs[i], yc, xs[i], ym));
            g.setStroke(new BasicStroke(1f));
            g.setColor(POINT);
            g.fill(new Ellipse2D.Double(xs[i] - 4, ym - 4, 8, 8));
            g.setColor(RESIDUAL);
            g.setFont(new Font(Font.SERIF, Font.ITALIC, 14));
            // подпись отклонения ставится со стороны, свободной от кривой
            float ty = (float) ((yc + ym) / 2 + 5);
            g.drawString("Δ" + (i + 1), (float) (xs[i] + 7), ty);
        }

        int ly = legend(g, x0, y0 + 30, x1, new Color[] {POINT, CURVE, RESIDUAL},
                new String[] {"измерения", "аппроксимирующая кривая",
                    "отклонения Δi"});
        note(g, "J = Δ1² + Δ2² + … + ΔN²  →  наименьшее", x0, ly + 24, x1);
        g.dispose();
        return img;
    }

    /**
     * Условный ход дальности: минимум в траверзе, ветви вверх. На экране ось
     * ординат направлена вниз, поэтому наименьшей дальности отвечает
     * наибольшая координата.
     */
    private static double model(double x) {
        double u = (x - 330) / 240.0;
        return 110 + 85 * (1 - u * u);
    }

    // ------------------------------------------------------ рисунок минимума

    /**
     * Как отыскивается минимум: сумма квадратов – парабола по оцениваемому
     * коэффициенту, в её низшей точке производная равна нулю.
     */
    public static BufferedImage minimum() {
        int w = 660;
        int h = 360;
        BufferedImage img = canvas(w, h);
        Graphics2D g = start(img);
        int x0 = 70;
        int y0 = 252;
        int x1 = 630;
        int y1 = 36;
        axes(g, x0, y0, x1, y1, "α", "J(α)");

        double xm = 350;
        double ym = 200;
        Path2D.Double p = new Path2D.Double();
        for (int i = 0; i <= 140; i++) {
            double x = 100 + (600 - 100) * i / 140.0;
            // ось ординат на экране направлена вниз, поэтому парабола
            // с ветвями вверх записывается со знаком минус
            double y = ym - 0.0021 * (x - xm) * (x - xm);
            if (y < y1 + 10) {
                continue;
            }
            if (p.getCurrentPoint() == null) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }
        g.setColor(CURVE);
        g.setStroke(new BasicStroke(2.4f));
        g.draw(p);
        g.setStroke(new BasicStroke(1f));

        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(new Line2D.Double(xm - 95, ym, xm + 95, ym));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {4f, 4f}, 0f));
        g.draw(new Line2D.Double(xm, ym, xm, y0));
        g.setStroke(new BasicStroke(1f));
        g.fill(new Ellipse2D.Double(xm - 5, ym - 5, 10, 10));

        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 15));
        g.drawString("α*", (float) (xm - 9), (float) (y0 + 18));

        int ly = legend(g, x0, y0 + 40, x1, new Color[] {CURVE, ACCENT},
                new String[] {"сумма квадратов отклонений J(α)",
                    "касательная в минимуме: dJ/dα = 0"});
        note(g, "J квадратична по искомым коэффициентам, поэтому минимум "
                + "единственный", x0, ly + 24, x1);
        g.dispose();
        return img;
    }

    // -------------------------------------------- рисунок составляющих ошибки

    /**
     * Две составляющие ошибки: расхождение полинома с истиной в момент
     * привязки и разброс полинома от опыта к опыту.
     */
    public static BufferedImage components() {
        int w = 660;
        int h = 375;
        BufferedImage img = canvas(w, h);
        Graphics2D g = start(img);
        int x0 = 60;
        int y0 = 262;
        int x1 = 630;
        int y1 = 36;
        axes(g, x0, y0, x1, y1, "t", "R");

        double xa = 170;
        double xs = 110;
        double xe = 560;

        Path2D.Double truth = new Path2D.Double();
        for (int i = 0; i <= 140; i++) {
            double x = xs + (xe - xs) * i / 140.0;
            double y = truthAt(x);
            if (i == 0) {
                truth.moveTo(x, y);
            } else {
                truth.lineTo(x, y);
            }
        }
        g.setColor(TRUTH);
        g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(truth);
        g.setStroke(new BasicStroke(1f));

        // прямая, проведённая по этой кривой методом наименьших квадратов
        int n = 40;
        double sx = 0;
        double sy = 0;
        double sxx = 0;
        double sxy = 0;
        for (int i = 0; i < n; i++) {
            double x = xs + (xe - xs) * i / (n - 1.0);
            double y = truthAt(x);
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
        }
        double b = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        double a = (sy - b * sx) / n;

        double[] shift = {-24, 0, 23};
        double[] tilt = {0.055, 0.0, -0.05};
        for (int k = 0; k < shift.length; k++) {
            double ys = a + b * xs + shift[k] + tilt[k] * (xs - xa);
            double ye = a + b * xe + shift[k] + tilt[k] * (xe - xa);
            g.setColor(k == 1 ? CURVE : new Color(0xE8, 0xA5, 0x9E));
            g.setStroke(new BasicStroke(k == 1 ? 2.2f : 1.2f));
            g.draw(new Line2D.Double(xs, ys, xe, ye));
        }
        g.setStroke(new BasicStroke(1f));

        double yTruth = truthAt(xa);
        double yFit = a + b * xa;
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {4f, 4f}, 0f));
        g.draw(new Line2D.Double(xa, y1 + 8, xa, y0));
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 13));
        g.drawString("момент привязки", (float) (xa - 22), (float) (y1 + 4));

        g.setColor(DYNAMIC);
        g.setStroke(new BasicStroke(2.4f));
        g.draw(new Line2D.Double(xa, yTruth, xa, yFit));
        bar(g, xa, yTruth);
        bar(g, xa, yFit);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
        // подпись ставится над размерным отрезком, в стороне от линий
        g.drawString("ERD", (float) (xa - 14),
                (float) (Math.min(yTruth, yFit) - 8));

        double xr = xe - 95;
        double top = a + b * xr + shift[0] + tilt[0] * (xr - xa);
        double low = a + b * xr + shift[2] + tilt[2] * (xr - xa);
        g.setColor(CURVE);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(new Line2D.Double(xr, top, xr, low));
        bar(g, xr, top);
        bar(g, xr, low);
        g.setStroke(new BasicStroke(1f));
        g.drawString("ERS", (float) (xr - 12), (float) (Math.min(top, low) - 8));

        int ly = legend(g, x0, y0 + 30, x1, new Color[] {TRUTH, CURVE, DYNAMIC},
                new String[] {"истинная дальность", "полином (три опыта)",
                    "ERD в момент привязки"});
        note(g, "ERD не зависит от шума, ERS – от траектории; "
                + "полная ошибка складывается из их квадратов", x0, ly + 24, x1);
        g.dispose();
        return img;
    }

    /** Условный ход дальности для рисунка составляющих ошибки. */
    private static double truthAt(double x) {
        double u = (x - 320) / 250.0;
        return 105 + 105 * (1 - u * u) + 34 * u;
    }

    private static void bar(Graphics2D g, double x, double y) {
        g.draw(new Line2D.Double(x - 5, y, x + 5, y));
    }

    // ---------------------------------------------------- эскиз к заданию

    /**
     * Эскиз к заданию: как обычно выглядит результат исследования.
     * Составляющие ведут себя противоположно, полная ошибка имеет минимум.
     */
    public static BufferedImage sketch() {
        int w = 660;
        int h = 380;
        BufferedImage img = canvas(w, h);
        Graphics2D g = start(img);
        int x0 = 70;
        int y0 = 262;
        int x1 = 630;
        int y1 = 36;
        axes(g, x0, y0, x1, y1, "", "E");

        Path2D.Double dyn = new Path2D.Double();
        Path2D.Double rnd = new Path2D.Double();
        Path2D.Double tot = new Path2D.Double();
        double best = 0;
        double bestValue = Double.MAX_VALUE;
        for (int i = 0; i <= 120; i++) {
            double u = i / 120.0;
            double x = x0 + 30 + (x1 - x0 - 80) * u;
            double d = 200 * Math.exp(-2.6 * u) + 6;
            double r = 10 + 135 * u * u;
            double t = Math.sqrt(d * d + r * r);
            if (t < bestValue) {
                bestValue = t;
                best = x;
            }
            if (i == 0) {
                dyn.moveTo(x, y0 - d);
                rnd.moveTo(x, y0 - r);
                tot.moveTo(x, y0 - t);
            } else {
                dyn.lineTo(x, y0 - d);
                rnd.lineTo(x, y0 - r);
                tot.lineTo(x, y0 - t);
            }
        }
        g.setStroke(new BasicStroke(2.2f));
        g.setColor(POINT);
        g.draw(dyn);
        g.setColor(ACCENT);
        g.draw(rnd);
        g.setColor(CURVE);
        g.setStroke(new BasicStroke(2.8f));
        g.draw(tot);
        g.setStroke(new BasicStroke(1f));

        g.setColor(CURVE);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {4f, 4f}, 0f));
        g.draw(new Line2D.Double(best, y0 - bestValue, best, y0));
        g.setStroke(new BasicStroke(1f));
        g.fill(new Ellipse2D.Double(best - 4.5, y0 - bestValue - 4.5, 9, 9));

        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, 14));
        g.drawString("параметр режима обработки  (m, T, N или M0)",
                (float) (x0 + 170), (float) (y0 + 20));

        int ly = legend(g, x0, y0 + 40, x1, new Color[] {POINT, ACCENT, CURVE},
                new String[] {"ERD – динамическая", "ERS – случайная",
                    "ER – полная"});
        note(g, "наилучший режим – там, где полная ошибка наименьшая", x0, ly + 24, x1);
        g.dispose();
        return img;
    }
}
