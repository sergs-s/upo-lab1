package ru.vka.upo.ui;

import java.util.Locale;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import javax.swing.JPanel;
import ru.vka.upo.core.Trajectory;
import ru.vka.upo.model.InputData;

/**
 * Чертёж пролёта объекта над измерительным пунктом.
 *
 * Показан не весь земной шар, а только тот его участок, над которым идёт
 * объект: иначе интересующая нас часть чертежа занимала бы несколько точек
 * в углу рисунка. Высота орбиты отложена в условном масштабе – при
 * действительном соотношении (высота в несколько сотен километров против
 * радиуса Земли в 6371 км) объект слился бы с поверхностью. Углы же
 * отложены верно, в том же отношении, в каком они изменяются на самом деле.
 *
 * Рисунок построен в плоскости большого круга, проходящей через центр Земли,
 * пункт и объект: в этой плоскости отрезок между пунктом и объектом и есть
 * наклонная дальность.
 *
 * Для бортового измерителя чертёж строится в относительном движении:
 * измеритель неподвижен, объект проходит мимо него по прямой с постоянной
 * относительной скоростью V0, наименьшее (траверзное) расстояние TR
 * достигается в середине интервала измерений. Масштаб по обеим осям
 * одинаковый, пока траверзное расстояние на рисунке различимо; иначе оно
 * откладывается в условном масштабе, о чём говорит подпись.
 */
public class GeometryView extends JPanel {

    private static final Color SKY = new Color(0xF7, 0xF9, 0xFC);
    private static final Color EARTH = new Color(0xC8, 0xDD, 0xF0);
    private static final Color EARTH_EDGE = new Color(0x7F, 0x9C, 0xB8);
    private static final Color ORBIT = new Color(0x77, 0x88, 0x99);
    private static final Color RANGE_LINE = new Color(0xC0, 0x39, 0x2B);
    private static final Color PAST_LINE = new Color(0xAA, 0xB4, 0xBE);
    private static final Color OBJECT = new Color(0x1F, 0x4E, 0x79);

    private InputData data;
    private Trajectory trajectory;
    private double currentTime;

    public GeometryView() {
        setBackground(SKY);
        setPreferredSize(new Dimension(360, 300));
    }

    /** Задаёт обстановку и момент времени, для которого показано положение. */
    public void show(InputData data, Trajectory trajectory, double currentTime) {
        this.data = data;
        this.trajectory = trajectory;
        this.currentTime = currentTime;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));

        if (trajectory instanceof Trajectory.Relative) {
            paintRelative(g, (Trajectory.Relative) trajectory);
            g.dispose();
            return;
        }
        if (!(trajectory instanceof Trajectory.Orbital)) {
            g.setColor(Color.GRAY);
            g.drawString("Чертёж появится после расчёта", 12, getHeight() / 2);
            g.dispose();
            return;
        }
        Trajectory.Orbital orb = (Trajectory.Orbital) trajectory;

        int w = getWidth();
        int h = getHeight();
        int left = 12;
        int right = w - 12;
        int top = 20;
        int bottom = h - 44;

        // Поверхность Земли: дуга с заметным, но небольшим прогибом.
        double halfWidth = (right - left) / 2.0;
        double sag = Math.max(16, Math.min(30, halfWidth * 0.12));
        double surfaceRadius = (halfWidth * halfWidth + sag * sag) / (2 * sag);
        double px = (left + right) / 2.0;
        double py = bottom - 14;                // пункт на поверхности
        double cy = py + surfaceRadius;         // центр дуги, далеко внизу
        double edgeAngle = Math.asin(Math.min(1.0, halfWidth / surfaceRadius));

        // Орбита: высота в условном масштабе, форма дуги та же.
        double orbitHeight = Math.max(60, (py - top) * 0.62);
        double orbitRadius = surfaceRadius + orbitHeight;

        // Наибольший центральный угол на интервале измерений задаёт масштаб
        // углов: весь интервал занимает почти всю ширину рисунка.
        double tk = data.getInterval();
        double gammaMax = Math.max(Math.max(orb.centralAngle(0), orb.centralAngle(tk)),
                orb.minCentralAngle());
        if (gammaMax < 1e-9) {
            gammaMax = 1e-9;
        }
        double scale = edgeAngle / (gammaMax * 1.35);

        // Земля
        Area earth = new Area(new Ellipse2D.Double(px - surfaceRadius, cy - surfaceRadius,
                2 * surfaceRadius, 2 * surfaceRadius));
        earth.intersect(new Area(new Rectangle2D.Double(0, 0, w, h)));
        g.setColor(EARTH);
        g.fill(earth);
        g.setColor(EARTH_EDGE);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(earth);
        g.setStroke(new BasicStroke(1f));

        // Дуга орбиты на интервале измерений
        double aStart = signed(orb, 0.0) * scale;
        double aEnd = signed(orb, tk) * scale;
        double from = Math.toDegrees(Math.min(aStart, aEnd));
        double to = Math.toDegrees(Math.max(aStart, aEnd));
        g.setColor(ORBIT);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(new Arc2D.Double(px - orbitRadius, cy - orbitRadius,
                2 * orbitRadius, 2 * orbitRadius, 90 - to, to - from, Arc2D.OPEN));
        g.setStroke(new BasicStroke(1f));

        // Лучи дальности: тонкие серые к началу, траверзу и концу интервала,
        // жирный красный к текущему положению
        double[] marks = {0.0, orb.closestApproachTime(), tk};
        String[] titles = {"начало", "траверз", "конец"};
        for (int i = 0; i < marks.length; i++) {
            double a = signed(orb, marks[i]) * scale;
            double ox = px + orbitRadius * Math.sin(a);
            double oy = cy - orbitRadius * Math.cos(a);
            g.setColor(PAST_LINE);
            g.draw(new Line2D.Double(px, py, ox, oy));
            g.setColor(ORBIT);
            g.fill(new Ellipse2D.Double(ox - 2.5, oy - 2.5, 5, 5));
            g.setColor(Color.GRAY);
            int tw = g.getFontMetrics().stringWidth(titles[i]);
            double tx = Math.max(left, Math.min(ox - tw / 2.0, right - tw));
            g.drawString(titles[i], (float) tx, (float) (oy - 8));
        }

        double aNow = signed(orb, currentTime) * scale;
        double ox = px + orbitRadius * Math.sin(aNow);
        double oy = cy - orbitRadius * Math.cos(aNow);
        g.setColor(RANGE_LINE);
        g.setStroke(new BasicStroke(2.0f));
        g.draw(new Line2D.Double(px, py, ox, oy));
        g.setStroke(new BasicStroke(1f));

        g.setColor(OBJECT);
        g.fill(new Ellipse2D.Double(ox - 5, oy - 5, 10, 10));
        g.fill(new Ellipse2D.Double(px - 4, py - 4, 8, 8));

        // Подписи
        double range = trajectory.range(currentTime);
        g.setColor(RANGE_LINE);
        g.drawString(String.format(Locale.ROOT, "R = %.1f км", range / 1000.0),
                (float) ((px + ox) / 2 + 8), (float) ((py + oy) / 2));
        g.setColor(OBJECT);
        g.drawString("объект", (float) (ox + 9), (float) (oy + 15));
        g.setColor(Color.DARK_GRAY);
        g.drawString("пункт", (float) (px + 9), (float) (py + 4));

        g.setColor(Color.GRAY);
        drawFitted(g, String.format(Locale.ROOT, "H = %.0f км, до трассы %.0f км, t = %.1f с",
                data.getOrbitHeight(), data.getTrackDistance(), currentTime), 8, h - 18, w - 16);
        drawFitted(g, "высота орбиты показана в условном масштабе, углы – в верном",
                8, h - 5, w - 16);
        g.dispose();
    }

    /**
     * Чертёж для бортового измерителя: относительное движение объекта
     * мимо неподвижного измерителя.
     */
    private void paintRelative(Graphics2D g, Trajectory.Relative rel) {
        int w = getWidth();
        int h = getHeight();
        int left = 24;
        int right = w - 24;
        int top = 34;
        int bottom = h - 44;

        double tk = data.getInterval();
        double tr = data.getTraverseDistance() * 1000.0;   // м
        double v0 = data.getRelativeSpeed();               // м/с
        double tc = rel.closestApproachTime();

        // положение объекта вдоль прямой относительного движения, м
        double xStart = v0 * (0.0 - tc);
        double xEnd = v0 * (tk - tc);
        double span = Math.max(Math.abs(xStart), Math.abs(xEnd));
        if (span < 1e-9) {
            span = Math.max(tr, 1.0);
        }

        // измеритель внизу посередине, прямая движения объекта выше него
        double px = (left + right) / 2.0;
        double py = bottom - 16;
        double sx = (right - left) / 2.0 / span;
        double sy = (py - top - 18) / Math.max(tr, 1e-9);
        double scale = Math.min(sx, sy);
        double vy = tr * scale;
        boolean conventional = false;
        if (vy < 40) {
            // траверзное расстояние мало по сравнению с пройденным путём:
            // откладываем его в условном масштабе, чтобы рисунок читался
            vy = Math.max(40, (py - top - 18) * 0.5);
            conventional = true;
        }
        double ly = py - vy;                               // прямая движения

        // прямая относительного движения на интервале измерений
        double ax = px + xStart * scale;
        double bx = px + xEnd * scale;
        g.setColor(ORBIT);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {6f, 4f}, 0f));
        g.draw(new Line2D.Double(ax, ly, bx, ly));
        g.setStroke(new BasicStroke(1f));

        // траверзное расстояние: перпендикуляр из измерителя на прямую
        double cx = px;
        g.setColor(PAST_LINE);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[] {3f, 3f}, 0f));
        g.draw(new Line2D.Double(cx, py, cx, ly));
        g.setStroke(new BasicStroke(1f));
        // текущее положение объекта: от него зависит, с какой стороны
        // перпендикуляра подписывать траверзное расстояние и дальность,
        // чтобы подписи не наезжали друг на друга
        double ox = px + v0 * (currentTime - tc) * scale;
        boolean objectRight = ox >= cx;
        String trText = String.format(Locale.ROOT, "TR = %s км", trim(tr / 1000.0));
        int trw = g.getFontMetrics().stringWidth(trText);
        g.setColor(Color.GRAY);
        g.drawString(trText, (float) (objectRight ? cx - trw - 5 : cx + 5),
                (float) ((py + ly) / 2 - 8));

        // отметки начала, траверза и конца интервала; подпись отметки
        // пропускается, если наезжает на уже выведенную (при малом пути
        // объекта за интервал отметки почти сливаются)
        double[] marks = {tc, 0.0, tk};
        String[] titles = {"траверз", "начало", "конец"};
        java.util.List<double[]> placed = new java.util.ArrayList<double[]>();
        for (int i = 0; i < marks.length; i++) {
            double mx = px + v0 * (marks[i] - tc) * scale;
            g.setColor(PAST_LINE);
            g.draw(new Line2D.Double(px, py, mx, ly));
            g.setColor(ORBIT);
            g.fill(new Ellipse2D.Double(mx - 2.5, ly - 2.5, 5, 5));
            int tw = g.getFontMetrics().stringWidth(titles[i]);
            double tx = Math.max(4, Math.min(mx - tw / 2.0, w - 4 - tw));
            boolean free = true;
            for (double[] b : placed) {
                if (tx < b[1] + 4 && tx + tw > b[0] - 4) {
                    free = false;
                }
            }
            if (free) {
                g.setColor(Color.GRAY);
                g.drawString(titles[i], (float) tx, (float) (ly - 12));
                placed.add(new double[] {tx, tx + tw});
            }
        }

        // луч дальности к текущему положению
        g.setColor(RANGE_LINE);
        g.setStroke(new BasicStroke(2.0f));
        g.draw(new Line2D.Double(px, py, ox, ly));
        g.setStroke(new BasicStroke(1f));
        double range = trajectory.range(currentTime);
        String rText = String.format(Locale.ROOT, "R = %.2f км", range / 1000.0);
        int rw = g.getFontMetrics().stringWidth(rText);
        double rx = (px + ox) / 2 + (objectRight ? 8 : -rw - 8);
        rx = Math.max(4, Math.min(rx, w - 4 - rw));
        g.drawString(rText, (float) rx, (float) ((py + ly) / 2 + 16));

        // объект и измеритель: силуэты самолётов, объект летит вдоль прямой
        drawAircraft(g, ox, ly, 11, v0 >= 0 ? 0.0 : Math.PI, OBJECT);
        drawAircraft(g, px, py, 9, -Math.PI / 2, Color.DARK_GRAY);
        g.setColor(OBJECT);
        g.drawString("объект", (float) Math.min(ox + 12, w - 44), (float) (ly + 18));
        g.setColor(Color.DARK_GRAY);
        g.drawString("измеритель", (float) (px + 12), (float) (py + 4));

        // направление относительной скорости
        double arrowY = Math.max(14, ly - 34);
        double a0 = px - 30;
        double a1 = px + 30;
        g.setColor(ORBIT);
        g.draw(new Line2D.Double(a0, arrowY, a1, arrowY));
        g.fill(arrowHead(v0 >= 0 ? a1 : a0, arrowY, v0 >= 0 ? 0.0 : Math.PI));
        g.setColor(Color.GRAY);
        g.drawString(String.format(Locale.ROOT, "V0 = %s м/с", trim(v0)),
                (float) (a1 + 8), (float) (arrowY + 4));

        g.setColor(Color.GRAY);
        drawFitted(g, String.format(Locale.ROOT,
                "TR = %s км, V0 = %s м/с, t = %.1f с",
                trim(tr / 1000.0), trim(v0), currentTime), 8, h - 18, w - 16);
        drawFitted(g, conventional
                ? "относительное движение; TR показано в условном масштабе"
                : "относительное движение, масштаб по осям одинаковый",
                8, h - 5, w - 16);
    }

    /**
     * Силуэт самолёта (вид сверху) с центром в точке (x, y).
     *
     * @param size  половина длины фюзеляжа, пикселей
     * @param angle направление полёта, рад (0 – вправо, −π/2 – вверх)
     */
    private static void drawAircraft(Graphics2D g, double x, double y, double size,
            double angle, Color color) {
        double k = size / 10.0;
        Path2D.Double p = new Path2D.Double();
        // фюзеляж с носом вправо (по оси x), крылья и стабилизатор
        p.moveTo(10, 0);
        p.lineTo(7, -1.4);
        p.lineTo(1.5, -1.4);
        p.lineTo(-2.5, -9);
        p.lineTo(-4.5, -9);
        p.lineTo(-2, -1.4);
        p.lineTo(-7, -1.4);
        p.lineTo(-9, -4.5);
        p.lineTo(-10.5, -4.5);
        p.lineTo(-9.5, 0);
        p.lineTo(-10.5, 4.5);
        p.lineTo(-9, 4.5);
        p.lineTo(-7, 1.4);
        p.lineTo(-2, 1.4);
        p.lineTo(-4.5, 9);
        p.lineTo(-2.5, 9);
        p.lineTo(1.5, 1.4);
        p.lineTo(7, 1.4);
        p.closePath();
        AffineTransform t = new AffineTransform();
        t.translate(x, y);
        t.rotate(angle);
        t.scale(k, k);
        g.setColor(color);
        g.fill(t.createTransformedShape(p));
    }

    /** Наконечник стрелки в точке (x, y), направленный под углом angle. */
    private static java.awt.Shape arrowHead(double x, double y, double angle) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(0, 0);
        p.lineTo(-7, -3.5);
        p.lineTo(-7, 3.5);
        p.closePath();
        AffineTransform t = new AffineTransform();
        t.translate(x, y);
        t.rotate(angle);
        return t.createTransformedShape(p);
    }

    /**
     * Выводит строку, при нехватке места уменьшая шрифт: подпись не должна
     * выходить за поле рисунка.
     */
    private static void drawFitted(Graphics2D g, String text, int x, int y, int maxWidth) {
        Font f = g.getFont();
        Font use = f;
        float size = f.getSize2D();
        while (size > 7f && g.getFontMetrics(use).stringWidth(text) > maxWidth) {
            size -= 0.5f;
            use = f.deriveFont(size);
        }
        g.setFont(use);
        g.drawString(text, x, y);
        g.setFont(f);
    }

    /** Число без лишних нулей. */
    private static String trim(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.ROOT, "%.3g", v);
    }

    /** Центральный угол со знаком: до траверза объект слева, после – справа. */
    private static double signed(Trajectory.Orbital orb, double t) {
        double gamma = orb.centralAngle(t);
        return t < orb.closestApproachTime() ? -gamma : gamma;
    }
}
