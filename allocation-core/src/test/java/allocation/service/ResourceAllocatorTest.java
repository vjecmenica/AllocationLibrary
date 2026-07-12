package allocation.service;

import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.testutil.TestScenarioFactory;
import allocation.testutil.TestScenarioFactory.ScenarioData;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static allocation.testutil.AllocationResultValidator.assertValid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceAllocatorTest {

    private final ResourceAllocator allocator = new ResourceAllocator();

    @Test
    void greedyTrapCanBeAllocatedThroughGreedyType() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        AllocationResult result = allocator.allocate(
                scenario.getResources(),
                scenario.getRequests(),
                AllocationAlgorithmType.GREEDY
        );

        assertValid(result, scenario.getResources(), scenario.getRequests());
        assertEquals(10, result.getStatistics().getTotalPriorityScore());
    }

    @Test
    void greedyTrapCanBeAllocatedThroughBacktrackingType() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        AllocationResult result = allocator.allocate(
                scenario.getResources(),
                scenario.getRequests(),
                AllocationAlgorithmType.BACKTRACKING
        );

        assertValid(result, scenario.getResources(), scenario.getRequests());
        assertEquals(19, result.getStatistics().getTotalPriorityScore());
    }

    @Test
    void greedyTrapCanBeAllocatedThroughCpSatType() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        AllocationResult result = allocator.allocate(
                scenario.getResources(),
                scenario.getRequests(),
                AllocationAlgorithmType.CP_SAT
        );

        assertValid(result, scenario.getResources(), scenario.getRequests());
        assertEquals(19, result.getStatistics().getTotalPriorityScore());
        assertEquals("OPTIMAL", result.getStatistics().getAlgorithmStatus());
    }

    @Test
    void allocationOptionsFactoryMethodsUseExpectedAlgorithmAndLimits() {
        AllocationOptions greedyOptions = AllocationOptions.greedy();
        AllocationOptions backtrackingOptions = AllocationOptions.backtracking(1000);
        AllocationOptions cpSatOptions = AllocationOptions.cpSat(2.5);
        AllocationOptions fullOptions = new AllocationOptions(AllocationAlgorithmType.CP_SAT, 2000, 3.5);

        assertEquals(AllocationAlgorithmType.GREEDY, greedyOptions.getAlgorithmType());
        assertEquals(AllocationOptions.DEFAULT_BACKTRACKING_TIME_LIMIT_MS, greedyOptions.getBacktrackingTimeLimitMs());
        assertEquals(AllocationOptions.DEFAULT_CP_SAT_TIME_LIMIT_SECONDS, greedyOptions.getCpSatTimeLimitSeconds(), 0.0001);

        assertEquals(AllocationAlgorithmType.BACKTRACKING, backtrackingOptions.getAlgorithmType());
        assertEquals(1000, backtrackingOptions.getBacktrackingTimeLimitMs());
        assertEquals(AllocationOptions.DEFAULT_CP_SAT_TIME_LIMIT_SECONDS, backtrackingOptions.getCpSatTimeLimitSeconds(), 0.0001);

        assertEquals(AllocationAlgorithmType.CP_SAT, cpSatOptions.getAlgorithmType());
        assertEquals(AllocationOptions.DEFAULT_BACKTRACKING_TIME_LIMIT_MS, cpSatOptions.getBacktrackingTimeLimitMs());
        assertEquals(2.5, cpSatOptions.getCpSatTimeLimitSeconds(), 0.0001);

        assertEquals(AllocationAlgorithmType.CP_SAT, fullOptions.getAlgorithmType());
        assertEquals(2000, fullOptions.getBacktrackingTimeLimitMs());
        assertEquals(3.5, fullOptions.getCpSatTimeLimitSeconds(), 0.0001);
    }

    @Test
    void customTimeLimitOptionsCanBePassedToAllocator() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        AllocationResult backtrackingResult = allocator.allocate(
                scenario.getResources(),
                scenario.getRequests(),
                AllocationOptions.backtracking(1000)
        );
        AllocationResult cpSatResult = allocator.allocate(
                scenario.getResources(),
                scenario.getRequests(),
                AllocationOptions.cpSat(2.5)
        );

        assertValid(backtrackingResult, scenario.getResources(), scenario.getRequests());
        assertValid(cpSatResult, scenario.getResources(), scenario.getRequests());
        assertEquals(19, backtrackingResult.getStatistics().getTotalPriorityScore());
        assertEquals(19, cpSatResult.getStatistics().getTotalPriorityScore());
        assertEquals("OPTIMAL", cpSatResult.getStatistics().getAlgorithmStatus());
    }

    @Test
    void explicitExecutionReturnsExpectedMetadataForAllAlgorithms() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        assertExplicitExecution(
                scenario,
                AllocationOptions.greedy(),
                AllocationAlgorithmType.GREEDY,
                10
        );
        assertExplicitExecution(
                scenario,
                AllocationOptions.backtracking(1000),
                AllocationAlgorithmType.BACKTRACKING,
                19
        );
        assertExplicitExecution(
                scenario,
                AllocationOptions.cpSat(2.5),
                AllocationAlgorithmType.CP_SAT,
                19
        );
    }

    @Test
    void automaticAllocationOptionsFactoryMethodsUseExpectedGoalAndLimits() {
        AutomaticAllocationOptions balanced = AutomaticAllocationOptions.balanced();
        AutomaticAllocationOptions fastest = AutomaticAllocationOptions.fastest();
        AutomaticAllocationOptions bestQuality = AutomaticAllocationOptions.bestQuality();
        AutomaticAllocationOptions custom = AutomaticAllocationOptions.of(AllocationGoal.FASTEST, 1000, 2.5);

        assertAutomaticOptions(balanced, AllocationGoal.BALANCED, 5000, 5.0);
        assertAutomaticOptions(fastest, AllocationGoal.FASTEST, 5000, 5.0);
        assertAutomaticOptions(bestQuality, AllocationGoal.BEST_QUALITY, 5000, 5.0);
        assertAutomaticOptions(custom, AllocationGoal.FASTEST, 1000, 2.5);
    }

    @Test
    void automaticExecutionUsesExpectedAlgorithmForEachGoalAndScenarioSize() {
        ScenarioData smallScenario = TestScenarioFactory.greedyTrapScenario();
        ScenarioData largeScenario = largerThanBalancedThresholdScenario();

        assertAutomaticExecution(
                smallScenario,
                AutomaticAllocationOptions.fastest(),
                AllocationGoal.FASTEST,
                AllocationAlgorithmType.GREEDY
        );
        assertAutomaticExecution(
                smallScenario,
                AutomaticAllocationOptions.bestQuality(),
                AllocationGoal.BEST_QUALITY,
                AllocationAlgorithmType.CP_SAT
        );
        assertAutomaticExecution(
                smallScenario,
                AutomaticAllocationOptions.balanced(),
                AllocationGoal.BALANCED,
                AllocationAlgorithmType.BACKTRACKING
        );
        assertAutomaticExecution(
                largeScenario,
                AutomaticAllocationOptions.balanced(),
                AllocationGoal.BALANCED,
                AllocationAlgorithmType.CP_SAT
        );
    }

    @Test
    void comparisonRunsAllAlgorithmsForGreedyTrapScenario() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        AllocationComparisonResult result = allocator.compare(
                scenario.getResources(),
                scenario.getRequests(),
                1000,
                2.5
        );

        assertEquals(3, result.getEntries().size());
        assertEquals(10, score(result.getEntry(AllocationAlgorithmType.GREEDY)));
        assertEquals(19, score(result.getEntry(AllocationAlgorithmType.BACKTRACKING)));
        assertEquals(19, score(result.getEntry(AllocationAlgorithmType.CP_SAT)));
        assertEquals(19, result.getBestTotalPriorityScore());
        assertEquals(
                Set.of(AllocationAlgorithmType.BACKTRACKING, AllocationAlgorithmType.CP_SAT),
                Set.copyOf(result.getBestScoreAlgorithms())
        );
        assertTrue(result.getEntries().containsKey(result.getFastestAlgorithm()));

        for (AlgorithmComparisonEntry entry : result.getEntries().values()) {
            assertValid(entry.getAllocationResult(), scenario.getResources(), scenario.getRequests());
            assertTrue(entry.getExecutionTimeMs() >= 0);
        }
    }

    @Test
    void allocationOptionsRejectNullAlgorithmTypeAndInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> new AllocationOptions(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AllocationOptions(AllocationAlgorithmType.GREEDY, 0, 5.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new AllocationOptions(AllocationAlgorithmType.GREEDY, 5000, 0)
        );
    }

    @Test
    void automaticOptionsRejectNullGoalAndInvalidLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AutomaticAllocationOptions.of(null, 5000, 5.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> AutomaticAllocationOptions.of(AllocationGoal.BALANCED, 0, 5.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> AutomaticAllocationOptions.of(AllocationGoal.BALANCED, 5000, 0)
        );
    }

    @Test
    void allocatorRejectsNullResourcesRequestsOptionsAndAlgorithmType() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.allocate(null, scenario.getRequests(), AllocationAlgorithmType.GREEDY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.allocate(scenario.getResources(), null, AllocationAlgorithmType.GREEDY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.allocate(scenario.getResources(), scenario.getRequests(), (AllocationOptions) null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.allocate(scenario.getResources(), scenario.getRequests(), (AllocationAlgorithmType) null)
        );
    }

    @Test
    void automaticExecutionRejectsNullInputs() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.executeAutomatically(null, scenario.getRequests(), AutomaticAllocationOptions.balanced())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.executeAutomatically(scenario.getResources(), null, AutomaticAllocationOptions.balanced())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.executeAutomatically(scenario.getResources(), scenario.getRequests(), null)
        );
    }

    @Test
    void compareRejectsNullInputsAndInvalidTimeLimits() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.compare(null, scenario.getRequests(), 1000, 2.5)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.compare(scenario.getResources(), null, 1000, 2.5)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.compare(scenario.getResources(), scenario.getRequests(), 0, 2.5)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.compare(scenario.getResources(), scenario.getRequests(), 1000, 0)
        );
    }

    @Test
    void allocatorRejectsNullElementsInResourcesAndRequests() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();
        List<Resource> resourcesWithNull = new ArrayList<>(scenario.getResources());
        List<AllocationRequest> requestsWithNull = new ArrayList<>(scenario.getRequests());

        resourcesWithNull.set(0, null);
        requestsWithNull.set(0, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.allocate(resourcesWithNull, scenario.getRequests(), AllocationAlgorithmType.GREEDY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.allocate(scenario.getResources(), requestsWithNull, AllocationAlgorithmType.GREEDY)
        );
    }

    @Test
    void allocatorRejectsDuplicateResourceId() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();
        Resource resource = scenario.getResources().get(0);
        List<Resource> resourcesWithDuplicateId = List.of(resource, resource);

        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.allocate(resourcesWithDuplicateId, scenario.getRequests(), AllocationAlgorithmType.GREEDY)
        );
    }

    @Test
    void allocatorRejectsDuplicateRequestId() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();
        AllocationRequest request = scenario.getRequests().get(0);
        List<AllocationRequest> requestsWithDuplicateId = List.of(request, request);

        assertThrows(
                IllegalArgumentException.class,
                () -> allocator.allocate(scenario.getResources(), requestsWithDuplicateId, AllocationAlgorithmType.GREEDY)
        );
    }

    @Test
    void emptyListsReturnEmptyResultForAllAlgorithmTypes() {
        for (AllocationAlgorithmType algorithmType : AllocationAlgorithmType.values()) {
            AllocationResult result = allocator.allocate(List.of(), List.of(), algorithmType);

            assertNotNull(result);
            assertValid(result, List.of(), List.of());
            assertEquals(0, result.getStatistics().getTotalRequests());
            assertEquals(0, result.getStatistics().getAllocatedRequests());
            assertEquals(0, result.getStatistics().getRejectedRequests());
            assertEquals(0, result.getStatistics().getTotalPriorityScore());
        }
    }

    private void assertExplicitExecution(
            ScenarioData scenario,
            AllocationOptions options,
            AllocationAlgorithmType expectedAlgorithm,
            int expectedScore
    ) {
        AllocationExecutionResult result = allocator.execute(
                scenario.getResources(),
                scenario.getRequests(),
                options
        );

        assertEquals(AllocationSelectionMode.EXPLICIT, result.getSelectionMode());
        assertEquals(expectedAlgorithm, result.getRequestedAlgorithm());
        assertEquals(expectedAlgorithm, result.getExecutedAlgorithm());
        assertNull(result.getGoal());
        assertFalse(result.getSelectionReason().isBlank());
        assertTrue(result.getExecutionTimeMs() >= 0);
        assertValid(result.getAllocationResult(), scenario.getResources(), scenario.getRequests());
        assertEquals(expectedScore, result.getAllocationResult().getStatistics().getTotalPriorityScore());
    }

    private void assertAutomaticOptions(
            AutomaticAllocationOptions options,
            AllocationGoal expectedGoal,
            long expectedBacktrackingLimitMs,
            double expectedCpSatLimitSeconds
    ) {
        assertEquals(expectedGoal, options.getGoal());
        assertEquals(expectedBacktrackingLimitMs, options.getBacktrackingTimeLimitMs());
        assertEquals(expectedCpSatLimitSeconds, options.getCpSatTimeLimitSeconds(), 0.0001);
    }

    private void assertAutomaticExecution(
            ScenarioData scenario,
            AutomaticAllocationOptions options,
            AllocationGoal expectedGoal,
            AllocationAlgorithmType expectedAlgorithm
    ) {
        AllocationExecutionResult result = allocator.executeAutomatically(
                scenario.getResources(),
                scenario.getRequests(),
                options
        );

        assertEquals(AllocationSelectionMode.AUTO, result.getSelectionMode());
        assertNull(result.getRequestedAlgorithm());
        assertEquals(expectedAlgorithm, result.getExecutedAlgorithm());
        assertEquals(expectedGoal, result.getGoal());
        assertFalse(result.getSelectionReason().isBlank());
        assertTrue(result.getExecutionTimeMs() >= 0);
        assertValid(result.getAllocationResult(), scenario.getResources(), scenario.getRequests());
    }

    private int score(AlgorithmComparisonEntry entry) {
        assertNotNull(entry);
        assertTrue(entry.getExecutionTimeMs() >= 0);
        return entry.getAllocationResult().getStatistics().getTotalPriorityScore();
    }

    private ScenarioData largerThanBalancedThresholdScenario() {
        ScenarioData scenario = TestScenarioFactory.timeLimitScenario();
        List<Resource> resources = new ArrayList<>(scenario.getResources());
        List<AllocationRequest> requests = new ArrayList<>(scenario.getRequests());

        resources.add(
                new Resource(
                        "ROOM_EXTRA",
                        "Extra room",
                        "ROOM",
                        Map.of("people", 100),
                        scenario.getResources().get(0).getAvailability()
                )
        );
        requests.add(
                new AllocationRequest(
                        "REQ_EXTRA",
                        "Extra request",
                        LocalDateTime.of(2026, 7, 1, 10, 0),
                        120,
                        1,
                        List.of(new ResourceRequirement("ROOM", 1, Map.of("people", 30)))
                )
        );

        return new ScenarioData(resources, requests);
    }
}
