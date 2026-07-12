package allocation.service;

import allocation.model.AllocationResult;

/**
 * Result returned by ResourceAllocator when the caller needs execution metadata
 * in addition to the allocation result.
 */
public class AllocationExecutionResult {

    private final AllocationSelectionMode selectionMode;
    private final AllocationAlgorithmType requestedAlgorithm;
    private final AllocationAlgorithmType executedAlgorithm;
    private final AllocationGoal goal;
    private final String selectionReason;
    private final double executionTimeMs;
    private final AllocationResult allocationResult;

    /**
     * Creates an execution result for explicit or automatic algorithm selection.
     */
    public AllocationExecutionResult(
            AllocationSelectionMode selectionMode,
            AllocationAlgorithmType requestedAlgorithm,
            AllocationAlgorithmType executedAlgorithm,
            AllocationGoal goal,
            String selectionReason,
            double executionTimeMs,
            AllocationResult allocationResult
    ) {
        validate(
                selectionMode,
                requestedAlgorithm,
                executedAlgorithm,
                goal,
                selectionReason,
                executionTimeMs,
                allocationResult
        );

        this.selectionMode = selectionMode;
        this.requestedAlgorithm = requestedAlgorithm;
        this.executedAlgorithm = executedAlgorithm;
        this.goal = goal;
        this.selectionReason = selectionReason;
        this.executionTimeMs = executionTimeMs;
        this.allocationResult = allocationResult;
    }

    private void validate(
            AllocationSelectionMode selectionMode,
            AllocationAlgorithmType requestedAlgorithm,
            AllocationAlgorithmType executedAlgorithm,
            AllocationGoal goal,
            String selectionReason,
            double executionTimeMs,
            AllocationResult allocationResult
    ) {
        if (selectionMode == null) {
            throw new IllegalArgumentException("Selection mode must not be null.");
        }

        if (executedAlgorithm == null) {
            throw new IllegalArgumentException("Executed algorithm must not be null.");
        }

        if (selectionReason == null || selectionReason.isBlank()) {
            throw new IllegalArgumentException("Selection reason must not be blank.");
        }

        if (executionTimeMs < 0) {
            throw new IllegalArgumentException("Execution time must not be negative.");
        }

        if (allocationResult == null) {
            throw new IllegalArgumentException("Allocation result must not be null.");
        }

        validateSelectionFields(selectionMode, requestedAlgorithm, executedAlgorithm, goal);
    }

    private void validateSelectionFields(
            AllocationSelectionMode selectionMode,
            AllocationAlgorithmType requestedAlgorithm,
            AllocationAlgorithmType executedAlgorithm,
            AllocationGoal goal
    ) {
        if (selectionMode == AllocationSelectionMode.EXPLICIT) {
            if (requestedAlgorithm == null) {
                throw new IllegalArgumentException("Requested algorithm must not be null for explicit selection.");
            }

            if (requestedAlgorithm != executedAlgorithm) {
                throw new IllegalArgumentException("Requested and executed algorithms must match for explicit selection.");
            }

            if (goal != null) {
                throw new IllegalArgumentException("Goal must be null for explicit selection.");
            }
        } else if (selectionMode == AllocationSelectionMode.AUTO) {
            if (requestedAlgorithm != null) {
                throw new IllegalArgumentException("Requested algorithm must be null for automatic selection.");
            }

            if (goal == null) {
                throw new IllegalArgumentException("Goal must not be null for automatic selection.");
            }
        }
    }

    /**
     * Returns whether selection was explicit or automatic.
     */
    public AllocationSelectionMode getSelectionMode() {
        return selectionMode;
    }

    /**
     * Returns the algorithm requested by the caller, or null for automatic mode.
     */
    public AllocationAlgorithmType getRequestedAlgorithm() {
        return requestedAlgorithm;
    }

    /**
     * Returns the concrete algorithm that was executed.
     */
    public AllocationAlgorithmType getExecutedAlgorithm() {
        return executedAlgorithm;
    }

    /**
     * Returns the automatic selection goal, or null for explicit mode.
     */
    public AllocationGoal getGoal() {
        return goal;
    }

    /**
     * Returns a human-readable explanation of the selection.
     */
    public String getSelectionReason() {
        return selectionReason;
    }

    /**
     * Returns the measured wall-clock execution time in milliseconds.
     */
    public double getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * Returns the allocation result produced by the executed algorithm.
     */
    public AllocationResult getAllocationResult() {
        return allocationResult;
    }
}
