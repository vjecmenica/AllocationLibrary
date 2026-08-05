package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Versioned request-level outcome from one measured benchmark execution.
 */
public class BenchmarkRequestOutcome {

    public static final int SCHEMA_VERSION = 1;

    private final String benchmarkRunId;
    private final Instant generatedAt;
    private final BenchmarkProfile profile;
    private final long seed;
    private final String scenarioFingerprint;
    private final int repetition;
    private final AllocationAlgorithmType algorithm;
    private final int executionOrderPosition;
    private final String requestId;
    private final String requestName;
    private final int requestPriority;
    private final LocalDateTime requestStart;
    private final LocalDateTime requestEnd;
    private final BenchmarkRequestOutcomeStatus outcome;
    private final List<String> assignedResourceIds;
    private final List<String> assignedResourceNames;
    private final String rejectionReason;

    public BenchmarkRequestOutcome(
            String benchmarkRunId,
            Instant generatedAt,
            BenchmarkProfile profile,
            long seed,
            String scenarioFingerprint,
            int repetition,
            AllocationAlgorithmType algorithm,
            int executionOrderPosition,
            String requestId,
            String requestName,
            int requestPriority,
            LocalDateTime requestStart,
            LocalDateTime requestEnd,
            BenchmarkRequestOutcomeStatus outcome,
            List<String> assignedResourceIds,
            List<String> assignedResourceNames,
            String rejectionReason
    ) {
        if (assignedResourceIds == null || assignedResourceNames == null) {
            throw new IllegalArgumentException("Assigned resource lists must not be null.");
        }

        this.benchmarkRunId = benchmarkRunId;
        this.generatedAt = generatedAt;
        this.profile = profile;
        this.seed = seed;
        this.scenarioFingerprint = scenarioFingerprint;
        this.repetition = repetition;
        this.algorithm = algorithm;
        this.executionOrderPosition = executionOrderPosition;
        this.requestId = requestId;
        this.requestName = requestName;
        this.requestPriority = requestPriority;
        this.requestStart = requestStart;
        this.requestEnd = requestEnd;
        this.outcome = outcome;
        this.assignedResourceIds = List.copyOf(assignedResourceIds);
        this.assignedResourceNames = List.copyOf(assignedResourceNames);
        this.rejectionReason = rejectionReason == null ? "" : rejectionReason;
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

    public int getRepetition() {
        return repetition;
    }

    public AllocationAlgorithmType getAlgorithm() {
        return algorithm;
    }

    public int getExecutionOrderPosition() {
        return executionOrderPosition;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRequestName() {
        return requestName;
    }

    public int getRequestPriority() {
        return requestPriority;
    }

    public LocalDateTime getRequestStart() {
        return requestStart;
    }

    public LocalDateTime getRequestEnd() {
        return requestEnd;
    }

    public BenchmarkRequestOutcomeStatus getOutcome() {
        return outcome;
    }

    public List<String> getAssignedResourceIds() {
        return assignedResourceIds;
    }

    public List<String> getAssignedResourceNames() {
        return assignedResourceNames;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
