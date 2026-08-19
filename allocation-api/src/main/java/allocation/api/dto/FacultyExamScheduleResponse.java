package allocation.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Domain-focused response for a faculty exam scheduling run.
 */
public record FacultyExamScheduleResponse(
        List<ExamAssignmentDto> assignments,
        List<UnscheduledExamDto> unscheduledExams,
        StatisticsDto statistics
) {

    public record ExamAssignmentDto(
            String examId,
            String examCode,
            String examName,
            int studentCount,
            String slotId,
            LocalDateTime slotStart,
            LocalDateTime slotEnd,
            LocalDateTime actualEnd,
            RoomDto room,
            List<InvigilatorDto> invigilators
    ) {
    }

    public record RoomDto(
            String id,
            String name,
            int capacity
    ) {
    }

    public record InvigilatorDto(
            String id,
            String name
    ) {
    }

    public record UnscheduledExamDto(
            String examId,
            String examCode,
            String examName,
            int studentCount,
            String reason
    ) {
    }

    public record StatisticsDto(
            int totalExams,
            int scheduledExams,
            int unscheduledExams,
            String solverStatus,
            long executionTimeMs,
            boolean stoppedByLimit
    ) {
    }
}
