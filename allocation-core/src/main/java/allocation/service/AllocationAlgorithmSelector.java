package allocation.service;

import allocation.model.AllocationRequest;
import allocation.model.Resource;

import java.util.List;

/**
 * Selects a concrete allocation algorithm from scenario size and automatic
 * allocation options.
 */
public class AllocationAlgorithmSelector {

    private static final int BALANCED_BACKTRACKING_RESOURCE_THRESHOLD = 10;
    private static final int BALANCED_BACKTRACKING_REQUEST_THRESHOLD = 10;

    /**
     * Selects a concrete algorithm without executing any algorithm.
     */
    public AlgorithmSelectionDecision select(
            List<Resource> resources,
            List<AllocationRequest> requests,
            AutomaticAllocationOptions options
    ) {
        if (resources == null) {
            throw new IllegalArgumentException("Resource list must not be null.");
        }

        if (requests == null) {
            throw new IllegalArgumentException("Request list must not be null.");
        }

        if (options == null) {
            throw new IllegalArgumentException("Automatic allocation options must not be null.");
        }

        AllocationGoal goal = options.getGoal();

        if (goal == AllocationGoal.FASTEST) {
            return new AlgorithmSelectionDecision(
                    AllocationAlgorithmType.GREEDY,
                    "GREEDY was selected because the FASTEST goal prioritizes execution speed."
            );
        }

        if (goal == AllocationGoal.BEST_QUALITY) {
            return new AlgorithmSelectionDecision(
                    AllocationAlgorithmType.CP_SAT,
                    "CP-SAT was selected because the BEST_QUALITY goal prioritizes solution quality."
            );
        }

        if (isSmallProblem(resources, requests)) {
            return new AlgorithmSelectionDecision(
                    AllocationAlgorithmType.BACKTRACKING,
                    "BACKTRACKING was selected because the BALANCED goal is being used for a small problem."
            );
        }

        return new AlgorithmSelectionDecision(
                AllocationAlgorithmType.CP_SAT,
                "CP-SAT was selected because the BALANCED goal is being used for a problem larger than the backtracking threshold."
        );
    }

    private boolean isSmallProblem(
            List<Resource> resources,
            List<AllocationRequest> requests
    ) {
        return resources.size() <= BALANCED_BACKTRACKING_RESOURCE_THRESHOLD
                && requests.size() <= BALANCED_BACKTRACKING_REQUEST_THRESHOLD;
    }
}
