package ru.vka.upo.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Отрисовка математических выражений в виде картинки.
 *
 * Поддерживается небольшое подмножество привычной записи формул:
 *
 * <pre>
 *   \sqrt{...}       корень с чертой над подкоренным выражением
 *   \frac{a}{b}      дробь с горизонтальной чертой
 *   ^{...}  _{...}   верхний и нижний индексы
 *   \Sigma \sigma …  греческие буквы и знаки (см. таблицу ниже)
 * </pre>
 *
 * Этого достаточно для формул лабораторной работы, а главное – корень
 * получается настоящим, с крышкой над подкоренным выражением, чего простой
 * разметкой не добиться. Внешних библиотек не требуется.
 */
public final class MathText {

    private static final BufferedImage PROBE =
            new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

    private MathText() {
    }

    /**
     * Во сколько раз формула чертится крупнее, чем показывается: при
     * уменьшении вдвое надписи выходят чёткими, а не размытыми.
     */
    public static final int SCALE = 2;

    /** Картинка с формулой; прозрачных полей нет, фон белый. */
    public static BufferedImage render(String expression, int size) {
        Font font = new Font(Font.SERIF, Font.PLAIN, size);
        Graphics2D probe = PROBE.createGraphics();
        probe.setFont(font);
        Box box = new Parser(expression, font, probe).parse();
        probe.dispose();

        int pad = Math.max(4, size / 4);
        int w = (int) Math.ceil(box.width) + 2 * pad;
        int h = (int) Math.ceil(box.ascent + box.descent) + 2 * pad;
        BufferedImage img = new BufferedImage(w * SCALE, h * SCALE,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w * SCALE, h * SCALE);
        g.scale(SCALE, SCALE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setFont(font);
        box.draw(g, pad, pad + box.ascent);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------- разметка

    /** Прямоугольник с формулой: ширина и высоты над и под опорной линией. */
    abstract static class Box {

        double width;
        double ascent;
        double descent;

        abstract void draw(Graphics2D g, double x, double baseline);
    }

    /** Простой текст. */
    private static class TextBox extends Box {

        private final String text;
        private final Font font;

        TextBox(String text, Font font, FontMetrics fm) {
            this.text = text;
            this.font = font;
            this.width = fm.stringWidth(text);
            this.ascent = fm.getAscent() * 0.86;
            this.descent = fm.getDescent();
        }

        @Override
        void draw(Graphics2D g, double x, double baseline) {
            Font old = g.getFont();
            g.setFont(font);
            g.drawString(text, (float) x, (float) baseline);
            g.setFont(old);
        }
    }

    /** Последовательность выражений. */
    private static class HBox extends Box {

        private final List<Box> items = new ArrayList<>();

        void add(Box b) {
            items.add(b);
            width += b.width;
            ascent = Math.max(ascent, b.ascent);
            descent = Math.max(descent, b.descent);
        }

        @Override
        void draw(Graphics2D g, double x, double baseline) {
            double cx = x;
            for (Box b : items) {
                b.draw(g, cx, baseline);
                cx += b.width;
            }
        }
    }

    /** Верхний или нижний индекс. */
    private static class ScriptBox extends Box {

        private final Box script;
        private final boolean up;
        private final double shift;

        ScriptBox(Box script, boolean up, double shift) {
            this.script = script;
            this.up = up;
            this.shift = shift;
            this.width = script.width;
            this.ascent = up ? script.ascent + shift : Math.max(0, script.ascent - shift);
            this.descent = up ? Math.max(0, script.descent - shift) : script.descent + shift;
        }

        @Override
        void draw(Graphics2D g, double x, double baseline) {
            script.draw(g, x, baseline + (up ? -shift : shift));
        }
    }

    /** Дробь. */
    private static class FracBox extends Box {

        private final Box top;
        private final Box bottom;
        private final double gap;

        FracBox(Box top, Box bottom, double gap) {
            this.top = top;
            this.bottom = bottom;
            this.gap = gap;
            this.width = Math.max(top.width, bottom.width) + gap;
            this.ascent = top.ascent + top.descent + gap;
            this.descent = bottom.ascent + bottom.descent + gap * 0.4;
        }

        @Override
        void draw(Graphics2D g, double x, double baseline) {
            double line = baseline - gap * 0.9;
            top.draw(g, x + (width - top.width) / 2, line - gap * 0.7 - top.descent);
            bottom.draw(g, x + (width - bottom.width) / 2, line + gap * 0.7 + bottom.ascent);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new Line2D.Double(x, line, x + width, line));
        }
    }

    /** Корень: знак радикала и черта над подкоренным выражением. */
    private static class SqrtBox extends Box {

        private final Box inner;
        private final double signWidth;
        private final double lift;

        SqrtBox(Box inner, double signWidth, double lift) {
            this.inner = inner;
            this.signWidth = signWidth;
            this.lift = lift;
            this.width = inner.width + signWidth + 4;
            this.ascent = inner.ascent + lift;
            this.descent = inner.descent;
        }

        @Override
        void draw(Graphics2D g, double x, double baseline) {
            double top = baseline - inner.ascent - lift * 0.75;
            double bottom = baseline + inner.descent;
            g.setStroke(new BasicStroke(1.3f));
            // «галочка» радикала
            g.draw(new Line2D.Double(x, bottom - (bottom - top) * 0.42,
                    x + signWidth * 0.34, bottom - (bottom - top) * 0.18));
            g.draw(new Line2D.Double(x + signWidth * 0.34, bottom - (bottom - top) * 0.18,
                    x + signWidth * 0.62, top));
            // черта над подкоренным выражением
            g.draw(new Line2D.Double(x + signWidth * 0.62, top,
                    x + width, top));
            inner.draw(g, x + signWidth + 2, baseline);
        }
    }

    // -------------------------------------------------------------- разбор

    /** Замены для греческих букв и знаков. */
    private static String symbol(String name) {
        switch (name) {
            case "alpha": return "α";
            case "beta": return "β";
            case "gamma": return "γ";
            case "sigma": return "σ";
            case "Sigma": return "Σ";
            case "Delta": return "Δ";
            case "delta": return "δ";
            case "partial": return "∂";
            case "cdot": return "·";
            case "times": return "×";
            case "approx": return "≈";
            case "pm": return "±";
            case "to": return "→";
            case "min": return "min";
            case "ldots": return "…";
            case "hat": return "̂";
            default: return name;
        }
    }

    /** Разбор выражения в прямоугольники. */
    private static class Parser {

        private final String src;
        private final Font font;
        private final Graphics2D g;
        private int pos;

        Parser(String src, Font font, Graphics2D g) {
            this.src = src;
            this.font = font;
            this.g = g;
        }

        Box parse() {
            return parseUntil('\0');
        }

        private Box parseUntil(char stop) {
            HBox box = new HBox();
            StringBuilder text = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == stop) {
                    pos++;
                    break;
                }
                if (c == '\\' || c == '^' || c == '_') {
                    flush(box, text);
                    pos++;
                    if (c == '\\') {
                        command(box);
                    } else {
                        Box inner = group(font.getSize() * 0.72f);
                        box.add(new ScriptBox(inner, c == '^', font.getSize() * 0.34));
                    }
                    continue;
                }
                text.append(c);
                pos++;
            }
            flush(box, text);
            return box;
        }

        private void command(HBox box) {
            StringBuilder name = new StringBuilder();
            while (pos < src.length() && Character.isLetter(src.charAt(pos))) {
                name.append(src.charAt(pos));
                pos++;
            }
            String cmd = name.toString();
            if ("sqrt".equals(cmd)) {
                Box inner = group(font.getSize());
                box.add(new SqrtBox(inner, font.getSize() * 0.55, font.getSize() * 0.30));
            } else if ("frac".equals(cmd)) {
                Box top = group(font.getSize() * 0.94f);
                Box bottom = group(font.getSize() * 0.94f);
                box.add(new FracBox(top, bottom, font.getSize() * 0.28));
            } else {
                Font f = font;
                g.setFont(f);
                box.add(new TextBox(symbol(cmd), f, g.getFontMetrics()));
            }
        }

        /** Содержимое фигурных скобок, набранное шрифтом заданного размера. */
        private Box group(float size) {
            while (pos < src.length() && src.charAt(pos) == ' ') {
                pos++;
            }
            if (pos >= src.length() || src.charAt(pos) != '{') {
                // одиночный символ без скобок
                String one = pos < src.length() ? String.valueOf(src.charAt(pos++)) : "";
                Font f = font.deriveFont(size);
                g.setFont(f);
                return new TextBox(one, f, g.getFontMetrics());
            }
            pos++;
            int depth = 1;
            int start = pos;
            while (pos < src.length() && depth > 0) {
                char c = src.charAt(pos);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
                pos++;
            }
            String inner = src.substring(start, pos - 1);
            Font f = font.deriveFont(size);
            Graphics2D probe = PROBE.createGraphics();
            probe.setFont(f);
            Box b = new Parser(inner, f, probe).parse();
            probe.dispose();
            return b;
        }

        private void flush(HBox box, StringBuilder text) {
            if (text.length() == 0) {
                return;
            }
            g.setFont(font);
            box.add(new TextBox(text.toString(), font, g.getFontMetrics()));
            text.setLength(0);
        }
    }
}
