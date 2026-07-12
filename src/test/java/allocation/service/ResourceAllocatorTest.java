package allocation.service;

import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.Resource;
import allocation.testutil.TestScenarioFactory;
import allocation.testutil.TestScenarioFactory.ScenarioData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static allocation.testutil.AllocationResultValidator.assertValid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
