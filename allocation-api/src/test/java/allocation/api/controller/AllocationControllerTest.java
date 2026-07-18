package allocation.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AllocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void explicitGreedyAllocationReturnsExpectedResult() throws Exception {
        postAllocation(allocationRequest("""
                "selectionMode": "EXPLICIT",
                "algorithm": "GREEDY"
                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.executedAlgorithm").value("GREEDY"))
                .andExpect(jsonPath("$.selectionMode").value("EXPLICIT"))
                .andExpect(jsonPath("$.statistics.totalPriorityScore").value(10))
                .andExpect(jsonPath("$.statistics.allocatedRequests").value(1))
                .andExpect(jsonPath("$.statistics.rejectedRequests").value(1));
    }

    @Test
    void explicitCpSatAllocationReturnsOptimalResult() throws Exception {
        postAllocation(allocationRequest("""
                "selectionMode": "EXPLICIT",
                "algorithm": "CP_SAT",
                "cpSatTimeLimitSeconds": 1.0
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedAlgorithm").value("CP_SAT"))
                .andExpect(jsonPath("$.statistics.totalPriorityScore").value(19))
                .andExpect(jsonPath("$.statistics.algorithmStatus").value("OPTIMAL"));
    }

    @Test
    void automaticFastestAllocationExecutesGreedy() throws Exception {
        postAllocation(allocationRequest("""
                "selectionMode": "AUTO",
                "goal": "FASTEST"
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectionMode").value("AUTO"))
                .andExpect(jsonPath("$.requestedAlgorithm").value(nullValue()))
                .andExpect(jsonPath("$.executedAlgorithm").value("GREEDY"))
                .andExpect(jsonPath("$.goal").value("FASTEST"));
    }

    @Test
    void automaticBalancedSmallAllocationExecutesBacktracking() throws Exception {
        postAllocation(allocationRequest("""
                "selectionMode": "AUTO",
                "goal": "BALANCED",
                "backtrackingTimeLimitMs": 1000
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectionMode").value("AUTO"))
                .andExpect(jsonPath("$.requestedAlgorithm").value(nullValue()))
                .andExpect(jsonPath("$.executedAlgorithm").value("BACKTRACKING"))
                .andExpect(jsonPath("$.goal").value("BALANCED"))
                .andExpect(jsonPath("$.statistics.totalPriorityScore").value(19));
    }

    @Test
    void automaticAllocationWithoutGoalUsesBalancedDefault() throws Exception {
        postAllocation(allocationRequest("""
                "selectionMode": "AUTO",
                "backtrackingTimeLimitMs": 1000
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectionMode").value("AUTO"))
                .andExpect(jsonPath("$.executedAlgorithm").value("BACKTRACKING"))
                .andExpect(jsonPath("$.goal").value("BALANCED"));
    }

    @Test
    void compareReturnsResultsForAllAlgorithms() throws Exception {
        mockMvc.perform(post("/api/allocations/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compareRequest()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.results.GREEDY").exists())
                .andExpect(jsonPath("$.results.BACKTRACKING").exists())
                .andExpect(jsonPath("$.results.CP_SAT").exists())
                .andExpect(jsonPath("$.results.GREEDY.allocationResult.statistics.totalPriorityScore").value(10))
                .andExpect(jsonPath("$.results.BACKTRACKING.allocationResult.statistics.totalPriorityScore").value(19))
                .andExpect(jsonPath("$.results.CP_SAT.allocationResult.statistics.totalPriorityScore").value(19))
                .andExpect(jsonPath("$.bestTotalPriorityScore").value(19))
                .andExpect(jsonPath("$.bestScoreAlgorithms", containsInAnyOrder("BACKTRACKING", "CP_SAT")))
                .andExpect(jsonPath("$.fastestAlgorithm").exists());
    }

    @Test
    void explicitAllocationWithoutAlgorithmReturnsBadRequest() throws Exception {
        expectBadAllocationRequest(allocationRequest("""
                "selectionMode": "EXPLICIT"
                """));
    }

    @Test
    void automaticAllocationWithAlgorithmReturnsBadRequest() throws Exception {
        expectBadAllocationRequest(allocationRequest("""
                "selectionMode": "AUTO",
                "algorithm": "GREEDY"
                """));
    }

    @Test
    void automaticAllocationWithInvalidGoalReturnsBadRequest() throws Exception {
        expectBadAllocationRequest(allocationRequest("""
                "selectionMode": "AUTO",
                "goal": "QUALITY"
                """));
    }

    @Test
    void unknownEnumValueReturnsBadRequest() throws Exception {
        expectBadAllocationRequest(allocationRequest("""
                "selectionMode": "UNKNOWN",
                "algorithm": "GREEDY"
                """));
    }

    @Test
    void nonPositiveTimeLimitReturnsBadRequest() throws Exception {
        expectBadAllocationRequest(allocationRequest("""
                "selectionMode": "EXPLICIT",
                "algorithm": "GREEDY",
                "backtrackingTimeLimitMs": 0
                """));
    }

    @Test
    void nullResourcesReturnsBadRequest() throws Exception {
        expectBadAllocationRequest("""
                {
                  "selectionMode": "EXPLICIT",
                  "algorithm": "GREEDY",
                  "resources": null,
                  "requests": %s
                }
                """.formatted(requestsJson()));
    }

    @Test
    void nullRequestsReturnsBadRequest() throws Exception {
        expectBadAllocationRequest("""
                {
                  "selectionMode": "EXPLICIT",
                  "algorithm": "GREEDY",
                  "resources": %s,
                  "requests": null
                }
                """.formatted(resourcesJson()));
    }

    private ResultActions postAllocation(String json) throws Exception {
        return mockMvc.perform(post("/api/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private void expectBadAllocationRequest(String json) throws Exception {
        postAllocation(json)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/allocations"));
    }

    private String allocationRequest(String modeFields) {
        return """
                {
                  %s,
                  "resources": %s,
                  "requests": %s
                }
                """.formatted(modeFields, resourcesJson(), requestsJson());
    }

    private String compareRequest() {
        return """
                {
                  "backtrackingTimeLimitMs": 1000,
                  "cpSatTimeLimitSeconds": 1.0,
                  "resources": %s,
                  "requests": %s
                }
                """.formatted(resourcesJson(), requestsJson());
    }

    private String resourcesJson() {
        return """
                [
                  {
                    "id": "R_BIG",
                    "name": "Large room",
                    "type": "ROOM",
                    "capacities": { "people": 100 },
                    "availability": [
                      {
                        "start": "2026-07-01T08:00:00",
                        "end": "2026-07-01T18:00:00"
                      }
                    ]
                  },
                  {
                    "id": "R_SMALL",
                    "name": "Small room",
                    "type": "ROOM",
                    "capacities": { "people": 30 },
                    "availability": [
                      {
                        "start": "2026-07-01T08:00:00",
                        "end": "2026-07-01T18:00:00"
                      }
                    ]
                  }
                ]
                """;
    }

    private String requestsJson() {
        return """
                [
                  {
                    "id": "REQ_SMALL",
                    "name": "Small exam",
                    "startTime": "2026-07-01T10:00:00",
                    "durationMinutes": 120,
                    "priority": 10,
                    "resourceRequirements": [
                      {
                        "resourceType": "ROOM",
                        "quantity": 1,
                        "requiredCapacities": { "people": 30 }
                      }
                    ]
                  },
                  {
                    "id": "REQ_BIG",
                    "name": "Large exam",
                    "startTime": "2026-07-01T10:00:00",
                    "durationMinutes": 120,
                    "priority": 9,
                    "resourceRequirements": [
                      {
                        "resourceType": "ROOM",
                        "quantity": 1,
                        "requiredCapacities": { "people": 100 }
                      }
                    ]
                  }
                ]
                """;
    }
}
