package allocation.api.controller;

import allocation.api.dto.FacultyExamScheduleRequest;
import allocation.api.dto.FacultyExamScheduleResponse;
import allocation.api.service.FacultyExamScheduleApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for stateless faculty exam scheduling.
 */
@RestController
@RequestMapping("/api/faculty/exam-schedule")
public class FacultyExamScheduleController {

    private final FacultyExamScheduleApplicationService applicationService;

    public FacultyExamScheduleController(FacultyExamScheduleApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public FacultyExamScheduleResponse schedule(@RequestBody FacultyExamScheduleRequest request) {
        return applicationService.schedule(request);
    }
}
