package allocation.api.service;

import allocation.api.dto.FacultyExamScheduleRequest;
import allocation.api.dto.FacultyExamScheduleResponse;
import allocation.api.mapper.FacultyExamScheduleMapper;
import allocation.faculty.exam.model.ExamScheduleResult;
import allocation.faculty.exam.solver.CpSatExamScheduler;
import org.springframework.stereotype.Service;

/**
 * Connects the faculty scheduling REST layer to the domain-specific scheduler.
 */
@Service
public class FacultyExamScheduleApplicationService {

    private final FacultyExamScheduleMapper mapper;
    private final CpSatExamScheduler scheduler;

    public FacultyExamScheduleApplicationService(FacultyExamScheduleMapper mapper) {
        this.mapper = mapper;
        this.scheduler = new CpSatExamScheduler();
    }

    public FacultyExamScheduleResponse schedule(FacultyExamScheduleRequest request) {
        validateRequest(request);
        ExamScheduleResult result = scheduler.schedule(mapper.toDomain(request));
        return mapper.toResponse(result);
    }

    private void validateRequest(FacultyExamScheduleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null.");
        }
        if (request.slots() == null) {
            throw new IllegalArgumentException("Exam slot list must not be null.");
        }
        if (request.exams() == null) {
            throw new IllegalArgumentException("Exam list must not be null.");
        }
        if (request.rooms() == null) {
            throw new IllegalArgumentException("Room list must not be null.");
        }
        if (request.invigilators() == null) {
            throw new IllegalArgumentException("Invigilator list must not be null.");
        }
    }
}
