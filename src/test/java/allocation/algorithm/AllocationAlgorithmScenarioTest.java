package allocation.algorithm;

import allocation.model.AllocationResult;
import allocation.testutil.TestScenarioFactory;
import allocation.testutil.TestScenarioFactory.ScenarioData;
import org.junit.jupiter.api.Test;

import static allocation.testutil.AllocationResultValidator.assertValid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AllocationAlgorithmScenarioTest {

    @Test
    void greedyTrapShowsBacktrackingAndCpSatFindBetterGlobalSolution() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        AllocationResult greedyResult = new GreedyAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );
        AllocationResult backtrackingResult = new BacktrackingAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );
        AllocationResult cpSatResult = new CpSatAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );

        assertValid(greedyResult, scenario.getResources(), scenario.getRequests());
        assertValid(backtrackingResult, scenario.getResources(), scenario.getRequests());
        assertValid(cpSatResult, scenario.getResources(), scenario.getRequests());

        assertEquals(1, greedyResult.getStatistics().getAllocatedRequests());
        assertEquals(1, greedyResult.getStatistics().getRejectedRequests());
        assertEquals(10, greedyResult.getStatistics().getTotalPriorityScore());

        assertEquals(2, backtrackingResult.getStatistics().getAllocatedRequests());
        assertEquals(0, backtrackingResult.getStatistics().getRejectedRequests());
        assertEquals(19, backtrackingResult.getStatistics().getTotalPriorityScore());

        assertEquals(2, cpSatResult.getStatistics().getAllocatedRequests());
        assertEquals(0, cpSatResult.getStatistics().getRejectedRequests());
        assertEquals(19, cpSatResult.getStatistics().getTotalPriorityScore());
        assertEquals("OPTIMAL", cpSatResult.getStatistics().getAlgorithmStatus());
    }

    @Test
    void examScenarioProducesSamePriorityScoreForAllAlgorithms() {
        ScenarioData scenario = TestScenarioFactory.examScenario();

        AllocationResult greedyResult = new GreedyAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );
        AllocationResult backtrackingResult = new BacktrackingAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );
        AllocationResult cpSatResult = new CpSatAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );

        assertValid(greedyResult, scenario.getResources(), scenario.getRequests());
        assertValid(backtrackingResult, scenario.getResources(), scenario.getRequests());
        assertValid(cpSatResult, scenario.getResources(), scenario.getRequests());

        assertEquals(24, greedyResult.getStatistics().getTotalPriorityScore());
        assertEquals(24, backtrackingResult.getStatistics().getTotalPriorityScore());
        assertEquals(24, cpSatResult.getStatistics().getTotalPriorityScore());
        assertEquals("OPTIMAL", cpSatResult.getStatistics().getAlgorithmStatus());
    }

    @Test
    void complexMultiResourceScenarioShowsCpSatAndBacktrackingBeatGreedy() {
        ScenarioData scenario = TestScenarioFactory.complexMultiResourceScenario();

        AllocationResult greedyResult = new GreedyAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );
        AllocationResult backtrackingResult = new BacktrackingAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );
        AllocationResult cpSatResult = new CpSatAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );

        assertValid(greedyResult, scenario.getResources(), scenario.getRequests());
        assertValid(backtrackingResult, scenario.getResources(), scenario.getRequests());
        assertValid(cpSatResult, scenario.getResources(), scenario.getRequests());

        assertEquals(3, greedyResult.getStatistics().getAllocatedRequests());
        assertEquals(3, backtrackingResult.getStatistics().getAllocatedRequests());
        assertEquals(3, cpSatResult.getStatistics().getAllocatedRequests());
        assertEquals(25, greedyResult.getStatistics().getTotalPriorityScore());
        assertEquals(26, backtrackingResult.getStatistics().getTotalPriorityScore());
        assertEquals(26, cpSatResult.getStatistics().getTotalPriorityScore());
        assertEquals("OPTIMAL", cpSatResult.getStatistics().getAlgorithmStatus());
        assertEquals(133.0, cpSatResult.getStatistics().getObjectiveValue(), 0.0001);
    }

    @Test
    void algorithmsDoNotAcceptBothOverlappingRequestsWhenOnlyOneProjectorExists() {
        ScenarioData scenario = TestScenarioFactory.projectorConflictScenario();

        AllocationResult greedyResult = new GreedyAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );
        AllocationResult backtrackingResult = new BacktrackingAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );
        AllocationResult cpSatResult = new CpSatAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );

        assertValid(greedyResult, scenario.getResources(), scenario.getRequests());
        assertValid(backtrackingResult, scenario.getResources(), scenario.getRequests());
        assertValid(cpSatResult, scenario.getResources(), scenario.getRequests());

        assertOneAcceptedOneRejectedWithScoreTen(greedyResult);
        assertOneAcceptedOneRejectedWithScoreTen(backtrackingResult);
        assertOneAcceptedOneRejectedWithScoreTen(cpSatResult);
        assertEquals("OPTIMAL", cpSatResult.getStatistics().getAlgorithmStatus());
    }

    @Test
    void backtrackingWithTinyTimeLimitReturnsConsistentResult() {
        ScenarioData scenario = TestScenarioFactory.timeLimitScenario();

        AllocationResult result = new BacktrackingAllocationAlgorithm(1).allocate(
                scenario.getResources(),
                scenario.getRequests()
        );

        assertNotNull(result);
        assertValid(result, scenario.getResources(), scenario.getRequests());
        assertEquals(scenario.getRequests().size(), result.getStatistics().getTotalRequests());
        assertEquals(result.getAllocations().size(), result.getStatistics().getAllocatedRequests());
        assertEquals(result.getRejectedRequests().size(), result.getStatistics().getRejectedRequests());
        assertEquals(
                result.getStatistics().getTotalRequests(),
                result.getStatistics().getAllocatedRequests() + result.getStatistics().getRejectedRequests()
        );
    }

    private void assertOneAcceptedOneRejectedWithScoreTen(AllocationResult result) {
        assertEquals(1, result.getStatistics().getAllocatedRequests());
        assertEquals(1, result.getStatistics().getRejectedRequests());
        assertEquals(10, result.getStatistics().getTotalPriorityScore());
    }
}
