package allocation.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Faculty exam scheduling payload containing only domain input data.
 */
public record FacultyExamScheduleRequest(
        List<ExamSlotDto> slots,
        List<ExamDto> exams,
        List<FacultyRoomDto> rooms,
        List<InvigilatorDto> invigilators
) {

    public record ExamSlotDto(
            String id,
            LocalDateTime start,
            LocalDateTime end
    ) {
    }

    public record ExamDto(
            String id,
            String code,
            String name,
            Integer studentCount,
            Integer durationMinutes,
            Integer requiredInvigilators,
            List<String> studentGroups
    ) {
    }

    public record FacultyRoomDto(
            String id,
            String name,
            Integer capacity,
            List<TimeWindowDto> availability
    ) {
    }

    public record InvigilatorDto(
            String id,
            String name,
            List<TimeWindowDto> availability
    ) {
    }

    public record TimeWindowDto(
            LocalDateTime start,
            LocalDateTime end
    ) {
    }
}
