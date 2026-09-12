package ru.vka.upo.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.Locale;
import javax.swing.JPanel;
import ru.vka.upo.core.ErrorProfile;

/**
 * Ход ошибок оценивания по интервалу усреднения.
 *
 * Сплошная линия – динамическая ошибка Δ(t) = r(t) – r̂(t) при отсутствии
 * шумов измерений, светлая полоса – коридор ±σ(t) случайной составляющей.
 * В момент привязки эти величины равны ERD и ERS из выбранной строки
 * таблицы: рисунок показывает, откуда берутся оба числа и как они менялись
 * бы при переносе момента привязки внутри выборки.
 */
public class ErrorView extends JPanel {

    private static final Color BG = new Color(0xFF, 0xFF, 0xFF);
    private static final Color AXIS = new Color(0x88, 0x88, 0x88);
    private static final Color GRID = new Color(0xEE, 0xEE, 0xEE);
    private static final Color BAND = new Color(0xDF, 0xEC, 0xF6);
    private static final Color BAND_EDGE = new Color(0x9E, 0xC3, 0xE0);
    private static final Color CURVE = new Color(0x2F, 0x6F, 0xA8);
    private static final Color ANCHOR = new Color(0x2E, 0x86, 0x4B);
    private static final Color ZERO = new Color(0x66, 0x66, 0x66);

    private ErrorProfile profile;

    public ErrorView() {
        setBackground(BG);
        setPreferredSize(new Dimension(460, 300));
    }

    public void show(ErrorProfile p) {
        this.profile = p;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));

        ErrorProfile p = profile;
        if (p == null) {
            g.setColor(Color.GRAY);
            g.drawString("График появится после расчёта", 12, getHeight() / 2);
            g.dispose();
            return;
        }

        int left = 62;
        int right = getWidth() - 12;
        int top = 26;
        int bottom = getHeight() - 48;

        double t0 = p.getWindowStart();
        double t1 = Math.max(p.getWindowEnd(), t0 + 1e-9);

        // масштаб по вертикали: обе составляющие должны быть видны целиком.
        // Границы берутся по факту, а не симметрично: при знакопостоянной
        // динамической ошибке симметричная шкала оставила бы полрисунка пустым
        int steps = 240;
        double lo = 0;
        double hi = 0;
        for (int i = 0; i <= steps; i++) {
            double t = t0 + (t1 - t0) * i / steps;
            double dyn = p.dynamic(t);
            double sig = p.random(t);
            lo = Math.min(lo, Math.min(dyn, -sig));
            hi = Math.max(hi, Math.max(dyn, sig));
        }
        if (hi - lo < 1e-12) {
            hi = lo + 1e-12;
        }
        double pad = (hi - lo) * 0.08;
        lo -= pad;
        hi += pad;

        // сетка и оси
        g.setColor(GRID);
        for (int k = 1; k < 4; k++) {
            int y = top + (bottom - top) * k / 4;
            g.draw(new Line2D.Double(left, y, right, y));
        }
        g.setColor(AXIS);
        g.draw(new Line2D.Double(left, top, left, bottom));
        g.draw(new Line2D.Double(left, bottom, right, bottom));

        g.setColor(Color.DARK_GRAY);
        // формат подписей выбирается один раз по наибольшему делению: иначе
        // на одной оси соседствуют «8e+04» и «6627»
        String format = format(Math.max(Math.abs(lo), Math.abs(hi)));
        for (int k = 0; k <= 4; k++) {
            double v = hi - (hi - lo) * k / 4;
            int y = top + (bottom - top) * k / 4;
            g.drawString(String.format(Locale.ROOT, format, v), 6, y + 4);
        }
        g.drawString("ошибка, м", 6, top - 9);
        String note = String.format(Locale.ROOT,
                "в привязке: |Δ| = %s м, σ = %s м",
                fmt(Math.abs(p.dynamic(p.getAnchorTime()))), fmt(p.random(p.getAnchorTime())));
        int nw = g.getFontMetrics().stringWidth(note);
        g.drawString(note, Math.max(left + 4, right - nw), top - 9);
        g.drawString(String.format(Locale.ROOT, "%.2f", t0), left - 10, bottom + 15);
        g.drawString(String.format(Locale.ROOT, "%.2f", t1), right - 26, bottom + 15);
        g.drawString("t, с", (left + right) / 2, bottom + 15);

        // коридор случайной ошибки ±σ(t)
        Path2D.Double band = new Path2D.Double();
        for (int i = 0; i <= steps; i++) {
            double t = t0 + (t1 - t0) * i / steps;
            double x = xOf(t, t0, t1, left, right);
            double y = yOf(p.random(t), lo, hi, top, bottom);
            if (i == 0) {
                band.moveTo(x, y);
            } else {
                band.lineTo(x, y);
            }
        }
        for (int i = steps; i >= 0; i--) {
            double t = t0 + (t1 - t0) * i / steps;
            band.lineTo(xOf(t, t0, t1, left, right), yOf(-p.random(t), lo, hi, top, bottom));
        }
        band.closePath();
        g.setColor(BAND);
        g.fill(band);
        g.setColor(BAND_EDGE);
        g.setStroke(new BasicStroke(1f));
        g.draw(band);

        // нулевая линия
        g.setColor(ZERO);
        double yz = yOf(0, lo, hi, top, bottom);
        g.draw(new Line2D.Double(left, yz, right, yz));

        // динамическая ошибка
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i <= steps; i++) {
            double t = t0 + (t1 - t0) * i / steps;
            double x = xOf(t, t0, t1, left, right);
            double y = yOf(p.dynamic(t), lo, hi, top, bottom);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        g.setColor(CURVE);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        g.setStroke(new BasicStroke(1f));

        // момент привязки: именно эти значения выведены в таблице
        double ta = p.getAnchorTime();
        double xa = xOf(ta, t0, t1, left, right);
        double ya = yOf(p.dynamic(ta), lo, hi, top, bottom);
        g.setColor(ANCHOR);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {3f, 3f}, 0f));
        g.draw(new Line2D.Double(xa, top, xa, bottom));
        g.setStroke(new BasicStroke(1f));
        g.fill(new Ellipse2D.Double(xa - 3.5, ya - 3.5, 7, 7));
        g.drawString("привязка", (float) Math.min(xa + 4, right - 56), (float) (top + 11));

        // условные обозначения
        int[] pen = {left, bottom + 28};
        legend(g, pen, right, CURVE, "Δ(t) – динамическая");
        legend(g, pen, right, BAND_EDGE, "±σ(t) – случайная");
        g.dispose();
    }

    /** Единый формат подписей оси, выбранный по наибольшему делению. */
    private static String format(double max) {
        if (max >= 1e4 || (max > 0 && max < 1e-2)) {
            return "%.0e";
        }
        if (max >= 100) {
            return "%.0f";
        }
        if (max >= 10) {
            return "%.1f";
        }
        return "%.2f";
    }

    /** Подпись отдельного значения: без лишних знаков. */
    private static String fmt(double v) {
        double a = Math.abs(v);
        if (a >= 1e4 || (a > 0 && a < 1e-2)) {
            return String.format(Locale.ROOT, "%.0e", v);
        }
        if (a >= 100) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        if (a >= 10) {
            return String.format(Locale.ROOT, "%.1f", v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static double xOf(double t, double t0, double t1, int left, int right) {
        return left + (right - left) * (t - t0) / (t1 - t0);
    }

    private static double yOf(double v, double lo, double hi, int top, int bottom) {
        return bottom - (bottom - top) * (v - lo) / (hi - lo);
    }

    /**
     * Рисует одно условное обозначение. Положение пера pen = {x, y}
     * подвигается вправо, а при нехватке места – на следующую строку:
     * иначе при узком окне подписи уходят за край рисунка.
     */
    static void legend(Graphics2D g, int[] pen, int right, Color color, String text) {
        int w = 10 + g.getFontMetrics().stringWidth(text);
        if (pen[0] > right - w) {
            pen[0] = 62;
            pen[1] += 14;
        }
        g.setColor(color);
        g.fill(new Ellipse2D.Double(pen[0], pen[1] - 4, 6, 6));
        g.setColor(Color.DARK_GRAY);
        g.drawString(text, pen[0] + 10, pen[1] + 2);
        pen[0] += w + 16;
    }
}
