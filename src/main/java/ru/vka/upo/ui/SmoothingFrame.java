package ru.vka.upo.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import ru.vka.upo.core.Trajectory;

/**
 * Наглядное окно «Сглаживание измерений дальности полиномом».
 *
 * Объект пролетает через зону видимости пункта (угол места не менее 7°),
 * за пролёт делается 60 измерений дальности с шумом. По каждым 10
 * измерениям методом наименьших квадратов строится полином первой
 * степени R*(t) = α0 + α1·t. Показ идёт по тактам: один такт – одно
 * измерение. Слева – пролёт объекта над пунктом, справа – ход дальности,
 * измерения и отрезки полиномов, внизу – таблица чисел.
 *
 * Исходные данные постоянны – вариант 1 таблицы вариантов; текущие
 * исходные данные программы окно не читает и не меняет. Расчёт – в
 * {@link SmoothingModel}.
 *
 * Поведение окна то же, что у {@link SummaryFrame}: один экземпляр,
 * вызвавшее окно на время показа недоступно.
 */
public class SmoothingFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Color SKY = new Color(0xF7, 0xF9, 0xFC);
    private static final Color EARTH = new Color(0xC8, 0xDD, 0xF0);
    private static final Color EARTH_EDGE = new Color(0x7F, 0x9C, 0xB8);
    private static final Color ORBIT = new Color(0x77, 0x88, 0x99);
    private static final Color ORBIT_FAINT = new Color(0xC4, 0xCC, 0xD4);
    private static final Color RANGE_LINE = new Color(0xC0, 0x39, 0x2B);
    private static final Color ZONE = new Color(0x2E, 0x86, 0x4B);
    private static final Color OBJECT = new Color(0x1F, 0x4E, 0x79);
    private static final Color TRUE_CURVE = new Color(0x1F, 0x4E, 0x79);
    private static final Color POINTS = new Color(0x55, 0x55, 0x55);
    private static final Color POLY = new Color(0xE6, 0x7E, 0x22);
    /** Величины, неизвестные измерителю: в таблице – красным. */
    private static final Color HIDDEN = new Color(0xC0, 0x39, 0x2B);
    private static final Color SEGMENT_BAND = new Color(0xEE, 0xF3, 0xF8);

    /** Скорость показа, тактов в секунду. */
    private static final double[] SPEEDS = {0.5, 1, 2, 4};
    private static final String[] SPEED_TITLES = {
        "0,5 такта/с", "1 такт/с", "2 такта/с", "4 такта/с"};

    /** Единственный открытый экземпляр окна – второй не заводится. */
    private static SmoothingFrame instance;

    private boolean fullScreenOwner;

    private SmoothingModel model;
    /** Сколько измерений уже сделано (0…60). */
    private int shown;
    private long seed = System.nanoTime();

    private final FlightView flight = new FlightView();
    private final RangeChart chart = new RangeChart();
    private final FormulaPanel formulas = new FormulaPanel();
    private final TableModel tableModel = new TableModel();
    private final JTable table = new JTable(tableModel);
    /** Строка СКО по участкам – отдельный компонент, легко убрать. */
    private final JLabel stats = new JLabel();
    private final JLabel counter = new JLabel();

    private final JButton btnStep = new JButton("Шаг");
    private final JButton btnStart = new JButton("Пуск");
    private final JButton btnPause = new JButton("Пауза");
    private final JButton btnReset = new JButton("Заново");
    private final JComboBox<String> cmbSpeed = new JComboBox<String>(SPEED_TITLES);
    private final Timer timer = new Timer(1000, e -> tick());

    private SmoothingFrame(Window owner) {
        super("Сглаживание измерений дальности полиномом");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        fullScreenOwner = isFullScreen(owner);

        JPanel chartBlock = new JPanel(new BorderLayout());
        chartBlock.add(chart, BorderLayout.CENTER);
        chartBlock.add(formulas, BorderLayout.SOUTH);
        chartBlock.setBorder(BorderFactory.createTitledBorder("Дальность: истинная, измерения, полином"));
        JPanel flightBlock = new JPanel(new BorderLayout());
        flightBlock.add(flight, BorderLayout.CENTER);
        flightBlock.setBorder(BorderFactory.createTitledBorder("Пролёт объекта над пунктом"));
        JPanel top = new JPanel(new GridLayout(1, 2, 8, 0));
        top.add(flightBlock);
        top.add(chartBlock);

        table.setRowHeight(20);
        table.setAutoCreateRowSorter(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new Renderer());
        table.setFocusable(false);
        table.setRowSelectionAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        stats.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(scroll, BorderLayout.CENTER);
        bottom.add(stats, BorderLayout.SOUTH);
        bottom.setBorder(BorderFactory.createTitledBorder(
                "Измерения и сглаживание (красным – то, чего измеритель не знает)"));

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 6, 0);
        c.gridy = 0;
        c.weighty = 0.6;
        center.add(top, c);
        c.gridy = 1;
        c.weighty = 0.4;
        c.insets = new Insets(0, 0, 0, 0);
        center.add(bottom, c);
        center.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        btnStep.addActionListener(e -> {
            timer.stop();
            tick();
        });
        btnStart.addActionListener(e -> {
            timer.setInitialDelay(0);
            timer.start();
            updateButtons();
        });
        btnPause.addActionListener(e -> {
            timer.stop();
            updateButtons();
        });
        btnReset.addActionListener(e -> reset());
        cmbSpeed.setSelectedIndex(1);
        cmbSpeed.addActionListener(e -> applySpeed());
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(btnStep);
        controls.add(btnStart);
        controls.add(btnPause);
        controls.add(btnReset);
        controls.add(new JLabel("   Скорость:"));
        controls.add(cmbSpeed);
        controls.add(new JLabel("   "));
        controls.add(counter);
        JPanel closeBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        closeBar.add(close);
        JPanel south = new JPanel(new BorderLayout());
        south.add(controls, BorderLayout.CENTER);
        south.add(closeBar, BorderLayout.EAST);

        setLayout(new BorderLayout());
        add(center, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1200, screen.width - 40), Math.min(850, screen.height - 60));
        setLocationRelativeTo(owner);
        Emblem.applyTo(this);

        // окно модальное: сворачивать его незачем (см. SummaryFrame)
        addWindowStateListener(e -> {
            if ((e.getNewState() & Frame.ICONIFIED) != 0) {
                setExtendedState(e.getOldState());
                JOptionPane.showMessageDialog(this,
                        "Это окно нельзя свернуть, можно только закрыть.",
                        "Предварительная обработка", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        if (fullScreenOwner) {
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        applySpeed();
        reset();
    }

    /** Открыто ли вызвавшее окно в полноэкранном режиме программы. */
    private static boolean isFullScreen(Window owner) {
        if (owner instanceof MainFrame) {
            return ((MainFrame) owner).isFullScreenMode();
        }
        return owner instanceof HelpFrame && ((HelpFrame) owner).isFullScreenOwner();
    }

    /**
     * Показывает окно. Если оно уже открыто, второй раз не создаётся –
     * открытое просто выводится на передний план.
     */
    public static void show(Component parent) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        if (parent instanceof Window) {
            owner = (Window) parent;
        }
        if (instance != null && instance.isDisplayable()) {
            instance.bringToFront();
            return;
        }
        final Window ownerFinal = owner;
        instance = new SmoothingFrame(owner);
        instance.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (instance != null) {
                    instance.timer.stop();
                }
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

    /** Вывод поверх владельца – как в {@link SummaryFrame}. */
    private void bringToFront() {
        setAlwaysOnTop(true);
        toFront();
        requestFocus();
        if (!fullScreenOwner) {
            setAlwaysOnTop(false);
        }
    }

    /** Окно без показа – для проверочных программ (снимки в BufferedImage). */
    static SmoothingFrame createHidden(long seed) {
        SmoothingFrame f = new SmoothingFrame(null);
        f.seed = seed;
        f.reset();
        return f;
    }

    /** Делает заданное число тактов без таймера – для проверочных программ. */
    void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick();
        }
    }

    /** К началу: новая реализация шума, показ не запускается. */
    private void reset() {
        timer.stop();
        model = new SmoothingModel(SmoothingModel.variantOne(), seed++);
        shown = 0;
        refresh();
    }

    /** Один такт – одно измерение. */
    private void tick() {
        if (shown >= SmoothingModel.COUNT) {
            timer.stop();
            updateButtons();
            return;
        }
        shown++;
        if (shown >= SmoothingModel.COUNT) {
            timer.stop();
        }
        refresh();
        int row = shown - 1;
        table.scrollRectToVisible(table.getCellRect(row, 0, true));
    }

    private void applySpeed() {
        int k = Math.max(0, cmbSpeed.getSelectedIndex());
        int delay = (int) Math.round(1000.0 / SPEEDS[k]);
        timer.setDelay(delay);
        timer.setInitialDelay(delay);
    }

    /** Число участков, по которым уже построен полином. */
    private int smoothed() {
        return shown / SmoothingModel.SEGMENT;
    }

    private void refresh() {
        tableModel.fireTableDataChanged();
        stats.setText(statsText());
        counter.setText("Измерений: " + shown + " из " + SmoothingModel.COUNT);
        formulas.update();
        flight.repaint();
        chart.repaint();
        updateButtons();
    }

    private void updateButtons() {
        boolean running = timer.isRunning();
        boolean done = shown >= SmoothingModel.COUNT;
        btnStep.setEnabled(!running && !done);
        btnStart.setEnabled(!running && !done);
        btnPause.setEnabled(running);
    }

    /** Строка СКО по участкам: небольшая таблица на шесть участков. */
    private String statsText() {
        StringBuilder sb = new StringBuilder("<html><table cellspacing=0 cellpadding=1><tr><td>"
                + "СКО по участку, м</td>");
        for (int s = 0; s < SmoothingModel.SEGMENTS; s++) {
            sb.append("<td align=right>&nbsp;&nbsp;участок ").append(s + 1).append("</td>");
        }
        sb.append("</tr><tr><td>полином − измеренная</td>");
        for (int s = 0; s < SmoothingModel.SEGMENTS; s++) {
            sb.append("<td align=right>").append(s < smoothed() ? num(model.residualRms(s)) : "–")
              .append("</td>");
        }
        sb.append("</tr><tr><td><font color='#C0392B'>истинная − полином</font></td>");
        for (int s = 0; s < SmoothingModel.SEGMENTS; s++) {
            sb.append("<td align=right><font color='#C0392B'>")
              .append(s < smoothed() ? num(model.errorRms(s)) : "–").append("</font></td>");
        }
        sb.append("</tr></table></html>");
        return sb.toString();
    }

    // ------------------------------------------------------------ числа

    private static final DecimalFormat ONE = format("#,##0.0");

    private static DecimalFormat format(String pattern) {
        DecimalFormatSymbols s = new DecimalFormatSymbols(new Locale("ru"));
        s.setDecimalSeparator(',');
        s.setGroupingSeparator(' ');
        s.setMinusSign('−');
        return new DecimalFormat(pattern, s);
    }

    /** Число с одним знаком после запятой, разряды через пробел. */
    static String num(double v) {
        synchronized (ONE) {
            return ONE.format(v);
        }
    }

    /** Время от входа в зону: «3 мин 12 с». */
    static String minutes(double seconds) {
        long s = Math.round(seconds);
        return (s / 60) + " мин " + (s % 60) + " с";
    }

    // ------------------------------------------------------- пролёт

    /**
     * Пролёт объекта через зону видимости: тот же чертёж, что на экране
     * результатов ({@link GeometryView}), с границами зоны видимости и
     * пройденными положениями объекта.
     */
    private class FlightView extends JPanel {

        private static final long serialVersionUID = 1L;

        FlightView() {
            setBackground(SKY);
            setPreferredSize(new Dimension(360, 300));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
            Trajectory.Orbital orb = model.getOrbit();

            int w = getWidth();
            int h = getHeight();
            int left = 12;
            int right = w - 12;
            int top = 34;
            int bottom = h - 44;

            // построение то же, что в GeometryView: дуга Земли с небольшим
            // прогибом, орбита в условном масштабе высоты
            double halfWidth = (right - left) / 2.0;
            double sag = Math.max(16, Math.min(30, halfWidth * 0.12));
            double surfaceRadius = (halfWidth * halfWidth + sag * sag) / (2 * sag);
            double px = (left + right) / 2.0;
            double py = bottom - 14;
            double cy = py + surfaceRadius;
            double edgeAngle = Math.asin(Math.min(1.0, halfWidth / surfaceRadius));
            double orbitHeight = Math.max(60, (py - top) * 0.62);
            double orbitRadius = surfaceRadius + orbitHeight;
            double gammaMax = Math.max(orb.centralAngle(model.getEntry()), orb.minCentralAngle());
            double scale = edgeAngle / (Math.max(gammaMax, 1e-9) * 1.25);

            Area earth = new Area(new Ellipse2D.Double(px - surfaceRadius, cy - surfaceRadius,
                    2 * surfaceRadius, 2 * surfaceRadius));
            earth.intersect(new Area(new Rectangle2D.Double(0, 0, w, h)));
            g.setColor(EARTH);
            g.fill(earth);
            g.setColor(EARTH_EDGE);
            g.setStroke(new BasicStroke(1.4f));
            g.draw(earth);

            // орбита: вне зоны видимости бледно, в зоне – обычным цветом
            double full = Math.toDegrees(edgeAngle);
            g.setColor(ORBIT_FAINT);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new Arc2D.Double(px - orbitRadius, cy - orbitRadius, 2 * orbitRadius,
                    2 * orbitRadius, 90 - full, 2 * full, Arc2D.OPEN));
            double aIn = signed(orb, model.getEntry()) * scale;
            double aOut = signed(orb, model.getExit()) * scale;
            g.setColor(ORBIT);
            g.setStroke(new BasicStroke(2.2f));
            g.draw(new Arc2D.Double(px - orbitRadius, cy - orbitRadius, 2 * orbitRadius,
                    2 * orbitRadius, 90 - Math.toDegrees(aOut),
                    Math.toDegrees(aOut - aIn), Arc2D.OPEN));

            // границы зоны видимости: штриховые линии из пункта
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[] {6f, 4f}, 0f));
            String[] titles = {"вход в зону", "выход из зоны"};
            double[] angles = {aIn, aOut};
            for (int k = 0; k < 2; k++) {
                double ox = px + orbitRadius * Math.sin(angles[k]);
                double oy = cy - orbitRadius * Math.cos(angles[k]);
                g.setColor(ZONE);
                g.draw(new Line2D.Double(px, py, ox, oy));
                int tw = g.getFontMetrics().stringWidth(titles[k]);
                double tx = Math.max(left, Math.min(ox - tw / 2.0, right - tw));
                g.drawString(titles[k], (float) tx, (float) (oy - 10));
            }
            g.setStroke(new BasicStroke(1f));

            // прежние положения объекта – точками
            g.setColor(ORBIT);
            for (int i = 0; i < shown - 1; i++) {
                double a = signed(orb, model.time(i)) * scale;
                double ox = px + orbitRadius * Math.sin(a);
                double oy = cy - orbitRadius * Math.cos(a);
                g.fill(new Ellipse2D.Double(ox - 2, oy - 2, 4, 4));
            }

            // пункт
            g.setColor(OBJECT);
            g.fill(new Ellipse2D.Double(px - 4, py - 4, 8, 8));
            g.setColor(Color.DARK_GRAY);
            g.drawString("измеритель", (float) (px + 9), (float) (py + 4));

            if (shown > 0) {
                double t = model.time(shown - 1);
                double a = signed(orb, t) * scale;
                double ox = px + orbitRadius * Math.sin(a);
                double oy = cy - orbitRadius * Math.cos(a);
                g.setColor(RANGE_LINE);
                g.setStroke(new BasicStroke(2.0f));
                g.draw(new Line2D.Double(px, py, ox, oy));
                g.setStroke(new BasicStroke(1f));
                g.setColor(OBJECT);
                g.fill(new Ellipse2D.Double(ox - 5, oy - 5, 10, 10));
                String label = "объект";
                int lw = g.getFontMetrics().stringWidth(label);
                float lx = (float) (ox + 9 + lw > right ? ox - 9 - lw : ox + 9);
                g.drawString(label, lx, (float) (oy + 15));
                // дальность у середины линии визирования
                g.setColor(RANGE_LINE);
                String r = "R = " + num(model.trueRange(shown - 1) / 1000.0) + " км";
                int rw = g.getFontMetrics().stringWidth(r);
                double mx = (px + ox) / 2;
                float rx = (float) (mx > px ? mx - rw - 8 : mx + 8);
                rx = Math.max(left, Math.min(rx, right - rw));
                g.drawString(r, rx, (float) ((py + oy) / 2));
                g.setColor(Color.BLACK);
                g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
                g.drawString("t = " + minutes(model.fromEntry(t)) + "   R = "
                        + num(model.trueRange(shown - 1) / 1000.0) + " км", left, 18);
                g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
            } else {
                g.setColor(Color.GRAY);
                g.drawString("Нажмите «Шаг» или «Пуск»", left, 18);
            }

            g.setColor(Color.GRAY);
            drawFitted(g, String.format(Locale.ROOT, "H = %.0f км, до трассы %.0f км; "
                    + "зона видимости – угол места не менее %.0f°, %s",
                    model.getData().getOrbitHeight(), model.getData().getTrackDistance(),
                    SmoothingModel.MIN_ELEVATION_DEG, minutes(model.duration())),
                    8, h - 20, w - 16);
            drawFitted(g, "высота орбиты и углы показаны в условном масштабе",
                    8, h - 6, w - 16);
            g.dispose();
        }
    }

    /** Центральный угол со знаком: до траверза объект слева, после – справа. */
    private static double signed(Trajectory.Orbital orb, double t) {
        double gamma = orb.centralAngle(t);
        return t < orb.closestApproachTime() ? -gamma : gamma;
    }

    /** Строка с уменьшением шрифта при нехватке места – как в GeometryView. */
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

    // --------------------------------------------------------- график

    /** Ход дальности: истинная кривая, измерения и отрезки полиномов. */
    private class RangeChart extends JPanel {

        private static final long serialVersionUID = 1L;

        RangeChart() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(360, 260));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            int w = getWidth();
            int h = getHeight();
            int l = 58;
            int r = w - 14;
            int t = 34;
            int b = h - 34;
            if (r - l < 40 || b - t < 40) {
                g.dispose();
                return;
            }

            // пределы осей постоянны на весь пролёт: по истинной кривой
            // с запасом на разброс точек
            double duration = model.duration();
            double yMin = Double.MAX_VALUE;
            double yMax = -Double.MAX_VALUE;
            for (int k = 0; k <= 200; k++) {
                double v = model.trueRangeAt(model.getEntry() + duration * k / 200);
                yMin = Math.min(yMin, v);
                yMax = Math.max(yMax, v);
            }
            double margin = 3.5 * SmoothingModel.DISPLAY_SIGMA;
            yMin = (yMin - margin) / 1000.0;
            yMax = (yMax + margin) / 1000.0;
            double step = niceStep((yMax - yMin) / 6);
            yMin = Math.floor(yMin / step) * step;
            yMax = Math.ceil(yMax / step) * step;
            double xMax = duration / 60.0;

            // сетка и оси
            g.setColor(new Color(0xE4, 0xE8, 0xEC));
            for (double y = yMin; y <= yMax + 1e-9; y += step) {
                int yy = (int) Math.round(sy(y, yMin, yMax, t, b));
                g.drawLine(l, yy, r, yy);
            }
            for (int m = 0; m <= (int) Math.floor(xMax); m++) {
                int xx = (int) Math.round(sx(m, xMax, l, r));
                g.drawLine(xx, t, xx, b);
            }
            g.setColor(Color.DARK_GRAY);
            g.drawRect(l, t, r - l, b - t);
            for (double y = yMin; y <= yMax + 1e-9; y += step) {
                int yy = (int) Math.round(sy(y, yMin, yMax, t, b));
                String s = String.format(Locale.ROOT, "%.0f", y);
                g.drawString(s, l - 6 - g.getFontMetrics().stringWidth(s), yy + 4);
            }
            for (int m = 0; m <= (int) Math.floor(xMax); m += xMax > 10 ? 2 : 1) {
                int xx = (int) Math.round(sx(m, xMax, l, r));
                String s = String.valueOf(m);
                g.drawString(s, xx - g.getFontMetrics().stringWidth(s) / 2, b + 14);
            }
            String xTitle = "t от входа в зону, мин";
            g.drawString(xTitle, r - g.getFontMetrics().stringWidth(xTitle), b + 28);
            g.drawString("R, км", 6, t - 8);

            Plot clip = new Plot(l, t, r, b);
            g.clip(new Rectangle2D.Double(l, t, r - l, b - t));

            // истинная дальность – непрерывно до текущего момента
            if (shown > 0) {
                double end = model.time(shown - 1);
                Path2D.Double path = new Path2D.Double();
                int n = Math.max(2, (int) Math.ceil((end - model.getEntry()) / duration * 600));
                for (int k = 0; k <= n; k++) {
                    double tt = model.getEntry() + (end - model.getEntry()) * k / n;
                    double xx = clip.x(model.fromEntry(tt) / 60.0, xMax);
                    double yy = clip.y(model.trueRangeAt(tt) / 1000.0, yMin, yMax);
                    if (k == 0) {
                        path.moveTo(xx, yy);
                    } else {
                        path.lineTo(xx, yy);
                    }
                }
                g.setColor(TRUE_CURVE);
                g.setStroke(new BasicStroke(1.8f));
                g.draw(path);
            }

            // отрезки полиномов сглаженных участков
            g.setColor(POLY);
            g.setStroke(new BasicStroke(2.6f));
            for (int s = 0; s < smoothed(); s++) {
                double t0 = model.segmentStart(s);
                double t1 = model.time((s + 1) * SmoothingModel.SEGMENT - 1);
                g.draw(new Line2D.Double(
                        clip.x(model.fromEntry(t0) / 60.0, xMax),
                        clip.y(model.displayPolynomial(s, t0) / 1000.0, yMin, yMax),
                        clip.x(model.fromEntry(t1) / 60.0, xMax),
                        clip.y(model.displayPolynomial(s, t1) / 1000.0, yMin, yMax)));
            }

            // измерения
            g.setStroke(new BasicStroke(1f));
            for (int i = 0; i < shown; i++) {
                double xx = clip.x(model.fromEntry(model.time(i)) / 60.0, xMax);
                double yy = clip.y(model.displayMeasured(i) / 1000.0, yMin, yMax);
                g.setColor(i == shown - 1 ? RANGE_LINE : POINTS);
                double d = i == shown - 1 ? 7 : 5;
                g.fill(new Ellipse2D.Double(xx - d / 2, yy - d / 2, d, d));
            }
            g.setClip(null);

            // легенда – вверху по центру, где кривая ниже всего
            String[] names = {"истинная дальность", "измерения",
                "полином 1-й степени по 10 измерениям"};
            int lx = l + 10;
            int ly = 14;
            for (int k = 0; k < names.length; k++) {
                int tw = g.getFontMetrics().stringWidth(names[k]);
                if (lx + 24 + tw > w - 4 && k > 0) {
                    lx = l + 10;
                    ly += 14;
                }
                if (k == 0) {
                    g.setColor(TRUE_CURVE);
                    g.setStroke(new BasicStroke(1.8f));
                    g.drawLine(lx, ly - 4, lx + 18, ly - 4);
                } else if (k == 1) {
                    g.setColor(POINTS);
                    g.fill(new Ellipse2D.Double(lx + 6.5, ly - 6.5, 5, 5));
                } else {
                    g.setColor(POLY);
                    g.setStroke(new BasicStroke(2.6f));
                    g.drawLine(lx, ly - 4, lx + 18, ly - 4);
                }
                g.setStroke(new BasicStroke(1f));
                g.setColor(Color.DARK_GRAY);
                g.drawString(names[k], lx + 22, ly);
                lx += 22 + tw + 16;
            }
            g.dispose();
        }
    }

    /** Пересчёт координат графика в точки поля. */
    private static final class Plot {

        private final double l;
        private final double t;
        private final double r;
        private final double b;

        Plot(double l, double t, double r, double b) {
            this.l = l;
            this.t = t;
            this.r = r;
            this.b = b;
        }

        double x(double v, double max) {
            return sx(v, max, l, r);
        }

        double y(double v, double min, double max) {
            return sy(v, min, max, t, b);
        }
    }

    private static double sx(double v, double max, double l, double r) {
        return l + (r - l) * v / max;
    }

    private static double sy(double v, double min, double max, double t, double b) {
        return b - (b - t) * (v - min) / (max - min);
    }

    /** Круглый шаг сетки: 1, 2 или 5, умноженное на степень десяти. */
    private static double niceStep(double raw) {
        double p = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / p;
        return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * p;
    }

    // ------------------------------------------------------- формулы

    /**
     * Под графиком: общий вид полинома и полином последнего сглаженного
     * участка с числами. Формулы набираются {@link MathText}.
     */
    private class FormulaPanel extends JPanel {

        private static final long serialVersionUID = 1L;
        private static final int SIZE = 15;

        private BufferedImage general;
        private BufferedImage current;
        private String note = "";

        FormulaPanel() {
            setBackground(Color.WHITE);
            general = MathText.render("Полином 1-й степени:  R*(t) = \\alpha_{0} + \\alpha_{1}"
                    + "·t,  t – от начала участка", SIZE);
        }

        void update() {
            int s = smoothed();
            if (s == 0) {
                current = null;
                note = "Полином участка появится после 10-го измерения";
            } else {
                double[] c = model.coefficients(s - 1);
                int from = (s - 1) * SmoothingModel.SEGMENT + 1;
                String sign = c[1] < 0 ? " − " : " + ";
                current = MathText.render("Участок " + s + " (измерения " + from + "–"
                        + (from + SmoothingModel.SEGMENT - 1) + "):  R*(t) = " + num(c[0])
                        + sign + num(Math.abs(c[1])) + "·t, м  (t в секундах)", SIZE);
                note = "";
            }
            int h = general.getHeight() / MathText.SCALE
                    + (current != null ? current.getHeight() / MathText.SCALE : 22) + 6;
            setPreferredSize(new Dimension(100, h));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int y = 2;
            y += drawScaled(g, general, 6, y);
            if (current != null) {
                drawScaled(g, current, 6, y);
            } else {
                g.setColor(Color.GRAY);
                g.setFont(getFont().deriveFont(Font.PLAIN, 12f));
                g.drawString(note, 12, y + 15);
            }
            g.dispose();
        }

        /** Рисует формулу, при нехватке ширины уменьшая её; возвращает высоту. */
        private int drawScaled(Graphics2D g, BufferedImage img, int x, int y) {
            double k = 1.0 / MathText.SCALE;
            double fit = (getWidth() - 2.0 * x) / (img.getWidth() * k);
            if (fit < 1) {
                k *= fit;
            }
            int w = (int) Math.round(img.getWidth() * k);
            int h = (int) Math.round(img.getHeight() * k);
            g.drawImage(img, x, y, w, h, null);
            return h;
        }
    }

    // ------------------------------------------------------- таблица

    /** Таблица чисел: 60 строк, заполняются по мере измерений и сглаживания. */
    private class TableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final String[] names = {
            "<html><center>№<br>измерения</center></html>",
            red("Истинная<br>дальность, м"),
            "<html><center>Измеренная<br>дальность, м</center></html>",
            red("Измеренная −<br>истинная, м"),
            "<html><center>Полином<br>R*(t) = α<sub>0</sub> + α<sub>1</sub>·t, м</center></html>",
            "<html><center>Полином −<br>измеренная, м</center></html>",
            red("Истинная −<br>полином, м")
        };

        private String red(String s) {
            return "<html><center><font color='#C0392B'>" + s + "</font></center></html>";
        }

        @Override
        public int getRowCount() {
            return SmoothingModel.COUNT;
        }

        @Override
        public int getColumnCount() {
            return names.length;
        }

        @Override
        public String getColumnName(int c) {
            return names[c];
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }

        @Override
        public Object getValueAt(int r, int c) {
            if (c == 0) {
                return String.valueOf(r + 1);
            }
            if (r >= shown) {
                return "";
            }
            switch (c) {
                case 1: return num(model.trueRange(r));
                case 2: return num(model.measured(r));
                case 3: return num(model.noise(r));
                default: break;
            }
            if (SmoothingModel.segmentOf(r) >= smoothed()) {
                return "";
            }
            switch (c) {
                case 4: return num(model.polynomial(SmoothingModel.segmentOf(r), model.time(r)));
                case 5: return num(model.residual(r));
                default: return num(model.error(r));
            }
        }
    }

    /** Красный шрифт у неизвестных измерителю величин, полосы участков. */
    private static class Renderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(t, value, false, false, row, column);
            setHorizontalAlignment(column == 0 ? SwingConstants.CENTER : SwingConstants.RIGHT);
            setForeground(column == 1 || column == 3 || column == 6 ? HIDDEN : Color.BLACK);
            setBackground(SmoothingModel.segmentOf(row) % 2 == 0 ? Color.WHITE : SEGMENT_BAND);
            // утолщённая черта после каждого участка
            setBorder((row + 1) % SmoothingModel.SEGMENT == 0
                    ? BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 0, 2, 0, Color.GRAY),
                            BorderFactory.createEmptyBorder(0, 4, 0, 4))
                    : BorderFactory.createEmptyBorder(0, 4, 0, 4));
            return this;
        }
    }
}
