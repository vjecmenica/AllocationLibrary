package allocation.service;

import allocation.model.AllocationResult;

/**
 * Allocation result and measured runtime for one algorithm in a comparison run.
 */
public class AlgorithmComparisonEntry {

    private final AllocationAlgorithmType algorithm;
    private final AllocationResult allocationResult;
    private final double executionTimeMs;

    /**
     * Creates one comparison entry for a concrete algorithm.
     */
    public AlgorithmComparisonEntry(
            AllocationAlgorithmType algorithm,
            AllocationResult allocationResult,
            double executionTimeMs
    ) {
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm must not be null.");
        }

        if (allocationResult == null) {
            throw new IllegalArgumentException("Allocation result must not be null.");
        }

        if (executionTimeMs < 0) {
            throw new IllegalArgumentException("Execution time must not be negative.");
        }

        this.algorithm = algorithm;
        this.allocationResult = allocationResult;
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * Returns the algorithm represented by this entry.
     */
    public AllocationAlgorithmType getAlgorithm() {
        return algorithm;
    }

    /**
     * Returns the algorithm allocation result.
     */
    public AllocationResult getAllocationResult() {
        return allocationResult;
    }

    /**
     * Returns the measured wall-clock execution time in milliseconds.
     */
    public double getExecutionTimeMs() {
        return executionTimeMs;
    }
}
