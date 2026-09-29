package ru.vka.upo.ui;

import java.util.Locale;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
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
 * При бортовом измерителе чертёж тот же: измеритель и объект лишь меняются
 * местами – измеритель на КА (точка на орбите), объект на поверхности.
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
        // при бортовом измерителе он и объект меняются местами: измеритель
        // на КА (точка на орбите), объект – на поверхности Земли
        boolean airborne = data.getMeasurer() == InputData.Measurer.AIRBORNE;
        g.setColor(OBJECT);
        g.drawString(airborne ? "измеритель" : "объект", (float) (ox + 9), (float) (oy + 15));
        g.setColor(Color.DARK_GRAY);
        g.drawString(airborne ? "объект" : "пункт", (float) (px + 9), (float) (py + 4));

        g.setColor(Color.GRAY);
        drawFitted(g, String.format(Locale.ROOT, "H = %.0f км, до трассы %.0f км, t = %.1f с",
                data.getOrbitHeight(), data.getTrackDistance(), currentTime), 8, h - 18, w - 16);
        drawFitted(g, "высота орбиты показана в условном масштабе, углы – в верном",
                8, h - 5, w - 16);
        g.dispose();
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

    /** Центральный угол со знаком: до траверза объект слева, после – справа. */
    private static double signed(Trajectory.Orbital orb, double t) {
        double gamma = orb.centralAngle(t);
        return t < orb.closestApproachTime() ? -gamma : gamma;
    }
}
