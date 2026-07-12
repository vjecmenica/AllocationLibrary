package allocation.service;

import allocation.testutil.TestScenarioFactory;
import allocation.testutil.TestScenarioFactory.ScenarioData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AllocationAlgorithmSelectorTest {

    private final AllocationAlgorithmSelector selector = new AllocationAlgorithmSelector();

    @Test
    void fastestGoalSelectsGreedy() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        AlgorithmSelectionDecision decision = selector.select(
                scenario.getResources(),
                scenario.getRequests(),
                AutomaticAllocationOptions.fastest()
        );

        assertDecision(decision, AllocationAlgorithmType.GREEDY);
    }

    @Test
    void bestQualityGoalSelectsCpSat() {
        ScenarioData scenario = TestScenarioFactory.greedyTrapScenario();

        AlgorithmSelectionDecision decision = selector.select(
                scenario.getResources(),
                scenario.getRequests(),
                AutomaticAllocationOptions.bestQuality()
        );

        assertDecision(decision, AllocationAlgorithmType.CP_SAT);
    }

    @Test
    void balancedGoalSelectsBacktrackingForSmallProblem() {
        ScenarioData scenario = TestScenarioFactory.timeLimitScenario();

        AlgorithmSelectionDecision decision = selector.select(
                scenario.getResources(),
                scenario.getRequests(),
                AutomaticAllocationOptions.balanced()
        );

        assertDecision(decision, AllocationAlgorithmType.BACKTRACKING);
    }

    @Test
    void balancedGoalSelectsCpSatForLargerProblem() {
        ScenarioData scenario = TestScenarioFactory.timeLimitScenario();
        ArrayList<allocation.model.Resource> resources = new ArrayList<>(scenario.getResources());
        resources.add(scenario.getResources().get(0));

        AlgorithmSelectionDecision decision = selector.select(
                resources,
                scenario.getRequests(),
                AutomaticAllocationOptions.balanced()
        );

        assertDecision(decision, AllocationAlgorithmType.CP_SAT);
    }

    private void assertDecision(
            AlgorithmSelectionDecision decision,
            AllocationAlgorithmType expectedAlgorithm
    ) {
        assertEquals(expectedAlgorithm, decision.getSelectedAlgorithm());
        assertFalse(decision.getReason().isBlank());
    }
}
