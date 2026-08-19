package allocation.faculty.exam.model;

import java.util.List;

/**
 * Complete domain result returned by the faculty exam scheduler.
 */
public final class ExamScheduleResult {

    private final List<ExamAssignment> assignments;
    private final List<UnscheduledExam> unscheduledExams;
    private final ExamScheduleStatistics statistics;

    public ExamScheduleResult(
            List<ExamAssignment> assignments,
            List<UnscheduledExam> unscheduledExams,
            ExamScheduleStatistics statistics
    ) {
        if (assignments == null || unscheduledExams == null || statistics == null) {
            throw new IllegalArgumentException("Exam schedule result values must not be null.");
        }
        if (assignments.stream().anyMatch(assignment -> assignment == null)
                || unscheduledExams.stream().anyMatch(exam -> exam == null)) {
            throw new IllegalArgumentException("Exam schedule result lists must not contain null elements.");
        }

        this.assignments = List.copyOf(assignments);
        this.unscheduledExams = List.copyOf(unscheduledExams);
        this.statistics = statistics;
    }

    public List<ExamAssignment> getAssignments() {
        return assignments;
    }

    public List<UnscheduledExam> getUnscheduledExams() {
        return unscheduledExams;
    }

    public ExamScheduleStatistics getStatistics() {
        return statistics;
    }
}
