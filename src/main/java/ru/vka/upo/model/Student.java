package ru.vka.upo.model;

/**
 * Сведения о том, кто работает с программой.
 *
 * Обучающийся называет фамилию с инициалами, номер группы и номер по списку
 * в журнале; по номеру по списку ему назначается вариант задания. Эти
 * сведения попадают в шапку отчёта. Преподаватель входит по паролю, и
 * ни варианта, ни отчёта у него нет.
 */
public class Student {

    /** Кто работает с программой. */
    public enum Role {
        /** Обучающийся: входной контроль, расчёты, рабочая тетрадь, отчёт. */
        STUDENT,
        /** Преподаватель: расчёт всех пунктов задания сразу. */
        TEACHER
    }

    private Role role = Role.STUDENT;
    private String name = "";
    private String group = "";
    private int listNumber;
    private VariantTable.Variant variant;
    /** Оценка за входной контроль знаний; null, если контроль не проводился. */
    private Integer testScore;

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isTeacher() {
        return role == Role.TEACHER;
    }

    /** Фамилия и инициалы. */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
    }

    /** Номер учебной группы. */
    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group == null ? "" : group.trim();
    }

    /** Номер по списку в журнале группы. */
    public int getListNumber() {
        return listNumber;
    }

    /** Задаёт номер по списку и назначает по нему вариант задания. */
    public void setListNumber(int listNumber) {
        this.listNumber = listNumber;
        this.variant = VariantTable.byListNumber(listNumber);
    }

    /** Назначенный вариант задания или null, если номер не задан. */
    public VariantTable.Variant getVariant() {
        return variant;
    }

    /** Номер назначенного варианта или ноль, если он не назначен. */
    public int getVariantNumber() {
        return variant == null ? 0 : variant.getNumber();
    }

    /** Оценка за входной контроль знаний или null, если он не проводился. */
    public Integer getTestScore() {
        return testScore;
    }

    /** Записывает оценку, полученную при успешном прохождении контроля. */
    public void setTestScore(Integer testScore) {
        this.testScore = testScore;
    }

    /** Строка для шапки отчёта и строки состояния. */
    @Override
    public String toString() {
        if (isTeacher()) {
            return "преподаватель";
        }
        StringBuilder sb = new StringBuilder(name);
        if (!group.isEmpty()) {
            sb.append(", группа ").append(group);
        }
        if (variant != null) {
            sb.append(", вариант ").append(variant.getNumber());
        }
        return sb.toString();
    }
}
