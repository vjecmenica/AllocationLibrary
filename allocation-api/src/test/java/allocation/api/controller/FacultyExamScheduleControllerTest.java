package allocation.api.controller;

import allocation.api.dto.FacultyExamScheduleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FacultyExamScheduleControllerTest {

    private static final String ENDPOINT = "/api/faculty/exam-schedule";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validRequestReturnsScheduledAssignmentAndStatistics() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.assignments.length()").value(1))
                .andExpect(jsonPath("$.assignments[0].examId").value("E1"))
                .andExpect(jsonPath("$.assignments[0].examCode").value("CS101"))
                .andExpect(jsonPath("$.assignments[0].examName").value("Algorithms"))
                .andExpect(jsonPath("$.assignments[0].studentCount").value(30))
                .andExpect(jsonPath("$.assignments[0].slotId").value("S1"))
                .andExpect(jsonPath("$.assignments[0].slotStart").value("2026-07-01T10:00:00"))
                .andExpect(jsonPath("$.assignments[0].slotEnd").value("2026-07-01T12:00:00"))
                .andExpect(jsonPath("$.assignments[0].actualEnd").value("2026-07-01T11:30:00"))
                .andExpect(jsonPath("$.assignments[0].room.id").value("R1"))
                .andExpect(jsonPath("$.assignments[0].invigilators[0].id").value("I1"))
                .andExpect(jsonPath("$.unscheduledExams.length()").value(0))
                .andExpect(jsonPath("$.statistics.totalExams").value(1))
                .andExpect(jsonPath("$.statistics.scheduledExams").value(1))
                .andExpect(jsonPath("$.statistics.unscheduledExams").value(0))
                .andExpect(jsonPath("$.statistics.solverStatus").value("OPTIMAL"))
                .andExpect(jsonPath("$.statistics.stoppedByLimit").value(false));
    }

    @Test
    void impossibleExamIsReturnedAsUnscheduled() throws Exception {
        String request = validRequest().replace("\"capacity\": 50", "\"capacity\": 20");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments.length()").value(0))
                .andExpect(jsonPath("$.unscheduledExams.length()").value(1))
                .andExpect(jsonPath("$.unscheduledExams[0].examId").value("E1"))
                .andExpect(jsonPath("$.unscheduledExams[0].reason", not(blankOrNullString())))
                .andExpect(jsonPath("$.statistics.scheduledExams").value(0))
                .andExpect(jsonPath("$.statistics.unscheduledExams").value(1));
    }

    @Test
    void duplicateExamIdReturnsBadRequest() throws Exception {
        String duplicateExam = """
                {
                  "id": "E1",
                  "code": "CS102",
                  "name": "Data Structures",
                  "studentCount": 20,
                  "durationMinutes": 60,
                  "requiredInvigilators": 0,
                  "studentGroups": ["SI1"]
                }
                """;
        String request = validRequest().replace(
                "\n  ],\n  \"rooms\"",
                ",\n" + duplicateExam + "  ],\n  \"rooms\""
        );

        expectBadRequest(request)
                .andExpect(jsonPath("$.message").value("Exam ID must be unique: E1"));
    }

    @Test
    void invalidDateReturnsBadRequest() throws Exception {
        expectBadRequest(validRequest().replace("2026-07-01T10:00:00", "not-a-date"));
    }

    @Test
    void missingRequiredListReturnsBadRequest() throws Exception {
        expectBadRequest(validRequest().replace("\"invigilators\": [", "\"invigilatorsMissing\": ["))
                .andExpect(jsonPath("$.message").value("Invigilator list must not be null."));
    }

    @Test
    void nullRequestBodyReturnsBadRequest() throws Exception {
        expectBadRequest("null");
    }

    @Test
    void facultyRequestDoesNotRequireAlgorithmSelectionOrSolverLimits() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").doesNotExist())
                .andExpect(jsonPath("$.selectionMode").doesNotExist())
                .andExpect(jsonPath("$.cpSatTimeLimitSeconds").doesNotExist())
                .andExpect(jsonPath("$.backtrackingTimeLimitMs").doesNotExist());
    }

    @Test
    void facultyRequestDtoContainsOnlyDomainInputs() {
        Set<String> componentNames = Arrays.stream(FacultyExamScheduleRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertFalse(componentNames.contains("selectionMode"));
        assertFalse(componentNames.contains("algorithm"));
        assertFalse(componentNames.contains("goal"));
        assertFalse(componentNames.contains("cpSatTimeLimitSeconds"));
        assertFalse(componentNames.contains("backtrackingTimeLimitMs"));
    }

    private org.springframework.test.web.servlet.ResultActions expectBadRequest(String body) throws Exception {
        return mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", not(blankOrNullString())))
                .andExpect(jsonPath("$.path").value(ENDPOINT));
    }

    private String validRequest() {
        return """
                {
                  "slots": [
                    {
                      "id": "S1",
                      "start": "2026-07-01T10:00:00",
                      "end": "2026-07-01T12:00:00"
                    }
                  ],
                  "exams": [
                    {
                      "id": "E1",
                      "code": "CS101",
                      "name": "Algorithms",
                      "studentCount": 30,
                      "durationMinutes": 90,
                      "requiredInvigilators": 1,
                      "studentGroups": ["SI2"]
                    }
                  ],
                  "rooms": [
                    {
                      "id": "R1",
                      "name": "Large room",
                      "capacity": 50,
                      "availability": [
                        {
                          "start": "2026-07-01T08:00:00",
                          "end": "2026-07-01T18:00:00"
                        }
                      ]
                    }
                  ],
                  "invigilators": [
                    {
                      "id": "I1",
                      "name": "Professor One",
                      "availability": [
                        {
                          "start": "2026-07-01T08:00:00",
                          "end": "2026-07-01T18:00:00"
                        }
                      ]
                    }
                  ]
                }
                """;
    }
}
