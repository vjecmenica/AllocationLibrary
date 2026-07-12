package allocation.service;

/**
 * Optimization preference used by automatic algorithm selection.
 */
public enum AllocationGoal {

    /**
     * Prefer the algorithm expected to return quickly.
     */
    FASTEST,

    /**
     * Balance expected runtime and solution quality.
     */
    BALANCED,

    /**
     * Prefer the algorithm expected to find the best solution.
     */
    BEST_QUALITY
}
