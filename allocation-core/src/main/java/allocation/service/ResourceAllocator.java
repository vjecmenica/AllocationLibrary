package allocation.service;

import allocation.algorithm.AllocationAlgorithm;
import allocation.algorithm.BacktrackingAllocationAlgorithm;
import allocation.algorithm.CpSatAllocationAlgorithm;
import allocation.algorithm.GreedyAllocationAlgorithm;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.Resource;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Main public entry point for running allocation algorithms from the library.
 */
public class ResourceAllocator {

    /**
     * Allocates requests with the selected concrete algorithm and default limits.
     */
    public AllocationResult allocate(
            List<Resource> resources,
            List<AllocationRequest> requests,
            AllocationAlgorithmType algorithmType
    ) {
        return execute(
                resources,
                requests,
                new AllocationOptions(algorithmType)
        ).getAllocationResult();
    }

    /**
     * Allocates requests with explicit allocation options.
     */
    public AllocationResult allocate(
            List<Resource> resources,
            List<AllocationRequest> requests,
            AllocationOptions options
    ) {
        return execute(resources, requests, options).getAllocationResult();
    }

    /**
     * Executes one explicitly selected algorithm and returns execution metadata.
     */
    public AllocationExecutionResult execute(
            List<Resource> resources,
            List<AllocationRequest> requests,
            AllocationOptions options
    ) {
        validateInput(resources, requests, options);

        AllocationAlgorithmType algorithmType = options.getAlgorithmType();
        TimedAllocationResult timedResult = runAlgorithm(
                createAlgorithm(options),
                resources,
                requests
        );

        return new AllocationExecutionResult(
                AllocationSelectionMode.EXPLICIT,
                algorithmType,
                algorithmType,
                null,
                algorithmName(algorithmType) + " was executed because the caller explicitly selected it.",
                timedResult.getExecutionTimeMs(),
                timedResult.getAllocationResult()
        );
    }

    /**
     * Automatically selects and executes one concrete algorithm.
     */
    public AllocationExecutionResult executeAutomatically(
            List<Resource> resources,
            List<AllocationRequest> requests,
            AutomaticAllocationOptions options
    ) {
        validateInput(resources, requests, options);

        AllocationAlgorithmSelector selector = new AllocationAlgorithmSelector();
        AlgorithmSelectionDecision decision = selector.select(resources, requests, options);
        AllocationAlgorithmType selectedAlgorithm = decision.getSelectedAlgorithm();
        TimedAllocationResult timedResult = runAlgorithm(
                createAlgorithm(selectedAlgorithm, options),
                resources,
                requests
        );

        return new AllocationExecutionResult(
                AllocationSelectionMode.AUTO,
                null,
                selectedAlgorithm,
                options.getGoal(),
                decision.getReason(),
                timedResult.getExecutionTimeMs(),
                timedResult.getAllocationResult()
        );
    }

    /**
     * Runs every concrete algorithm on the same scenario and returns comparison metadata.
     */
    public AllocationComparisonResult compare(
            List<Resource> resources,
            List<AllocationRequest> requests,
            long backtrackingTimeLimitMs,
            double cpSatTimeLimitSeconds
    ) {
        validateResourcesAndRequests(resources, requests);
        validateBacktrackingTimeLimit(backtrackingTimeLimitMs);
        validateCpSatTimeLimit(cpSatTimeLimitSeconds);

        Map<AllocationAlgorithmType, AlgorithmComparisonEntry> entries =
                new EnumMap<>(AllocationAlgorithmType.class);

        for (AllocationAlgorithmType algorithmType : AllocationAlgorithmType.values()) {
            TimedAllocationResult timedResult = runAlgorithm(
                    createAlgorithm(algorithmType, backtrackingTimeLimitMs, cpSatTimeLimitSeconds),
                    resources,
                    requests
            );

            entries.put(
                    algorithmType,
                    new AlgorithmComparisonEntry(
                            algorithmType,
                            timedResult.getAllocationResult(),
                            timedResult.getExecutionTimeMs()
                    )
            );
        }

        return new AllocationComparisonResult(entries);
    }

    private void validateInput(
            List<Resource> resources,
            List<AllocationRequest> requests,
            AllocationOptions options
    ) {
        validateResourcesAndRequests(resources, requests);

        if (options == null) {
            throw new IllegalArgumentException("Allocation options must not be null.");
        }
    }

    private void validateInput(
            List<Resource> resources,
            List<AllocationRequest> requests,
            AutomaticAllocationOptions options
    ) {
        validateResourcesAndRequests(resources, requests);

        if (options == null) {
            throw new IllegalArgumentException("Automatic allocation options must not be null.");
        }
    }

    private void validateResourcesAndRequests(
            List<Resource> resources,
            List<AllocationRequest> requests
    ) {
        if (resources == null) {
            throw new IllegalArgumentException("Resource list must not be null.");
        }

        if (requests == null) {
            throw new IllegalArgumentException("Request list must not be null.");
        }

        validateResources(resources);
        validateRequests(requests);
    }

    private void validateResources(List<Resource> resources) {
        Set<String> resourceIds = new HashSet<>();

        for (Resource resource : resources) {
            if (resource == null) {
                throw new IllegalArgumentException("Resource list must not contain null elements.");
            }

            if (!resourceIds.add(resource.getId())) {
                throw new IllegalArgumentException("Resource ID must be unique: " + resource.getId());
            }
        }
    }

    private void validateRequests(List<AllocationRequest> requests) {
        Set<String> requestIds = new HashSet<>();

        for (AllocationRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("Request list must not contain null elements.");
            }

            if (!requestIds.add(request.getId())) {
                throw new IllegalArgumentException("Allocation request ID must be unique: " + request.getId());
            }
        }
    }

    private AllocationAlgorithm createAlgorithm(AllocationOptions options) {
        return createAlgorithm(
                options.getAlgorithmType(),
                options.getBacktrackingTimeLimitMs(),
                options.getCpSatTimeLimitSeconds()
        );
    }

    private AllocationAlgorithm createAlgorithm(
            AllocationAlgorithmType algorithmType,
            AutomaticAllocationOptions options
    ) {
        return createAlgorithm(
                algorithmType,
                options.getBacktrackingTimeLimitMs(),
                options.getCpSatTimeLimitSeconds()
        );
    }

    private AllocationAlgorithm createAlgorithm(
            AllocationAlgorithmType algorithmType,
            long backtrackingTimeLimitMs,
            double cpSatTimeLimitSeconds
    ) {
        switch (algorithmType) {
            case GREEDY:
                return new GreedyAllocationAlgorithm();
            case BACKTRACKING:
                return new BacktrackingAllocationAlgorithm(backtrackingTimeLimitMs);
            case CP_SAT:
                return new CpSatAllocationAlgorithm(cpSatTimeLimitSeconds);
            default:
                throw new IllegalArgumentException("Unsupported algorithm type: " + algorithmType);
        }
    }

    private TimedAllocationResult runAlgorithm(
            AllocationAlgorithm algorithm,
            List<Resource> resources,
            List<AllocationRequest> requests
    ) {
        long startTime = System.nanoTime();
        AllocationResult allocationResult = algorithm.allocate(resources, requests);
        long endTime = System.nanoTime();

        return new TimedAllocationResult(
                allocationResult,
                toMilliseconds(endTime - startTime)
        );
    }

    private double toMilliseconds(long durationNanos) {
        return durationNanos / 1_000_000.0;
    }

    private void validateBacktrackingTimeLimit(long backtrackingTimeLimitMs) {
        if (backtrackingTimeLimitMs <= 0) {
            throw new IllegalArgumentException("Backtracking time limit must be positive.");
        }
    }

    private void validateCpSatTimeLimit(double cpSatTimeLimitSeconds) {
        if (cpSatTimeLimitSeconds <= 0) {
            throw new IllegalArgumentException("CP-SAT time limit must be positive.");
        }
    }

    private String algorithmName(AllocationAlgorithmType algorithmType) {
        if (algorithmType == AllocationAlgorithmType.CP_SAT) {
            return "CP-SAT";
        }

        return algorithmType.name();
    }

    private static class TimedAllocationResult {

        private final AllocationResult allocationResult;
        private final double executionTimeMs;

        private TimedAllocationResult(
                AllocationResult allocationResult,
                double executionTimeMs
        ) {
            this.allocationResult = allocationResult;
            this.executionTimeMs = executionTimeMs;
        }

        private AllocationResult getAllocationResult() {
            return allocationResult;
        }

        private double getExecutionTimeMs() {
            return executionTimeMs;
        }
    }
}
