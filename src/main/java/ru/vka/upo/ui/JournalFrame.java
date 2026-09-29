package ru.vka.upo.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import ru.vka.upo.model.Journal;
import ru.vka.upo.model.JournalRecord;
import ru.vka.upo.model.Notebook;

/**
 * Журнал обучающихся в режиме преподавателя: кто работает с программой,
 * с какой оценкой за входной контроль и насколько продвинулась работа.
 *
 * Преподаватель может удалить запись – тогда обучающийся снова допускается
 * к входному контролю (например, после неудовлетворительной оценки), –
 * или очистить журнал целиком. Удаляются только файлы записей журнала,
 * папка «БД» и посторонние файлы в ней не затрагиваются.
 *
 * Поведение окна то же, что у {@link SummaryFrame}: один экземпляр,
 * главное окно на время показа недоступно.
 */
public class JournalFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Единственный открытый экземпляр окна – второй не заводится. */
    private static JournalFrame instance;

    private final Journal journal;
    private final boolean fullScreenOwner;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final JLabel head = new JLabel();

    private JournalFrame(Window owner, Journal journal) {
        super("Журнал обучающихся");
        this.journal = journal;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        fullScreenOwner = owner instanceof MainFrame && ((MainFrame) owner).isFullScreenMode();

        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoCreateRowSorter(false);

        JButton delete = new JButton("Удалить запись");
        delete.addActionListener(e -> deleteSelected());
        JButton clear = new JButton("Очистить журнал");
        clear.addActionListener(e -> deleteAll());
        JButton refresh = new JButton("Обновить");
        refresh.addActionListener(e -> reload());
        JButton close = new JButton("Закрыть");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttons.add(delete);
        buttons.add(clear);
        buttons.add(refresh);
        buttons.add(close);

        head.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 8, 0, 8), scroll.getBorder()));
        setLayout(new BorderLayout());
        add(head, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1000, screen.width - 80), Math.min(600, screen.height - 80));
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
        reload();
    }

    /**
     * Показывает журнал. Если окно уже открыто, второй раз не создаётся.
     *
     * @param journal журнал программы или null, если он отключён настройкой
     */
    public static void show(Component parent, Journal journal) {
        if (journal == null) {
            JOptionPane.showMessageDialog(parent,
                    "Журнал обучающихся не ведётся: он отключён настройкой "
                    + "test.journal = false в файле «Настройки.properties».",
                    "Журнал обучающихся", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(parent);
        if (instance != null && instance.isDisplayable()) {
            instance.reload();
            instance.bringToFront();
            return;
        }
        final Window ownerFinal = owner;
        instance = new JournalFrame(owner, journal);
        instance.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
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

    /** Окно без показа – для проверочных программ. */
    static JournalFrame createHidden(Journal journal) {
        return new JournalFrame(null, journal);
    }

    /** Перечитывает журнал; повреждённые записи при этом удаляются. */
    void reload() {
        List<JournalRecord> list = journal.list();
        Collections.sort(list, (a, b) -> {
            int c = a.getGroup().compareToIgnoreCase(b.getGroup());
            return c != 0 ? c : a.getName().compareToIgnoreCase(b.getName());
        });
        model.setRows(list);
        head.setText("<html>Записей в журнале: <b>" + list.size() + "</b>. Папка: "
                + escape(journal.getFolder().toString()) + "<br>Удалённая запись позволяет "
                + "обучающемуся пройти входной контроль заново; его работа при этом "
                + "не сохраняется.</html>");
    }

    /** Число записей в таблице (для проверочных программ). */
    int rowCount() {
        return model.getRowCount();
    }

    private void deleteSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(this, "Выделите в таблице записи, которые нужно удалить.",
                    "Журнал обучающихся", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<JournalRecord> chosen = new ArrayList<JournalRecord>();
        for (int r : rows) {
            chosen.add(model.row(r));
        }
        String what = chosen.size() == 1
                ? "запись «" + chosen.get(0).getName() + ", группа " + chosen.get(0).getGroup() + "»"
                : "выделенные записи (" + chosen.size() + ")";
        int answer = JOptionPane.showConfirmDialog(this, "Удалить " + what
                + "? Отменить это будет нельзя.", "Журнал обучающихся",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        deleteRecords(chosen);
    }

    /** Удаляет заданные записи и перечитывает журнал. */
    void deleteRecords(List<JournalRecord> chosen) {
        for (JournalRecord r : chosen) {
            journal.delete(r);
        }
        reload();
    }

    private void deleteAll() {
        int n = model.getRowCount();
        if (n == 0) {
            JOptionPane.showMessageDialog(this, "Журнал пуст.", "Журнал обучающихся",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Удалить все " + n
                + " записей журнала? Отменить это будет нельзя.", "Журнал обучающихся",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        journal.deleteAll();
        reload();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Таблица записей журнала. */
    private static class Model extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private static final String[] NAMES = {
            "Фамилия, инициалы", "Группа", "Вариант", "Оценка за контроль",
            "Заполнено пунктов", "Выводов записано", "Последнее изменение"
        };

        private List<JournalRecord> rows = new ArrayList<JournalRecord>();

        void setRows(List<JournalRecord> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        JournalRecord row(int i) {
            return rows.get(i);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return NAMES.length;
        }

        @Override
        public String getColumnName(int c) {
            return NAMES[c];
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }

        @Override
        public Object getValueAt(int r, int c) {
            JournalRecord j = rows.get(r);
            switch (c) {
                case 0: return j.getName();
                case 1: return j.getGroup();
                case 2: return j.getVariantNumber() > 0 ? String.valueOf(j.getVariantNumber()) : "–";
                case 3: return j.getScore() == null ? "–" : String.valueOf(j.getScore());
                case 4: return j.readyCount() + " из " + Notebook.Item.values().length;
                case 5: return j.conclusionCount() + " из " + Notebook.Item.values().length;
                default:
                    return new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(j.getModified()));
            }
        }
    }
}
