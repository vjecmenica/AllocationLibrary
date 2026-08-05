package allocation.benchmark;

import java.nio.file.Path;

public record BenchmarkOutputPaths(
        Path rawResults,
        Path summaryResults,
        Path requestOutcomes,
        Path scenarioSnapshots,
        Path metadata
) {

    public BenchmarkOutputPaths(
            Path rawResults,
            Path summaryResults,
            Path metadata
    ) {
        this(
                rawResults,
                summaryResults,
                rawResults.resolveSibling(BenchmarkOutputWriter.REQUEST_OUTCOMES_FILE_NAME),
                rawResults.resolveSibling(BenchmarkOutputWriter.SCENARIO_SNAPSHOTS_FILE_NAME),
                metadata
        );
    }
}
