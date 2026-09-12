package ru.vka.upo.core;

import java.util.List;
import ru.vka.upo.model.InputData;
import ru.vka.upo.model.VariantTable;

/** Пробный расчёт в новом (правильном) режиме – для отработки программы. */
public final class Demo {

    public static void main(String[] args) {
        InputData d = VariantTable.variants().get(0).toInputData();
        d.setStep(0.1);
        d.setSampleSize(49);
        for (int anchor : new int[] {1, 25, 49}) {
            d.setAnchor(anchor);
            System.out.printf("%nПривязка M0 = %d%n", anchor);
            System.out.println("  m    t, с     ERD, м     ERS, м      ER, м     EVD, м/с   EVS, м/с");
            for (int m = 0; m <= 4; m++) {
                d.setDegree(m);
                List<ErrorRow> t = new Processor(d).table();
                ErrorRow r = t.get(0);
                System.out.printf("%3d %7.2f %10.4g %10.4g %10.4g %10.4g %10.4g%n",
                        m, r.getTime(), r.getRangeDynamic(), r.getRangeRandom(),
                        r.getRangeTotal(), r.getSpeedDynamic(), r.getSpeedRandom());
            }
        }
    }
}
