package allocation.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BenchmarkCsvWriter {

    static final String RAW_HEADER = String.join(",",
            "schemaVersion",
            "benchmarkRunId",
            "generatedAt",
            "profile",
            "seed",
            "scenarioFingerprint",
            "repetition",
            "algorithm",
            "executionOrderPosition",
            "resourceCount",
            "requestCount",
            "backtrackingTimeLimitMs",
            "cpSatTimeLimitSeconds",
            "totalPriorityScore",
            "allocatedRequests",
            "rejectedRequests",
            "measuredExecutionTimeMs",
            "algorithmExecutionTimeMs",
            "exploredStates",
            "stoppedByLimit",
            "algorithmStatus",
            "objectiveValue"
    );

    static final String SUMMARY_HEADER = String.join(",",
            "schemaVersion",
            "benchmarkRunId",
            "generatedAt",
            "profile",
            "seed",
            "scenarioFingerprint",
            "algorithm",
            "resourceCount",
            "requestCount",
            "measuredRuns",
            "averageMeasuredExecutionTimeMs",
            "medianMeasuredExecutionTimeMs",
            "minimumMeasuredExecutionTimeMs",
            "maximumMeasuredExecutionTimeMs",
            "averageTotalPriorityScore",
            "bestTotalPriorityScore",
            "worstTotalPriorityScore",
            "averageAllocatedRequests",
            "stoppedByLimitRuns",
            "optimalCpSatRuns"
    );

    public void writeRaw(Path outputPath, List<BenchmarkResult> results) throws IOException {
        validate(outputPath, results);
        List<String> lines = new ArrayList<>();
        lines.add(RAW_HEADER);

        for (BenchmarkResult result : results) {
            lines.add(rawLine(result));
        }

        writeLines(outputPath, lines);
    }

    public void writeSummary(
            Path outputPath,
            List<BenchmarkSummaryResult> results
    ) throws IOException {
        validate(outputPath, results);
        List<String> lines = new ArrayList<>();
        lines.add(SUMMARY_HEADER);

        for (BenchmarkSummaryResult result : results) {
            lines.add(summaryLine(result));
        }

        writeLines(outputPath, lines);
    }

    /**
     * Compatibility alias for the earlier single-file benchmark writer.
     */
    public void write(Path outputPath, List<BenchmarkResult> results) throws IOException {
        writeRaw(outputPath, results);
    }

    private String rawLine(BenchmarkResult result) {
        return String.join(",",
                BenchmarkCsv.number(result.getSchemaVersion()),
                BenchmarkCsv.text(result.getBenchmarkRunId()),
                BenchmarkCsv.text(result.getGeneratedAt().toString()),
                BenchmarkCsv.text(result.getProfile().name()),
                BenchmarkCsv.number(result.getSeed()),
                BenchmarkCsv.text(result.getScenarioFingerprint()),
                BenchmarkCsv.number(result.getRepetition()),
                BenchmarkCsv.text(result.getAlgorithm().name()),
                BenchmarkCsv.number(result.getExecutionOrderPosition()),
                BenchmarkCsv.number(result.getResourceCount()),
                BenchmarkCsv.number(result.getRequestCount()),
                BenchmarkCsv.number(result.getBacktrackingTimeLimitMs()),
                decimal(result.getCpSatTimeLimitSeconds()),
                BenchmarkCsv.number(result.getTotalPriorityScore()),
                BenchmarkCsv.number(result.getAllocatedRequests()),
                BenchmarkCsv.number(result.getRejectedRequests()),
                decimal(result.getMeasuredExecutionTimeMs()),
                BenchmarkCsv.number(result.getAlgorithmExecutionTimeMs()),
                BenchmarkCsv.number(result.getExploredStates()),
                BenchmarkCsv.bool(result.isStoppedByLimit()),
                BenchmarkCsv.text(result.getAlgorithmStatus()),
                decimal(result.getObjectiveValue())
        );
    }

    private String summaryLine(BenchmarkSummaryResult result) {
        return String.join(",",
                BenchmarkCsv.number(result.getSchemaVersion()),
                BenchmarkCsv.text(result.getBenchmarkRunId()),
                BenchmarkCsv.text(result.getGeneratedAt().toString()),
                BenchmarkCsv.text(result.getProfile().name()),
                BenchmarkCsv.number(result.getSeed()),
                BenchmarkCsv.text(result.getScenarioFingerprint()),
                BenchmarkCsv.text(result.getAlgorithm().name()),
                BenchmarkCsv.number(result.getResourceCount()),
                BenchmarkCsv.number(result.getRequestCount()),
                BenchmarkCsv.number(result.getMeasuredRuns()),
                decimal(result.getAverageMeasuredExecutionTimeMs()),
                decimal(result.getMedianMeasuredExecutionTimeMs()),
                decimal(result.getMinimumMeasuredExecutionTimeMs()),
                decimal(result.getMaximumMeasuredExecutionTimeMs()),
                decimal(result.getAverageTotalPriorityScore()),
                BenchmarkCsv.number(result.getBestTotalPriorityScore()),
                BenchmarkCsv.number(result.getWorstTotalPriorityScore()),
                decimal(result.getAverageAllocatedRequests()),
                BenchmarkCsv.number(result.getStoppedByLimitRuns()),
                BenchmarkCsv.number(result.getOptimalCpSatRuns())
        );
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private void writeLines(Path outputPath, List<String> lines) throws IOException {
        Path parent = outputPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(
                outputPath,
                String.join("\r\n", lines) + "\r\n",
                StandardCharsets.UTF_8
        );
    }

    private void validate(Path outputPath, List<?> results) {
        if (outputPath == null) {
            throw new IllegalArgumentException("CSV file path must not be null.");
        }

        if (results == null) {
            throw new IllegalArgumentException("Result list must not be null.");
        }

        if (results.stream().anyMatch(result -> result == null)) {
            throw new IllegalArgumentException("Result list must not contain null elements.");
        }
    }
}
