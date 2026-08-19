package allocation.faculty.exam.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Complete input for one stateless faculty exam scheduling run.
 */
public final class ExamScheduleRequest {

    private final List<ExamSlot> slots;
    private final List<Exam> exams;
    private final List<FacultyRoom> rooms;
    private final List<Invigilator> invigilators;

    public ExamScheduleRequest(
            List<ExamSlot> slots,
            List<Exam> exams,
            List<FacultyRoom> rooms,
            List<Invigilator> invigilators
    ) {
        this.slots = copyAndValidate(slots, "Exam slot list");
        this.exams = copyAndValidate(exams, "Exam list");
        this.rooms = copyAndValidate(rooms, "Room list");
        this.invigilators = copyAndValidate(invigilators, "Invigilator list");

        validateUniqueIds(this.slots, ExamSlot::getId, "Exam slot");
        validateUniqueIds(this.exams, Exam::getId, "Exam");
        validateUniqueIds(this.rooms, FacultyRoom::getId, "Room");
        validateUniqueIds(this.invigilators, Invigilator::getId, "Invigilator");
    }

    private static <T> List<T> copyAndValidate(List<T> values, String label) {
        if (values == null) {
            throw new IllegalArgumentException(label + " must not be null.");
        }
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(label + " must not contain null elements.");
        }
        return List.copyOf(values);
    }

    private static <T> void validateUniqueIds(
            List<T> values,
            Function<T, String> idExtractor,
            String label
    ) {
        Set<String> ids = new HashSet<>();
        for (T value : values) {
            String id = idExtractor.apply(value);
            if (!ids.add(id)) {
                throw new IllegalArgumentException(label + " ID must be unique: " + id);
            }
        }
    }

    public List<ExamSlot> getSlots() {
        return slots;
    }

    public List<Exam> getExams() {
        return exams;
    }

    public List<FacultyRoom> getRooms() {
        return rooms;
    }

    public List<Invigilator> getInvigilators() {
        return invigilators;
    }
}
