package allocation.faculty.exam.model;

import allocation.model.TimeWindow;

import java.time.LocalDateTime;

/**
 * A permitted discrete start time for an exam.
 */
public final class ExamSlot {

    private final String id;
    private final TimeWindow window;

    public ExamSlot(String id, LocalDateTime start, LocalDateTime end) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Exam slot ID must not be blank.");
        }

        this.id = id;
        this.window = new TimeWindow(start, end);
    }

    public boolean canFit(int durationMinutes) {
        if (durationMinutes <= 0) {
            return false;
        }

        return !getStart().plusMinutes(durationMinutes).isAfter(getEnd());
    }

    public TimeWindow examTimeWindow(int durationMinutes) {
        if (!canFit(durationMinutes)) {
            throw new IllegalArgumentException("Exam duration does not fit inside the selected slot.");
        }

        return new TimeWindow(getStart(), getStart().plusMinutes(durationMinutes));
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getStart() {
        return window.getStart();
    }

    public LocalDateTime getEnd() {
        return window.getEnd();
    }
}
