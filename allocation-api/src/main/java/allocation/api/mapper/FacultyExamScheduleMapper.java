package allocation.api.mapper;

import allocation.api.dto.FacultyExamScheduleRequest;
import allocation.api.dto.FacultyExamScheduleResponse;
import allocation.faculty.exam.model.Exam;
import allocation.faculty.exam.model.ExamAssignment;
import allocation.faculty.exam.model.ExamScheduleRequest;
import allocation.faculty.exam.model.ExamScheduleResult;
import allocation.faculty.exam.model.ExamScheduleStatistics;
import allocation.faculty.exam.model.ExamSlot;
import allocation.faculty.exam.model.FacultyRoom;
import allocation.faculty.exam.model.Invigilator;
import allocation.faculty.exam.model.UnscheduledExam;
import allocation.model.TimeWindow;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps faculty REST payloads to faculty domain models and results back to DTOs.
 */
@Component
public class FacultyExamScheduleMapper {

    public ExamScheduleRequest toDomain(FacultyExamScheduleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Faculty exam schedule request must not be null.");
        }

        return new ExamScheduleRequest(
                mapSlots(request.slots()),
                mapExams(request.exams()),
                mapRooms(request.rooms()),
                mapInvigilators(request.invigilators())
        );
    }

    public FacultyExamScheduleResponse toResponse(ExamScheduleResult result) {
        if (result == null) {
            throw new IllegalArgumentException("Faculty exam schedule result must not be null.");
        }

        return new FacultyExamScheduleResponse(
                result.getAssignments().stream().map(this::toAssignmentDto).toList(),
                result.getUnscheduledExams().stream().map(this::toUnscheduledDto).toList(),
                toStatisticsDto(result.getStatistics())
        );
    }

    private List<ExamSlot> mapSlots(List<FacultyExamScheduleRequest.ExamSlotDto> slots) {
        requireList(slots, "Exam slot list");
        return slots.stream().map(this::toExamSlot).toList();
    }

    private ExamSlot toExamSlot(FacultyExamScheduleRequest.ExamSlotDto slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Exam slot list must not contain null elements.");
        }
        return new ExamSlot(slot.id(), slot.start(), slot.end());
    }

    private List<Exam> mapExams(List<FacultyExamScheduleRequest.ExamDto> exams) {
        requireList(exams, "Exam list");
        return exams.stream().map(this::toExam).toList();
    }

    private Exam toExam(FacultyExamScheduleRequest.ExamDto exam) {
        if (exam == null) {
            throw new IllegalArgumentException("Exam list must not contain null elements.");
        }
        if (exam.studentCount() == null) {
            throw new IllegalArgumentException("Student count must not be null.");
        }
        if (exam.durationMinutes() == null) {
            throw new IllegalArgumentException("Exam duration must not be null.");
        }
        if (exam.requiredInvigilators() == null) {
            throw new IllegalArgumentException("Required invigilator count must not be null.");
        }

        return new Exam(
                exam.id(),
                exam.code(),
                exam.name(),
                exam.studentCount(),
                exam.durationMinutes(),
                exam.requiredInvigilators(),
                exam.studentGroups()
        );
    }

    private List<FacultyRoom> mapRooms(List<FacultyExamScheduleRequest.FacultyRoomDto> rooms) {
        requireList(rooms, "Room list");
        return rooms.stream().map(this::toRoom).toList();
    }

    private FacultyRoom toRoom(FacultyExamScheduleRequest.FacultyRoomDto room) {
        if (room == null) {
            throw new IllegalArgumentException("Room list must not contain null elements.");
        }
        if (room.capacity() == null) {
            throw new IllegalArgumentException("Room capacity must not be null.");
        }

        return new FacultyRoom(
                room.id(),
                room.name(),
                room.capacity(),
                mapAvailability(room.availability(), "Room availability")
        );
    }

    private List<Invigilator> mapInvigilators(
            List<FacultyExamScheduleRequest.InvigilatorDto> invigilators
    ) {
        requireList(invigilators, "Invigilator list");
        return invigilators.stream().map(this::toInvigilator).toList();
    }

    private Invigilator toInvigilator(FacultyExamScheduleRequest.InvigilatorDto invigilator) {
        if (invigilator == null) {
            throw new IllegalArgumentException("Invigilator list must not contain null elements.");
        }
        return new Invigilator(
                invigilator.id(),
                invigilator.name(),
                mapAvailability(invigilator.availability(), "Invigilator availability")
        );
    }

    private List<TimeWindow> mapAvailability(
            List<FacultyExamScheduleRequest.TimeWindowDto> availability,
            String label
    ) {
        if (availability == null) {
            return null;
        }
        return availability.stream()
                .map(window -> toTimeWindow(window, label))
                .toList();
    }

    private TimeWindow toTimeWindow(FacultyExamScheduleRequest.TimeWindowDto window, String label) {
        if (window == null) {
            throw new IllegalArgumentException(label + " must not contain null elements.");
        }
        return new TimeWindow(window.start(), window.end());
    }

    private FacultyExamScheduleResponse.ExamAssignmentDto toAssignmentDto(ExamAssignment assignment) {
        Exam exam = assignment.getExam();
        FacultyRoom room = assignment.getRoom();
        return new FacultyExamScheduleResponse.ExamAssignmentDto(
                exam.getId(),
                exam.getCode(),
                exam.getName(),
                exam.getStudentCount(),
                assignment.getSlot().getId(),
                assignment.getSlot().getStart(),
                assignment.getSlot().getEnd(),
                assignment.getActualTimeWindow().getEnd(),
                new FacultyExamScheduleResponse.RoomDto(
                        room.getId(),
                        room.getName(),
                        room.getCapacity()
                ),
                assignment.getInvigilators().stream()
                        .map(invigilator -> new FacultyExamScheduleResponse.InvigilatorDto(
                                invigilator.getId(),
                                invigilator.getName()
                        ))
                        .toList()
        );
    }

    private FacultyExamScheduleResponse.UnscheduledExamDto toUnscheduledDto(UnscheduledExam unscheduledExam) {
        Exam exam = unscheduledExam.getExam();
        return new FacultyExamScheduleResponse.UnscheduledExamDto(
                exam.getId(),
                exam.getCode(),
                exam.getName(),
                exam.getStudentCount(),
                unscheduledExam.getReason()
        );
    }

    private FacultyExamScheduleResponse.StatisticsDto toStatisticsDto(ExamScheduleStatistics statistics) {
        return new FacultyExamScheduleResponse.StatisticsDto(
                statistics.getTotalExams(),
                statistics.getScheduledExams(),
                statistics.getUnscheduledExams(),
                statistics.getSolverStatus(),
                statistics.getExecutionTimeMs(),
                statistics.isStoppedByLimit()
        );
    }

    private void requireList(List<?> values, String label) {
        if (values == null) {
            throw new IllegalArgumentException(label + " must not be null.");
        }
    }
}
