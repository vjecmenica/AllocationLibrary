package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BenchmarkSummaryReport {

    private final List<BenchmarkSummaryResult> summaries;

    private BenchmarkSummaryReport(List<BenchmarkSummaryResult> summaries) {
        this.summaries = List.copyOf(summaries);
    }

    public static BenchmarkSummaryReport fromResults(List<BenchmarkResult> results) {
        if (results == null) {
            throw new IllegalArgumentException("Benchmark result list must not be null.");
        }

        Map<GroupKey, List<BenchmarkResult>> grouped = new LinkedHashMap<>();

        for (BenchmarkResult result : results) {
            GroupKey key = new GroupKey(result.getProfile(), result.getSeed(), result.getAlgorithm());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(result);
        }

        List<BenchmarkSummaryResult> summaries = new ArrayList<>();

        for (Map.Entry<GroupKey, List<BenchmarkResult>> entry : grouped.entrySet()) {
            summaries.add(summarize(entry.getKey(), entry.getValue()));
        }

        summaries.sort(
                Comparator.comparing(BenchmarkSummaryResult::getProfile)
                        .thenComparingLong(BenchmarkSummaryResult::getSeed)
                        .thenComparing(BenchmarkSummaryResult::getAlgorithm)
        );

        return new BenchmarkSummaryReport(summaries);
    }

    public List<BenchmarkSummaryResult> getSummaries() {
        return summaries;
    }

    public String formatForConsole() {
        StringBuilder builder = new StringBuilder();
        builder.append(System.lineSeparator()).append("===== BENCHMARK SUMMARY =====");

        for (BenchmarkSummaryResult summary : summaries) {
            builder.append(System.lineSeparator()).append(
                    String.format(
                            Locale.ROOT,
                            "%s | seed=%d | algorithm=%s | runs=%d | avgScore=%.3f | avgAllocated=%.3f | medianTimeMs=%.3f | stoppedByLimit=%d | optimalCpSat=%d",
                            summary.getProfile(),
                            summary.getSeed(),
                            summary.getAlgorithm(),
                            summary.getMeasuredRuns(),
                            summary.getAverageTotalPriorityScore(),
                            summary.getAverageAllocatedRequests(),
                            summary.getMedianMeasuredExecutionTimeMs(),
                            summary.getStoppedByLimitRuns(),
                            summary.getOptimalCpSatRuns()
                    )
            );
        }

        return builder.toString();
    }

    private static BenchmarkSummaryResult summarize(
            GroupKey key,
            List<BenchmarkResult> results
    ) {
        BenchmarkResult first = results.get(0);
        List<Double> times = results.stream()
                .map(BenchmarkResult::getMeasuredExecutionTimeMs)
                .sorted()
                .toList();

        return new BenchmarkSummaryResult(
                first.getBenchmarkRunId(),
                first.getGeneratedAt(),
                key.profile(),
                key.seed(),
                key.algorithm(),
                results.size(),
                results.stream().mapToDouble(BenchmarkResult::getMeasuredExecutionTimeMs).average().orElse(0),
                median(times),
                times.get(0),
                times.get(times.size() - 1),
                results.stream().mapToInt(BenchmarkResult::getTotalPriorityScore).average().orElse(0),
                results.stream().mapToInt(BenchmarkResult::getTotalPriorityScore).max().orElse(0),
                results.stream().mapToInt(BenchmarkResult::getTotalPriorityScore).min().orElse(0),
                results.stream().mapToInt(BenchmarkResult::getAllocatedRequests).average().orElse(0),
                (int) results.stream().filter(BenchmarkResult::isStoppedByLimit).count(),
                optimalCpSatCount(key.algorithm(), results)
        );
    }

    private static double median(List<Double> sortedValues) {
        int middle = sortedValues.size() / 2;

        if (sortedValues.size() % 2 == 1) {
            return sortedValues.get(middle);
        }

        return (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0;
    }

    private static int optimalCpSatCount(
            AllocationAlgorithmType algorithm,
            List<BenchmarkResult> results
    ) {
        if (algorithm != AllocationAlgorithmType.CP_SAT) {
            return 0;
        }

        return (int) results.stream()
                .filter(result -> "OPTIMAL".equals(result.getAlgorithmStatus()))
                .count();
    }

    private record GroupKey(
            BenchmarkProfile profile,
            long seed,
            AllocationAlgorithmType algorithm
    ) {
    }
}
