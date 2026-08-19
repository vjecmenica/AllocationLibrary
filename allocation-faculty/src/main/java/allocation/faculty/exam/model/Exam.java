package allocation.faculty.exam.model;

import java.util.List;

/**
 * An exam whose slot and resources are selected by the faculty scheduler.
 */
public final class Exam {

    private final String id;
    private final String code;
    private final String name;
    private final int studentCount;
    private final int durationMinutes;
    private final int requiredInvigilators;
    private final List<String> studentGroups;

    public Exam(
            String id,
            String code,
            String name,
            int studentCount,
            int durationMinutes,
            int requiredInvigilators,
            List<String> studentGroups
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Exam ID must not be blank.");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Exam code must not be blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Exam name must not be blank.");
        }
        if (studentCount <= 0) {
            throw new IllegalArgumentException("Student count must be positive.");
        }
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Exam duration must be positive.");
        }
        if (requiredInvigilators < 0) {
            throw new IllegalArgumentException("Required invigilator count must not be negative.");
        }

        List<String> providedGroups = studentGroups == null ? List.of() : studentGroups;
        if (providedGroups.stream().anyMatch(group -> group == null || group.isBlank())) {
            throw new IllegalArgumentException("Student groups must not contain blank values.");
        }
        List<String> safeGroups = List.copyOf(providedGroups);

        this.id = id;
        this.code = code;
        this.name = name;
        this.studentCount = studentCount;
        this.durationMinutes = durationMinutes;
        this.requiredInvigilators = requiredInvigilators;
        this.studentGroups = safeGroups;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getStudentCount() {
        return studentCount;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getRequiredInvigilators() {
        return requiredInvigilators;
    }

    public List<String> getStudentGroups() {
        return studentGroups;
    }
}
