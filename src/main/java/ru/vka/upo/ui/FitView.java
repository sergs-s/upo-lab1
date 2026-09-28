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
import java.awt.geom.Path2D;
import javax.swing.JPanel;
import ru.vka.upo.core.Realization;
import ru.vka.upo.model.InputData;

/**
 * Ход дальности на интервале усреднения: истинная кривая, отсчёты с шумом,
 * проведённый по ним аппроксимирующий полином и невязки.
 *
 * Именно этот рисунок объясняет обе составляющие ошибки. Расхождение
 * полинома с истинной кривой в момент привязки – составляющая динамическая,
 * она есть и без всякого шума. Разброс полинома от опыта к опыту при том же
 * шуме – составляющая случайная; её видно, если несколько раз сменить
 * реализацию шума.
 */
public class FitView extends JPanel {

    private static final Color BG = new Color(0xFF, 0xFF, 0xFF);
    private static final Color AXIS = new Color(0x88, 0x88, 0x88);
    private static final Color GRID = new Color(0xEE, 0xEE, 0xEE);
    private static final Color TRUE_CURVE = new Color(0x9E, 0xC3, 0xE0);
    private static final Color POINTS = new Color(0x33, 0x33, 0x33);
    private static final Color FIT_CURVE = new Color(0xC0, 0x39, 0x2B);
    private static final Color RESIDUAL = new Color(0xBB, 0xBB, 0xBB);
    private static final Color ANCHOR = new Color(0x2E, 0x86, 0x4B);

    private Realization realization;

    public FitView() {
        setBackground(BG);
        setPreferredSize(new Dimension(460, 300));
    }

    public void show(Realization r) {
        this.realization = r;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));

        Realization r = realization;
        if (r == null) {
            g.setColor(Color.GRAY);
            g.drawString("График появится после расчёта", 12, getHeight() / 2);
            g.dispose();
            return;
        }
        if (r.getData().getMeasured() == InputData.Measured.VELOCITY) {
            // рисунок показывает измерения дальности и проведённый по ним
            // полином; при измерении одной скорости таких измерений нет
            g.setColor(Color.GRAY);
            int y = getHeight() / 2 - 8;
            g.drawString("Измеряется только радиальная скорость:", 12, y);
            g.drawString("измерений дальности нет, рисунок не строится", 12, y + 16);
            g.dispose();
            return;
        }

        int left = 62;
        int right = getWidth() - 12;
        int top = 26;
        int bottom = getHeight() - 48;

        double[] t = r.getTime();
        double[] truth = r.getTruth();
        double[] meas = r.getMeasured();
        double[] fit = r.getFitted();

        double t0 = r.getWindowStart();
        double t1 = Math.max(r.getWindowEnd(), t0 + 1e-9);
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (int i = 0; i < t.length; i++) {
            lo = Math.min(lo, Math.min(truth[i], Math.min(meas[i], fit[i])));
            hi = Math.max(hi, Math.max(truth[i], Math.max(meas[i], fit[i])));
        }
        if (hi - lo < 1e-6) {
            hi = lo + 1;
        }
        double pad = (hi - lo) * 0.12;
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
        for (int k = 0; k <= 4; k++) {
            double v = hi - (hi - lo) * k / 4;
            int y = top + (bottom - top) * k / 4;
            g.drawString(String.format(Locale.ROOT, "%.1f", v / 1000.0), 6, y + 4);
        }
        g.drawString("R, км", 6, top - 9);
        g.drawString(String.format(Locale.ROOT, "%.2f", t0), left - 10, bottom + 15);
        g.drawString(String.format(Locale.ROOT, "%.2f", t1), right - 26, bottom + 15);
        g.drawString("t, с", (left + right) / 2, bottom + 15);

        // истинная кривая: считается чаще, чем идут измерения
        Path2D.Double truePath = new Path2D.Double();
        int steps = Math.max(120, t.length * 3);
        for (int i = 0; i <= steps; i++) {
            double tt = t0 + (t1 - t0) * i / steps;
            double x = xOf(tt, t0, t1, left, right);
            double y = yOf(r.getTrajectory().range(tt), lo, hi, top, bottom);
            if (i == 0) {
                truePath.moveTo(x, y);
            } else {
                truePath.lineTo(x, y);
            }
        }
        // истинную дальность чертим широкой светлой лентой, а полином –
        // тонкой линией поверх неё: иначе при хорошем приближении одна кривая
        // полностью закрывает другую и рисунок ничего не объясняет
        g.setColor(TRUE_CURVE);
        g.setStroke(new BasicStroke(5.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(truePath);

        // аппроксимирующий полином
        Path2D.Double fitPath = new Path2D.Double();
        for (int i = 0; i <= steps; i++) {
            double tt = t0 + (t1 - t0) * i / steps;
            double x = xOf(tt, t0, t1, left, right);
            double y = yOf(r.value(tt), lo, hi, top, bottom);
            if (i == 0) {
                fitPath.moveTo(x, y);
            } else {
                fitPath.lineTo(x, y);
            }
        }
        g.setColor(FIT_CURVE);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(fitPath);
        g.setStroke(new BasicStroke(1f));

        // невязки и отсчёты
        for (int i = 0; i < t.length; i++) {
            double x = xOf(t[i], t0, t1, left, right);
            double ym = yOf(meas[i], lo, hi, top, bottom);
            double yf = yOf(fit[i], lo, hi, top, bottom);
            g.setColor(RESIDUAL);
            g.draw(new Line2D.Double(x, ym, x, yf));
            g.setColor(POINTS);
            g.fill(new Ellipse2D.Double(x - 2, ym - 2, 4, 4));
        }

        // момент привязки
        double xa = xOf(r.getAnchorTime(), t0, t1, left, right);
        g.setColor(ANCHOR);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {3f, 3f}, 0f));
        g.draw(new Line2D.Double(xa, top, xa, bottom));
        g.setStroke(new BasicStroke(1f));
        g.drawString("привязка", (float) Math.min(xa + 4, right - 56), (float) (top + 11));

        // условные обозначения: сдвигаем по действительной ширине надписей,
        // иначе они наезжают друг на друга
        int[] pen = {left, bottom + 28};
        ErrorView.legend(g, pen, right, TRUE_CURVE, "истинная дальность");
        ErrorView.legend(g, pen, right, FIT_CURVE, "полином степени "
                + Math.min(r.getData().getDegree(), r.getTime().length - 1));
        ErrorView.legend(g, pen, right, POINTS, "измерения");
        g.dispose();
    }

    private static double xOf(double t, double t0, double t1, int left, int right) {
        return left + (right - left) * (t - t0) / (t1 - t0);
    }

    private static double yOf(double v, double lo, double hi, int top, int bottom) {
        return bottom - (bottom - top) * (v - lo) / (hi - lo);
    }

}
