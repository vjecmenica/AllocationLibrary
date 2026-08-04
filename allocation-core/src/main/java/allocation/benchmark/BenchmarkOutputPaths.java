package allocation.benchmark;

import java.nio.file.Path;

public record BenchmarkOutputPaths(
        Path rawResults,
        Path summaryResults,
        Path metadata
) {
}
