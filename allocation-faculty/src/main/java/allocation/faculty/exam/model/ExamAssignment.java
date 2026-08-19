package allocation.faculty.exam.model;

import allocation.model.TimeWindow;

import java.util.List;

/**
 * A scheduled exam with its selected slot, room, and invigilators.
 */
public final class ExamAssignment {

    private final Exam exam;
    private final ExamSlot slot;
    private final FacultyRoom room;
    private final List<Invigilator> invigilators;
    private final TimeWindow actualTimeWindow;

    public ExamAssignment(
            Exam exam,
            ExamSlot slot,
            FacultyRoom room,
            List<Invigilator> invigilators
    ) {
        if (exam == null || slot == null || room == null || invigilators == null) {
            throw new IllegalArgumentException("Exam assignment values must not be null.");
        }
        if (invigilators.stream().anyMatch(invigilator -> invigilator == null)) {
            throw new IllegalArgumentException("Assigned invigilators must not contain null elements.");
        }

        this.exam = exam;
        this.slot = slot;
        this.room = room;
        this.invigilators = List.copyOf(invigilators);
        this.actualTimeWindow = slot.examTimeWindow(exam.getDurationMinutes());
    }

    public Exam getExam() {
        return exam;
    }

    public ExamSlot getSlot() {
        return slot;
    }

    public FacultyRoom getRoom() {
        return room;
    }

    public List<Invigilator> getInvigilators() {
        return invigilators;
    }

    public TimeWindow getActualTimeWindow() {
        return actualTimeWindow;
    }
}
