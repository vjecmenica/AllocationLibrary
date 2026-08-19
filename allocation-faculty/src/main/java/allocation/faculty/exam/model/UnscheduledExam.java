package allocation.faculty.exam.model;

/**
 * An exam for which no permitted assignment was selected.
 */
public final class UnscheduledExam {

    private final Exam exam;
    private final String reason;

    public UnscheduledExam(Exam exam, String reason) {
        if (exam == null) {
            throw new IllegalArgumentException("Unscheduled exam must not be null.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Unscheduled exam reason must not be blank.");
        }

        this.exam = exam;
        this.reason = reason;
    }

    public Exam getExam() {
        return exam;
    }

    public String getReason() {
        return reason;
    }
}
