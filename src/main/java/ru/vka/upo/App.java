package ru.vka.upo;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import ru.vka.upo.ui.Emblem;
import ru.vka.upo.ui.MainFrame;

/**
 * Точка входа программы лабораторной работы «Предварительная обработка».
 *
 * ВКА имени А.Ф. Можайского, кафедра космической радиолокации и радионавигации.
 */
public class App {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {
            // при недоступности системного оформления используется стандартное
        }
        // значок приложения: в Linux панель задач и переключатель окон берут
        // его не от окна, а от приложения целиком, поэтому ставится отдельно
        Emblem.applyToApplication();
        // пароль преподавателя, вписанный в настроечный файл открытым
        // текстом, заменяется его хэшем (см. TeacherPassword)
        ru.vka.upo.model.TeacherPassword.migrate();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainFrame().setVisible(true);
            }
        });
    }
}
