package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;

import java.time.Instant;

public class BenchmarkSummaryResult {

    public static final int SCHEMA_VERSION = 2;

    private final String benchmarkRunId;
    private final Instant generatedAt;
    private final BenchmarkProfile profile;
    private final long seed;
    private final String scenarioFingerprint;
    private final AllocationAlgorithmType algorithm;
    private final int resourceCount;
    private final int requestCount;
    private final int measuredRuns;
    private final double averageMeasuredExecutionTimeMs;
    private final double medianMeasuredExecutionTimeMs;
    private final double minimumMeasuredExecutionTimeMs;
    private final double maximumMeasuredExecutionTimeMs;
    private final double averageTotalPriorityScore;
    private final int bestTotalPriorityScore;
    private final int worstTotalPriorityScore;
    private final double averageAllocatedRequests;
    private final int stoppedByLimitRuns;
    private final int optimalCpSatRuns;

    public BenchmarkSummaryResult(
            String benchmarkRunId,
            Instant generatedAt,
            BenchmarkProfile profile,
            long seed,
            String scenarioFingerprint,
            AllocationAlgorithmType algorithm,
            int resourceCount,
            int requestCount,
            int measuredRuns,
            double averageMeasuredExecutionTimeMs,
            double medianMeasuredExecutionTimeMs,
            double minimumMeasuredExecutionTimeMs,
            double maximumMeasuredExecutionTimeMs,
            double averageTotalPriorityScore,
            int bestTotalPriorityScore,
            int worstTotalPriorityScore,
            double averageAllocatedRequests,
            int stoppedByLimitRuns,
            int optimalCpSatRuns
    ) {
        this.benchmarkRunId = benchmarkRunId;
        this.generatedAt = generatedAt;
        this.profile = profile;
        this.seed = seed;
        this.scenarioFingerprint = scenarioFingerprint;
        this.algorithm = algorithm;
        this.resourceCount = resourceCount;
        this.requestCount = requestCount;
        this.measuredRuns = measuredRuns;
        this.averageMeasuredExecutionTimeMs = averageMeasuredExecutionTimeMs;
        this.medianMeasuredExecutionTimeMs = medianMeasuredExecutionTimeMs;
        this.minimumMeasuredExecutionTimeMs = minimumMeasuredExecutionTimeMs;
        this.maximumMeasuredExecutionTimeMs = maximumMeasuredExecutionTimeMs;
        this.averageTotalPriorityScore = averageTotalPriorityScore;
        this.bestTotalPriorityScore = bestTotalPriorityScore;
        this.worstTotalPriorityScore = worstTotalPriorityScore;
        this.averageAllocatedRequests = averageAllocatedRequests;
        this.stoppedByLimitRuns = stoppedByLimitRuns;
        this.optimalCpSatRuns = optimalCpSatRuns;
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

    public long getSeed() {
        return seed;
    }

    public String getScenarioFingerprint() {
        return scenarioFingerprint;
    }

    public AllocationAlgorithmType getAlgorithm() {
        return algorithm;
    }

    public int getResourceCount() {
        return resourceCount;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public int getMeasuredRuns() {
        return measuredRuns;
    }

    public double getAverageMeasuredExecutionTimeMs() {
        return averageMeasuredExecutionTimeMs;
    }

    public double getMedianMeasuredExecutionTimeMs() {
        return medianMeasuredExecutionTimeMs;
    }

    public double getMinimumMeasuredExecutionTimeMs() {
        return minimumMeasuredExecutionTimeMs;
    }

    public double getMaximumMeasuredExecutionTimeMs() {
        return maximumMeasuredExecutionTimeMs;
    }

    public double getAverageTotalPriorityScore() {
        return averageTotalPriorityScore;
    }

    public int getBestTotalPriorityScore() {
        return bestTotalPriorityScore;
    }

    public int getWorstTotalPriorityScore() {
        return worstTotalPriorityScore;
    }

    public double getAverageAllocatedRequests() {
        return averageAllocatedRequests;
    }

    public int getStoppedByLimitRuns() {
        return stoppedByLimitRuns;
    }

    public int getOptimalCpSatRuns() {
        return optimalCpSatRuns;
    }
}
