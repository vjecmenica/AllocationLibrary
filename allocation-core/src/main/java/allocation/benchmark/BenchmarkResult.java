package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;

import java.time.Instant;

/**
 * One measured algorithm execution. Warmup executions are never represented by this model.
 */
public class BenchmarkResult {

    public static final int SCHEMA_VERSION = 1;

    private final String benchmarkRunId;
    private final Instant generatedAt;
    private final BenchmarkProfile profile;
    private final long seed;
    private final int repetition;
    private final AllocationAlgorithmType algorithm;
    private final int resourceCount;
    private final int requestCount;
    private final long backtrackingTimeLimitMs;
    private final double cpSatTimeLimitSeconds;
    private final int totalPriorityScore;
    private final int allocatedRequests;
    private final int rejectedRequests;
    private final double measuredExecutionTimeMs;
    private final long algorithmExecutionTimeMs;
    private final long exploredStates;
    private final boolean stoppedByLimit;
    private final String algorithmStatus;
    private final double objectiveValue;

    public BenchmarkResult(
            String benchmarkRunId,
            Instant generatedAt,
            BenchmarkProfile profile,
            long seed,
            int repetition,
            AllocationAlgorithmType algorithm,
            int resourceCount,
            int requestCount,
            long backtrackingTimeLimitMs,
            double cpSatTimeLimitSeconds,
            int totalPriorityScore,
            int allocatedRequests,
            int rejectedRequests,
            double measuredExecutionTimeMs,
            long algorithmExecutionTimeMs,
            long exploredStates,
            boolean stoppedByLimit,
            String algorithmStatus,
            double objectiveValue
    ) {
        this.benchmarkRunId = benchmarkRunId;
        this.generatedAt = generatedAt;
        this.profile = profile;
        this.seed = seed;
        this.repetition = repetition;
        this.algorithm = algorithm;
        this.resourceCount = resourceCount;
        this.requestCount = requestCount;
        this.backtrackingTimeLimitMs = backtrackingTimeLimitMs;
        this.cpSatTimeLimitSeconds = cpSatTimeLimitSeconds;
        this.totalPriorityScore = totalPriorityScore;
        this.allocatedRequests = allocatedRequests;
        this.rejectedRequests = rejectedRequests;
        this.measuredExecutionTimeMs = measuredExecutionTimeMs;
        this.algorithmExecutionTimeMs = algorithmExecutionTimeMs;
        this.exploredStates = exploredStates;
        this.stoppedByLimit = stoppedByLimit;
        this.algorithmStatus = algorithmStatus;
        this.objectiveValue = objectiveValue;
    }

    public int getSchemaVersion() {
        return SCHEMA_VERSION;
    }

    public String getBenchmarkRunId() {
        return benchmarkRunId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public BenchmarkProfile getProfile() {
        return profile;
    }

    public String getScenarioName() {
        return profile.name();
    }

    public long getSeed() {
        return seed;
    }

    public int getRepetition() {
        return repetition;
    }

    public AllocationAlgorithmType getAlgorithm() {
        return algorithm;
    }

    public String getAlgorithmName() {
        return algorithm.name();
    }

    public int getResourceCount() {
        return resourceCount;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public long getBacktrackingTimeLimitMs() {
        return backtrackingTimeLimitMs;
    }

    public double getCpSatTimeLimitSeconds() {
        return cpSatTimeLimitSeconds;
    }

    public int getTotalPriorityScore() {
        return totalPriorityScore;
    }

    public int getAllocatedRequests() {
        return allocatedRequests;
    }

    public int getRejectedRequests() {
        return rejectedRequests;
    }

    public double getMeasuredExecutionTimeMs() {
        return measuredExecutionTimeMs;
    }

    public double getExecutionTimeMs() {
        return measuredExecutionTimeMs;
    }

    public long getAlgorithmExecutionTimeMs() {
        return algorithmExecutionTimeMs;
    }

    public long getExploredStates() {
        return exploredStates;
    }

    public boolean isStoppedByLimit() {
        return stoppedByLimit;
    }

    public String getAlgorithmStatus() {
        return algorithmStatus;
    }

    public double getObjectiveValue() {
        return objectiveValue;
    }
}
