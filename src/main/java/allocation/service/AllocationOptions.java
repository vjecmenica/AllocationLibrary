package allocation.service;

/**
 * Options used by ResourceAllocator when selecting and configuring an
 * allocation algorithm.
 */
public class AllocationOptions {

    public static final long DEFAULT_BACKTRACKING_TIME_LIMIT_MS = 5000;
    public static final double DEFAULT_CP_SAT_TIME_LIMIT_SECONDS = 5.0;

    private final AllocationAlgorithmType algorithmType;
    private final long backtrackingTimeLimitMs;
    private final double cpSatTimeLimitSeconds;

    public AllocationOptions(AllocationAlgorithmType algorithmType) {
        this(
                algorithmType,
                DEFAULT_BACKTRACKING_TIME_LIMIT_MS,
                DEFAULT_CP_SAT_TIME_LIMIT_SECONDS
        );
    }

    public AllocationOptions(
            AllocationAlgorithmType algorithmType,
            long backtrackingTimeLimitMs,
            double cpSatTimeLimitSeconds
    ) {
        if (algorithmType == null) {
            throw new IllegalArgumentException("Tip algoritma ne sme biti null.");
        }

        if (backtrackingTimeLimitMs <= 0) {
            throw new IllegalArgumentException("Backtracking vremenski limit mora biti pozitivan.");
        }

        if (cpSatTimeLimitSeconds <= 0) {
            throw new IllegalArgumentException("CP-SAT vremenski limit mora biti pozitivan.");
        }

        this.algorithmType = algorithmType;
        this.backtrackingTimeLimitMs = backtrackingTimeLimitMs;
        this.cpSatTimeLimitSeconds = cpSatTimeLimitSeconds;
    }

    public static AllocationOptions greedy() {
        return new AllocationOptions(AllocationAlgorithmType.GREEDY);
    }

    public static AllocationOptions backtracking(long backtrackingTimeLimitMs) {
        return new AllocationOptions(
                AllocationAlgorithmType.BACKTRACKING,
                backtrackingTimeLimitMs,
                DEFAULT_CP_SAT_TIME_LIMIT_SECONDS
        );
    }

    public static AllocationOptions cpSat(double cpSatTimeLimitSeconds) {
        return new AllocationOptions(
                AllocationAlgorithmType.CP_SAT,
                DEFAULT_BACKTRACKING_TIME_LIMIT_MS,
                cpSatTimeLimitSeconds
        );
    }

    public AllocationAlgorithmType getAlgorithmType() {
        return algorithmType;
    }

    public long getBacktrackingTimeLimitMs() {
        return backtrackingTimeLimitMs;
    }

    public double getCpSatTimeLimitSeconds() {
        return cpSatTimeLimitSeconds;
    }
}
