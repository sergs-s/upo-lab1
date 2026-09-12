package ru.vka.upo.ui;

import java.util.Locale;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * График зависимости, построенный по числам, которые обучающийся выписал сам.
 *
 * Программа берёт на себя только черчение: масштаб осей, при необходимости
 * логарифмический, подписи и условные обозначения. Никаких своих значений
 * она в график не добавляет, поэтому ошибка при переписывании числа сразу
 * видна выпадающей точкой.
 */
public class ChartView extends JPanel {

    private static final Color BG = Color.WHITE;
    private static final Color AXIS = new Color(0x88, 0x88, 0x88);
    private static final Color GRID = new Color(0xEE, 0xEE, 0xEE);
    private static final Color SUSPECT = new Color(0xE6, 0x7E, 0x22);
    private static final Color PICKED = new Color(0x33, 0x66, 0x99);
    private static final Color BEST = new Color(0x44, 0x44, 0x44);

    /** Ряд точек: подпись, цвет и сами значения. */
    public static class Series {

        final String title;
        final Color color;
        final List<double[]> points = new ArrayList<>(); // {x, y}
        final List<Boolean> suspicious = new ArrayList<>();

        public Series(String title, Color color) {
            this.title = title;
            this.color = color;
        }

        public void add(double x, double y, boolean suspect) {
            points.add(new double[] {x, y});
            suspicious.add(suspect);
        }
    }

    private final List<Series> series = new ArrayList<>();

    /** Ряд, наименьшее значение в котором отмечается кружком. */
    private Series best;

    /** Значение параметра, точки которого выделяются: выбранная строка. */
    private Double picked;
    private String xTitle = "";
    private String yTitle = "";
    private boolean logX;
    private boolean logY = true;
    private String message = "Заполните таблицу и нажмите «Построить график»";

    public ChartView() {
        setBackground(BG);
        setPreferredSize(new Dimension(520, 300));
    }

    public void clear() {
        series.clear();
        best = null;
        picked = null;
        repaint();
    }

    /**
     * Отмечает тонким кружком наименьшее значение в указанном ряду.
     * Для полной ошибки это и есть оптимальный режим обработки.
     */
    public void markMinimum(Series s) {
        this.best = s;
        repaint();
    }

    /**
     * Выделяет точки, отвечающие одному значению параметра: так на графике
     * видно, какая строка таблицы выбрана. Значение null снимает выделение.
     */
    public void setPicked(Double parameter) {
        this.picked = parameter;
        repaint();
    }

    public void setAxes(String xTitle, String yTitle, boolean logX, boolean logY) {
        this.xTitle = xTitle;
        this.yTitle = yTitle;
        this.logX = logX;
        this.logY = logY;
    }

    /** Признак того, что ось ошибок сейчас в логарифмическом масштабе. */
    public boolean isLogY() {
        return logY;
    }

    /** Переключает ось ошибок между логарифмическим и линейным масштабом. */
    public void setLogY(boolean logY) {
        this.logY = logY;
        repaint();
    }

    public void setMessage(String message) {
        this.message = message;
        repaint();
    }

    public void add(Series s) {
        series.add(s);
        repaint();
    }

    /** Изображение графика для отчёта. */
    public BufferedImage image(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setFont(getFont() == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 11)
                : getFont().deriveFont(Font.PLAIN, 11f));
        draw(g, w, h);
        g.dispose();
        return img;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        draw(g, getWidth(), getHeight());
        g.dispose();
    }

    private void draw(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        boolean empty = true;
        for (Series s : series) {
            empty &= s.points.isEmpty();
        }
        if (empty) {
            g.setColor(Color.GRAY);
            g.drawString(message, 12, h / 2);
            return;
        }

        int left = 66;
        int right = w - 14;
        int top = 24;
        int bottom = h - 56;

        double xlo = Double.MAX_VALUE;
        double xhi = -Double.MAX_VALUE;
        double ylo = Double.MAX_VALUE;
        double yhi = -Double.MAX_VALUE;
        for (Series s : series) {
            for (double[] p : s.points) {
                xlo = Math.min(xlo, p[0]);
                xhi = Math.max(xhi, p[0]);
                if (!logY || p[1] > 0) {
                    ylo = Math.min(ylo, p[1]);
                    yhi = Math.max(yhi, p[1]);
                }
            }
        }
        if (ylo > yhi) {
            ylo = 0;
            yhi = 1;
        }
        if (xhi - xlo < 1e-12) {
            xhi = xlo + 1;
        }
        if (logY) {
            ylo = Math.max(ylo, 1e-12);
            yhi = Math.max(yhi, ylo * 10);
            ylo = Math.pow(10, Math.floor(Math.log10(ylo)));
            yhi = Math.pow(10, Math.ceil(Math.log10(yhi)));
        } else {
            double pad = (yhi - ylo) * 0.1 + 1e-12;
            ylo -= pad;
            yhi += pad;
        }

        // оси и сетка
        g.setColor(GRID);
        for (int k = 1; k < 5; k++) {
            int y = top + (bottom - top) * k / 5;
            g.draw(new Line2D.Double(left, y, right, y));
        }
        g.setColor(AXIS);
        g.draw(new Line2D.Double(left, top, left, bottom));
        g.draw(new Line2D.Double(left, bottom, right, bottom));

        g.setColor(Color.DARK_GRAY);
        for (int k = 0; k <= 5; k++) {
            int y = top + (bottom - top) * k / 5;
            double v = logY
                    ? Math.pow(10, Math.log10(yhi) - (Math.log10(yhi) - Math.log10(ylo)) * k / 5)
                    : yhi - (yhi - ylo) * k / 5;
            g.drawString(tick(v), 4, y + 4);
        }
        g.drawString(yTitle, 4, top - 9);
        g.drawString(xTitle, (left + right) / 2 - 10, bottom + 30);
        // деления по оси абсцисс ставим в самих точках: их немного,
        // и обучающемуся важно видеть, какому значению параметра отвечает точка
        java.util.TreeSet<Double> xs = new java.util.TreeSet<>();
        for (Series s : series) {
            for (double[] p : s.points) {
                xs.add(p[0]);
            }
        }
        if (xs.size() <= 8) {
            for (double v : xs) {
                double x = xOf(v, xlo, xhi, left, right);
                g.setColor(AXIS);
                g.draw(new Line2D.Double(x, bottom, x, bottom + 3));
                g.setColor(Color.DARK_GRAY);
                String s = tick(v);
                g.drawString(s, (float) (x - g.getFontMetrics().stringWidth(s) / 2.0),
                        (float) (bottom + 16));
            }
        } else {
            g.drawString(tick(xlo), left - 8, bottom + 16);
            g.drawString(tick(xhi), right - 24, bottom + 16);
        }

        // ряды
        for (Series s : series) {
            g.setColor(s.color);
            g.setStroke(new BasicStroke(1.6f));
            double px = 0;
            double py = 0;
            boolean first = true;
            for (int i = 0; i < s.points.size(); i++) {
                double[] p = s.points.get(i);
                double x = xOf(p[0], xlo, xhi, left, right);
                double y = yOf(p[1], ylo, yhi, top, bottom);
                if (!first) {
                    g.draw(new Line2D.Double(px, py, x, y));
                }
                px = x;
                py = y;
                first = false;
            }
            g.setStroke(new BasicStroke(1f));
            for (int i = 0; i < s.points.size(); i++) {
                double[] p = s.points.get(i);
                double x = xOf(p[0], xlo, xhi, left, right);
                double y = yOf(p[1], ylo, yhi, top, bottom);
                boolean bad = Boolean.TRUE.equals(s.suspicious.get(i));
                g.setColor(bad ? SUSPECT : s.color);
                double r = bad ? 5 : 3.2;
                g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
            }
        }

        // выбранная строка таблицы: вертикальная черта и кольца на точках
        if (picked != null) {
            double x = xOf(picked, xlo, xhi, left, right);
            g.setColor(PICKED);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[] {3f, 3f}, 0f));
            g.draw(new Line2D.Double(x, top, x, bottom));
            g.setStroke(new BasicStroke(1.6f));
            for (Series s : series) {
                for (double[] p : s.points) {
                    if (Math.abs(p[0] - picked) < 1e-9) {
                        double y = yOf(p[1], ylo, yhi, top, bottom);
                        g.setColor(s.color);
                        g.draw(new Ellipse2D.Double(x - 6, y - 6, 12, 12));
                    }
                }
            }
            g.setStroke(new BasicStroke(1f));
        }

        // наименьшая полная ошибка: тонкий кружок, виден всегда
        if (best != null && !best.points.isEmpty()) {
            int at = 0;
            for (int i = 1; i < best.points.size(); i++) {
                if (best.points.get(i)[1] < best.points.get(at)[1]) {
                    at = i;
                }
            }
            double[] p = best.points.get(at);
            double x = xOf(p[0], xlo, xhi, left, right);
            double y = yOf(p[1], ylo, yhi, top, bottom);
            g.setColor(BEST);
            g.draw(new Ellipse2D.Double(x - 9, y - 9, 18, 18));
            String s = "минимум";
            g.drawString(s, (float) (x - g.getFontMetrics().stringWidth(s) / 2.0),
                    (float) (y - 13));
        }

        // условные обозначения
        int lx = left;
        int ly = bottom + 46;
        for (Series s : series) {
            g.setColor(s.color);
            g.fill(new Ellipse2D.Double(lx, ly - 4, 6, 6));
            g.setColor(Color.DARK_GRAY);
            g.drawString(s.title, lx + 10, ly + 2);
            lx += 20 + g.getFontMetrics().stringWidth(s.title) + 14;
        }
    }

    private double xOf(double v, double lo, double hi, int left, int right) {
        if (logX && lo > 0 && v > 0) {
            double a = Math.log10(lo);
            double b = Math.log10(hi);
            return left + (right - left) * (Math.log10(v) - a) / (b - a);
        }
        return left + (right - left) * (v - lo) / (hi - lo);
    }

    private double yOf(double v, double lo, double hi, int top, int bottom) {
        if (logY) {
            double a = Math.log10(Math.max(lo, 1e-12));
            double b = Math.log10(Math.max(hi, 1e-12));
            double x = Math.log10(Math.max(v, 1e-12));
            return bottom - (bottom - top) * (x - a) / (b - a);
        }
        return bottom - (bottom - top) * (v - lo) / (hi - lo);
    }

    private static String tick(double v) {
        double a = Math.abs(v);
        if (a != 0 && (a >= 1e4 || a < 1e-2)) {
            return String.format(Locale.ROOT, "%.0e", v);
        }
        if (v == Math.rint(v)) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        return String.format(Locale.ROOT, "%.3g", v);
    }
}
