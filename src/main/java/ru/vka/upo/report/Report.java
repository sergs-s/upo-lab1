package ru.vka.upo.report;

import java.util.Locale;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.Notebook;
import ru.vka.upo.model.Student;

/**
 * Отчёт по лабораторной работе.
 *
 * Собирается из того, что сделал обучающийся: его данные, исходные данные
 * варианта, заполненные им таблицы, построенные по этим таблицам графики
 * и написанные им выводы. Ничего сверх этого программа в отчёт не
 * добавляет: если строка таблицы не заполнена, она такой и останется.
 */
public final class Report {

    private Report() {
    }

    /**
     * Записывает отчёт.
     *
     * @param file     куда записать
     * @param student  сведения об обучающемся
     * @param data     исходные данные варианта
     * @param notebook рабочая тетрадь
     * @param charts   готовые изображения графиков по пунктам задания
     */
    public static void write(Path file, Student student, InputData data,
            Notebook notebook, Map<Notebook.Item, BufferedImage> charts)
            throws IOException {
        DocxWriter doc = new DocxWriter();

        doc.heading("Отчёт по лабораторной работе", 1);
        doc.heading("«Исследование эффективности устройств предварительной "
                + "обработки радионавигационных систем»", 1);

        Integer testScore = student.getTestScore();
        doc.paragraph("Выполнил: " + student.getName()
                + "\nУчебная группа: " + student.getGroup()
                + "\nВариант задания: " + student.getVariantNumber()
                + "\nОценка за входной контроль: " + (testScore == null
                        ? "входной контроль не проводился" : String.valueOf(testScore))
                + "\nДата: " + new SimpleDateFormat("dd.MM.yyyy").format(new Date()));

        doc.heading("1. Исходные данные", 2);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Признак траектории", String.valueOf(data.getMeasurer())});
        rows.add(new String[] {"Измеряемые параметры", String.valueOf(data.getMeasured())});
        rows.add(new String[] {"Интервал измерений, с", num(data.getInterval())});
        if (data.getMeasurer() == InputData.Measurer.GROUND) {
            rows.add(new String[] {"Высота орбиты, км", num(data.getOrbitHeight())});
            rows.add(new String[] {"Расстояние до трассы, км", num(data.getTrackDistance())});
        } else {
            rows.add(new String[] {"Траверзное расстояние, км", num(data.getTraverseDistance())});
            rows.add(new String[] {"Относительная скорость, м/с", num(data.getRelativeSpeed())});
        }
        rows.add(new String[] {"СКО измерения дальности, м", num(data.getSigmaRange())});
        if (data.getMeasured().hasVelocity()) {
            rows.add(new String[] {"СКО измерения скорости, м/с", num(data.getSigmaVelocity())});
        }
        doc.table(new String[] {"Величина", "Значение"}, rows);

        int number = 2;
        for (Notebook.Item item : Notebook.Item.values()) {
            Notebook.Page page = notebook.page(item);
            doc.heading(number + ". Пункт " + item.getLetter() + ": влияние параметра "
                    + item.getParameter() + " (" + item.getParameterTitle() + ")", 2);
            doc.paragraph("Условия: " + item.fixedDescription(notebook.getChosenDegree())
                    + ". Числа сняты из строки " + page.getSourceRow()
                    + " таблицы результатов.");

            List<String[]> table = new ArrayList<>();
            for (Notebook.Line l : page.getLines()) {
                table.add(new String[] {
                    num(l.getParameter()),
                    num(l.getRangeDynamic()),
                    num(l.getRangeRandom()),
                    num(l.getRangeTotal())
                });
            }
            doc.table(new String[] {item.getParameter(), "ERD, м", "ERS, м", "ER, м"}, table);

            BufferedImage img = charts == null ? null : charts.get(item);
            if (img != null) {
                doc.image(img, 560);
                doc.paragraph("Рисунок " + (number - 1) + ". Зависимость ошибок оценивания "
                        + "дальности от параметра " + item.getParameter() + ".");
            }
            String conclusion = page.getConclusion().trim();
            doc.paragraph("Вывод: " + (conclusion.isEmpty() ? "не записан." : conclusion));
            number++;
        }

        doc.heading(number + ". Общий вывод", 2);
        doc.paragraph("_____________________________________________________________"
                + "\n\n_____________________________________________________________"
                + "\n\n_____________________________________________________________");
        doc.save(file);
    }

    /** Имя файла отчёта: по фамилии, группе и варианту. */
    public static String fileName(Student student) {
        String name = student.getName().replaceAll("[\\\\/:*?\"<>|]", "").trim();
        if (name.isEmpty()) {
            name = "Отчёт";
        }
        return "Отчёт ЛР1 " + name + " гр " + student.getGroup()
                + " вар " + student.getVariantNumber() + ".docx";
    }

    private static String num(Double v) {
        if (v == null || Double.isNaN(v)) {
            return "";
        }
        double a = Math.abs(v);
        if (v == Math.rint(v) && a < 1e6) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        if (a >= 1e5 || (a > 0 && a < 1e-3)) {
            return String.format(Locale.ROOT, "%.4g", v);
        }
        return String.format(Locale.ROOT, "%.4g", v);
    }

    private static String num(double v) {
        return num(Double.valueOf(v));
    }
}
