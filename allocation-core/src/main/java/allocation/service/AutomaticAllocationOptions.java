package allocation.service;

/**
 * Options used when ResourceAllocator automatically selects an allocation
 * algorithm before execution.
 */
public class AutomaticAllocationOptions {

    private final AllocationGoal goal;
    private final long backtrackingTimeLimitMs;
    private final double cpSatTimeLimitSeconds;

    private AutomaticAllocationOptions(
            AllocationGoal goal,
            long backtrackingTimeLimitMs,
            double cpSatTimeLimitSeconds
    ) {
        if (goal == null) {
            throw new IllegalArgumentException("Allocation goal must not be null.");
        }

        if (backtrackingTimeLimitMs <= 0) {
            throw new IllegalArgumentException("Backtracking time limit must be positive.");
        }

        if (cpSatTimeLimitSeconds <= 0) {
            throw new IllegalArgumentException("CP-SAT time limit must be positive.");
        }

        this.goal = goal;
        this.backtrackingTimeLimitMs = backtrackingTimeLimitMs;
        this.cpSatTimeLimitSeconds = cpSatTimeLimitSeconds;
    }

    /**
     * Creates automatic options with the BALANCED goal and default time limits.
     */
    public static AutomaticAllocationOptions balanced() {
        return of(
                AllocationGoal.BALANCED,
                AllocationOptions.DEFAULT_BACKTRACKING_TIME_LIMIT_MS,
                AllocationOptions.DEFAULT_CP_SAT_TIME_LIMIT_SECONDS
        );
    }

    /**
     * Creates automatic options with the FASTEST goal and default time limits.
     */
    public static AutomaticAllocationOptions fastest() {
        return of(
                AllocationGoal.FASTEST,
                AllocationOptions.DEFAULT_BACKTRACKING_TIME_LIMIT_MS,
                AllocationOptions.DEFAULT_CP_SAT_TIME_LIMIT_SECONDS
        );
    }

    /**
     * Creates automatic options with the BEST_QUALITY goal and default time limits.
     */
    public static AutomaticAllocationOptions bestQuality() {
        return of(
                AllocationGoal.BEST_QUALITY,
                AllocationOptions.DEFAULT_BACKTRACKING_TIME_LIMIT_MS,
                AllocationOptions.DEFAULT_CP_SAT_TIME_LIMIT_SECONDS
        );
    }

    /**
     * Creates automatic options with an explicit goal and explicit time limits.
     */
    public static AutomaticAllocationOptions of(
            AllocationGoal goal,
            long backtrackingTimeLimitMs,
            double cpSatTimeLimitSeconds
    ) {
        return new AutomaticAllocationOptions(
                goal,
                backtrackingTimeLimitMs,
                cpSatTimeLimitSeconds
        );
    }

    /**
     * Returns the automatic selection goal.
     */
    public AllocationGoal getGoal() {
        return goal;
    }

    /**
     * Returns the Backtracking time limit in milliseconds.
     */
    public long getBacktrackingTimeLimitMs() {
        return backtrackingTimeLimitMs;
    }

    /**
     * Returns the CP-SAT time limit in seconds.
     */
    public double getCpSatTimeLimitSeconds() {
        return cpSatTimeLimitSeconds;
    }
}
