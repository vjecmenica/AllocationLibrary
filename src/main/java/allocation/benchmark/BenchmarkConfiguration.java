package allocation.benchmark;

public class BenchmarkConfiguration {

    private int repetitions;
    private long backtrackingTimeLimitMs;
    private double cpSatTimeLimitSeconds;

    public BenchmarkConfiguration(
            int repetitions,
            long backtrackingTimeLimitMs,
            double cpSatTimeLimitSeconds
    ) {
        if (repetitions <= 0) {
            throw new IllegalArgumentException("Broj ponavljanja mora biti pozitivan.");
        }

        if (backtrackingTimeLimitMs <= 0) {
            throw new IllegalArgumentException("Backtracking vremenski limit mora biti pozitivan.");
        }

        if (cpSatTimeLimitSeconds <= 0) {
            throw new IllegalArgumentException("CP-SAT vremenski limit mora biti pozitivan.");
        }

        this.repetitions = repetitions;
        this.backtrackingTimeLimitMs = backtrackingTimeLimitMs;
        this.cpSatTimeLimitSeconds = cpSatTimeLimitSeconds;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public long getBacktrackingTimeLimitMs() {
        return backtrackingTimeLimitMs;
    }

    public double getCpSatTimeLimitSeconds() {
        return cpSatTimeLimitSeconds;
    }
}
