package allocation.benchmark;

import java.time.Instant;
import java.util.List;

/**
 * Complete output of one benchmark invocation before it is written to disk.
 */
public class BenchmarkRun {

    public static final int SCHEMA_VERSION = 3;

    private final String benchmarkRunId;
    private final Instant generatedAt;
    private final BenchmarkConfiguration configuration;
    private final List<BenchmarkResult> rawResults;
    private final List<BenchmarkSummaryResult> summaryResults;
    private final List<BenchmarkRequestOutcome> requestOutcomes;
    private final List<BenchmarkScenarioSnapshot> scenarioSnapshots;

    public BenchmarkRun(
            String benchmarkRunId,
            Instant generatedAt,
            BenchmarkConfiguration configuration,
            List<BenchmarkResult> rawResults,
            List<BenchmarkSummaryResult> summaryResults
    ) {
        this(
                benchmarkRunId,
                generatedAt,
                configuration,
                rawResults,
                summaryResults,
                List.of(),
                List.of()
        );
    }

    public BenchmarkRun(
            String benchmarkRunId,
            Instant generatedAt,
            BenchmarkConfiguration configuration,
            List<BenchmarkResult> rawResults,
            List<BenchmarkSummaryResult> summaryResults,
            List<BenchmarkRequestOutcome> requestOutcomes,
            List<BenchmarkScenarioSnapshot> scenarioSnapshots
    ) {
        if (benchmarkRunId == null || benchmarkRunId.isBlank()) {
            throw new IllegalArgumentException("Benchmark run ID must not be blank.");
        }

        if (generatedAt == null) {
            throw new IllegalArgumentException("Generation time must not be null.");
        }

        if (configuration == null) {
            throw new IllegalArgumentException("Benchmark configuration must not be null.");
        }

        if (rawResults == null || summaryResults == null
                || requestOutcomes == null || scenarioSnapshots == null) {
            throw new IllegalArgumentException("Benchmark result lists must not be null.");
        }

        this.benchmarkRunId = benchmarkRunId;
        this.generatedAt = generatedAt;
        this.configuration = configuration;
        this.rawResults = List.copyOf(rawResults);
        this.summaryResults = List.copyOf(summaryResults);
        this.requestOutcomes = List.copyOf(requestOutcomes);
        this.scenarioSnapshots = List.copyOf(scenarioSnapshots);
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

    public BenchmarkConfiguration getConfiguration() {
        return configuration;
    }

    public List<BenchmarkResult> getRawResults() {
        return rawResults;
    }

    public List<BenchmarkSummaryResult> getSummaryResults() {
        return summaryResults;
    }

    public List<BenchmarkRequestOutcome> getRequestOutcomes() {
        return requestOutcomes;
    }

    public List<BenchmarkScenarioSnapshot> getScenarioSnapshots() {
        return scenarioSnapshots;
    }
}
