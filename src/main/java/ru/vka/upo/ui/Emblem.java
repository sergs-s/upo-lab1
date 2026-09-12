package ru.vka.upo.ui;

import java.awt.Image;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Эмблема кафедры космической радиолокации и радионавигации: значок программы.
 *
 * Файлы значка лежат в самом jar-е (папка ресурсов ru/vka/upo) в нескольких
 * размерах. Несколько размеров нужны потому, что система выбирает значок
 * под место, куда его ставит: 16 и 24 точки – заголовок окна и панель задач,
 * 32 и 48 – список переключения окон, 64 и больше – крупные значки рабочего
 * стола. Если оставить один крупный файл, система уменьшит его сама, и на
 * мелких размерах надпись по кругу превратится в кашу.
 *
 * Значок ставится трижды и в трёх разных местах:
 * <ul>
 *   <li>окну – {@link #applyTo(Window)}: заголовок окна и панель задач
 *       в Windows;</li>
 *   <li>приложению целиком – {@link #applyToApplication()}: панель задач
 *       и переключатель окон в Linux, где значок берётся не от окна,
 *       а от приложения;</li>
 *   <li>диалогам – они получают его от своего хозяина сами, поэтому каждому
 *       диалогу при создании передаётся окно-владелец.</li>
 * </ul>
 *
 * Без этого и в Windows, и в Linux показывался бы стандартный значок Java
 * (кофейная чашка) либо пустой прямоугольник Swing.
 */
public final class Emblem {

    /** Размеры, в которых заготовлен значок. */
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    /** Загруженные изображения; загружаются один раз при первом обращении. */
    private static List<Image> images;

    private Emblem() {
    }

    /**
     * Изображения значка во всех заготовленных размерах.
     *
     * Список может оказаться пустым, если ресурсы почему-то недоступны
     * (например, программу запустили из распакованной папки с потерянными
     * файлами). Это не ошибка, из-за которой стоит останавливать работу:
     * программа просто останется со стандартным значком.
     */
    public static synchronized List<Image> images() {
        if (images == null) {
            images = new ArrayList<Image>();
            for (int size : SIZES) {
                Image img = load("emblem-" + size + ".png");
                if (img != null) {
                    images.add(img);
                }
            }
        }
        return images;
    }

    /** Самое крупное изображение значка или null, если значок недоступен. */
    public static Image large() {
        Image img = load("emblem.png");
        if (img != null) {
            return img;
        }
        List<Image> all = images();
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    /** Ставит значок окну: заголовок окна и панель задач. */
    public static void applyTo(Window window) {
        List<Image> all = images();
        if (window != null && !all.isEmpty()) {
            window.setIconImages(all);
        }
    }

    /**
     * Ставит значок приложению целиком.
     *
     * В Linux (в том числе в Астра-Линукс) панель задач и переключатель окон
     * берут значок не от окна, а от приложения, и окно там остаётся
     * со стандартным значком, сколько ему ни ставь свой. Нужный для этого
     * класс java.awt.Taskbar появился только в Java 9, а программа собирается
     * под Java 8, поэтому обращение к нему выполняется отражением: на Java 8
     * вызов просто ничего не сделает, на Java 9 и новее – поставит значок.
     */
    public static void applyToApplication() {
        Image img = large();
        if (img == null) {
            return;
        }
        try {
            Class<?> taskbar = Class.forName("java.awt.Taskbar");
            Method supported = taskbar.getMethod("isTaskbarSupported");
            if (!Boolean.TRUE.equals(supported.invoke(null))) {
                return;
            }
            Object instance = taskbar.getMethod("getTaskbar").invoke(null);
            taskbar.getMethod("setIconImage", Image.class).invoke(instance, img);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Java 8, либо оконная среда значка приложения не поддерживает:
            // окно всё равно получит свой значок через applyTo(Window)
        }
    }

    /**
     * Чтение одного файла значка из ресурсов jar-а.
     *
     * Путь указывается от корня jar-а (с ведущей косой чертой): сам класс
     * лежит в пакете ru.vka.upo.ui, а файлы значка – в ru/vka/upo, и без
     * ведущей косой черты поиск шёл бы в папке пакета и ничего не находил.
     */
    private static Image load(String name) {
        try (InputStream in = Emblem.class.getResourceAsStream("/ru/vka/upo/" + name)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }
}
