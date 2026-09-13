package ru.vka.upo.ui;

import java.awt.BorderLayout;
import java.awt.Window;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import ru.vka.upo.model.InputData;

/**
 * Окно изменения параметров режима обработки (степень полинома, объём
 * выборки, шаг измерений, момент привязки) без возврата на экран ввода
 * исходных данных.
 *
 * Вызывается с экрана результатов кнопкой «Параметры режима обработки»,
 * чтобы не гонять обучающегося туда-сюда между экранами ради одной цифры.
 * Значения синхронизируются с экраном ввода данных в обе стороны:
 * изменения отсюда сразу передаются в поля того экрана (см.
 * {@link InputPanel#applyModeParams(InputData)}, вызывается из
 * {@link MainFrame#syncModeParams(InputData)}), а при повторном открытии
 * окно видит те значения, что действуют сейчас в расчёте.
 *
 * Само окно – только рамка: поля, кнопки и расчёт интервала усреднения
 * лежат в {@link ModeParamsPanel}, которая правится в конструкторе форм
 * NetBeans.
 */
public final class ModeParamsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final ModeParamsPanel panel = new ModeParamsPanel();

    private ModeParamsDialog(Window owner, InputData base) {
        super(owner, "Параметры режима обработки", ModalityType.APPLICATION_MODAL);
        panel.setBase(base);
        panel.setOnFinish(new Runnable() {
            @Override
            public void run() {
                setVisible(false);
            }
        });
        setLayout(new BorderLayout());
        add(panel, BorderLayout.CENTER);
        getRootPane().setDefaultButton(panel.getOkButton());
        Emblem.applyTo(this);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /**
     * Показывает окно и возвращает копию {@code base} с новыми параметрами
     * режима обработки, или {@code null}, если нажата «Отмена» либо окно
     * закрыто.
     */
    public static InputData show(java.awt.Component parent, InputData base) {
        ModeParamsDialog d = new ModeParamsDialog(
                SwingUtilities.getWindowAncestor(parent), base);
        d.setVisible(true);
        return d.panel.getResult();
    }
}
