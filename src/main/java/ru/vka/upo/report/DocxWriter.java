package ru.vka.upo.report;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Запись документа Word (.docx) без внешних библиотек.
 *
 * Файл .docx – это обычный zip-архив с несколькими файлами XML внутри.
 * Здесь собирается самый простой их набор, которого хватает для отчёта
 * по лабораторной работе: заголовки, абзацы, таблицы и рисунки. Так
 * программа остаётся без единой внешней зависимости и собирается
 * на любой машине, в том числе без доступа в сеть.
 */
public class DocxWriter {

    /** Перевод точек изображения в единицы, которыми оперирует Word. */
    private static final int EMU_PER_PIXEL = 9525;

    private final StringBuilder body = new StringBuilder();
    private int columnWidth = 2000;
    private final List<byte[]> images = new ArrayList<>();

    /** Заголовок; уровень 1 – название отчёта, 2 – раздел. */
    public DocxWriter heading(String text, int level) {
        int size = level <= 1 ? 32 : 26;   // половины пункта
        body.append("<w:p><w:pPr><w:spacing w:before=\"240\" w:after=\"120\" ")
            .append("w:line=\"240\" w:lineRule=\"auto\"/>")
            .append(level <= 1 ? "<w:jc w:val=\"center\"/>" : "")
            .append("</w:pPr>")
            .append("<w:r><w:rPr><w:b/><w:sz w:val=\"").append(size).append("\"/>")
            .append("<w:szCs w:val=\"").append(size).append("\"/></w:rPr>")
            .append("<w:t xml:space=\"preserve\">").append(esc(text)).append("</w:t></w:r></w:p>");
        return this;
    }

    /**
     * Абзац. Многострочный текст разбивается на отдельные абзацы и
     * выравнивается по левому краю: так строки шапки не растягиваются
     * во всю ширину страницы.
     */
    public DocxWriter paragraph(String text) {
        String[] lines = text == null ? new String[] {""} : text.split("\n", -1);
        String jc = lines.length > 1 ? "left" : "both";
        for (String line : lines) {
            body.append("<w:p><w:pPr><w:jc w:val=\"").append(jc).append("\"/>")
                .append("<w:spacing w:line=\"240\" w:lineRule=\"auto\"/></w:pPr>")
                .append("<w:r><w:t xml:space=\"preserve\">")
                .append(esc(line)).append("</w:t></w:r></w:p>");
        }
        return this;
    }

    /** Ширина области набора в двадцатых долях пункта. */
    private static final int TEXT_WIDTH = 9355;

    /** Таблица с шапкой; колонки одинаковой ширины во всю строку набора. */
    public DocxWriter table(String[] headers, List<String[]> rows) {
        int columns = Math.max(headers.length, 1);
        int width = TEXT_WIDTH / columns;
        this.columnWidth = width;
        body.append("<w:tbl><w:tblPr><w:tblW w:w=\"").append(TEXT_WIDTH)
            .append("\" w:type=\"dxa\"/>")
            .append("<w:tblLayout w:type=\"fixed\"/>")
            .append("<w:tblBorders>");
        for (String side : new String[] {"top", "left", "bottom", "right",
                "insideH", "insideV"}) {
            body.append("<w:").append(side)
                .append(" w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"808080\"/>");
        }
        body.append("</w:tblBorders></w:tblPr><w:tblGrid>");
        for (int i = 0; i < columns; i++) {
            body.append("<w:gridCol w:w=\"").append(width).append("\"/>");
        }
        body.append("</w:tblGrid>");
        row(headers, true);
        for (String[] r : rows) {
            row(r, false);
        }
        body.append("</w:tbl><w:p/>");
        return this;
    }

    private void row(String[] cells, boolean header) {
        body.append("<w:tr>");
        for (String c : cells) {
            body.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(columnWidth)
                .append("\" w:type=\"dxa\"/></w:tcPr>")
                .append("<w:p><w:pPr><w:jc w:val=\"center\"/>")
                .append("<w:spacing w:line=\"240\" w:lineRule=\"auto\"/></w:pPr><w:r>");
            if (header) {
                body.append("<w:rPr><w:b/></w:rPr>");
            }
            body.append("<w:t xml:space=\"preserve\">").append(esc(c == null ? "" : c))
                .append("</w:t></w:r></w:p></w:tc>");
        }
        body.append("</w:tr>");
    }

    /** Рисунок; ширина задаётся в точках, высота считается по пропорции. */
    public DocxWriter image(BufferedImage img, int widthPx) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        images.add(out.toByteArray());
        int id = images.size();
        int heightPx = (int) Math.round(widthPx * (double) img.getHeight() / img.getWidth());
        long cx = (long) widthPx * EMU_PER_PIXEL;
        long cy = (long) heightPx * EMU_PER_PIXEL;
        body.append("<w:p><w:pPr><w:jc w:val=\"center\"/>")
            .append("<w:spacing w:line=\"240\" w:lineRule=\"auto\"/></w:pPr><w:r><w:drawing>")
            .append("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
            .append("<wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
            .append("<wp:docPr id=\"").append(id).append("\" name=\"Рисунок ")
            .append(id).append("\"/><a:graphic ")
            .append("xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">")
            .append("<a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
            .append("<pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
            .append("<pic:nvPicPr><pic:cNvPr id=\"").append(id).append("\" name=\"image")
            .append(id).append(".png\"/><pic:cNvPicPr/></pic:nvPicPr>")
            .append("<pic:blipFill><a:blip r:embed=\"rId").append(100 + id)
            .append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
            .append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx)
            .append("\" cy=\"").append(cy).append("\"/></a:xfrm>")
            .append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>")
            .append("</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
        return this;
    }

    /** Записывает документ. */
    public void save(Path file) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/"
                    + "officeDocument/2006/relationships/officeDocument\" "
                    + "Target=\"word/document.xml\"/></Relationships>");
            put(zip, "word/_rels/document.xml.rels", documentRels());
            put(zip, "word/document.xml", document());
            put(zip, "word/styles.xml", styles());
            for (int i = 0; i < images.size(); i++) {
                zip.putNextEntry(new ZipEntry("word/media/image" + (i + 1) + ".png"));
                zip.write(images.get(i));
                zip.closeEntry();
            }
        }
    }

    private static void put(ZipOutputStream zip, String name, String content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        OutputStream out = zip;
        out.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-"
                + "package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Default Extension=\"png\" ContentType=\"image/png\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd."
                + "openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd."
                + "openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
                + "</Types>";
    }

    private String documentRels() {
        StringBuilder sb = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/"
                + "relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/"
                + "officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        for (int i = 0; i < images.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(101 + i)
              .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/"
                      + "relationships/image\" Target=\"media/image").append(i + 1)
              .append(".png\"/>");
        }
        return sb.append("</Relationships>").toString();
    }

    /**
     * Стили документа: шрифт Times New Roman, 12 пт, одинарный межстрочный
     * интервал по умолчанию для всего текста (заголовки задают собственный
     * увеличенный размер поверх этого).
     */
    private String styles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/"
                + "2006/main\">"
                + "<w:docDefaults>"
                + "<w:rPrDefault><w:rPr>"
                + "<w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\" "
                + "w:eastAsia=\"Times New Roman\" w:cs=\"Times New Roman\"/>"
                + "<w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/>"
                + "</w:rPr></w:rPrDefault>"
                + "<w:pPrDefault><w:pPr>"
                + "<w:spacing w:line=\"240\" w:lineRule=\"auto\"/>"
                + "</w:pPr></w:pPrDefault>"
                + "</w:docDefaults>"
                + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">"
                + "<w:name w:val=\"Normal\"/></w:style>"
                + "</w:styles>";
    }

    private String document() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/"
                + "2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/"
                + "2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/"
                + "drawingml/2006/wordprocessingDrawing\">"
                + "<w:body>" + body
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1134\" w:right=\"850\" w:bottom=\"1134\" w:left=\"1701\" "
                + "w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>"
                + "</w:body></w:document>";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
