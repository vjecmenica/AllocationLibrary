package allocation.benchmark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class BenchmarkSummaryReport {

    private List<GroupSummary> groupSummaries;
    private List<BestScoreSummary> bestScoreSummaries;

    private BenchmarkSummaryReport(
            List<GroupSummary> groupSummaries,
            List<BestScoreSummary> bestScoreSummaries
    ) {
        this.groupSummaries = List.copyOf(groupSummaries);
        this.bestScoreSummaries = List.copyOf(bestScoreSummaries);
    }

    public static BenchmarkSummaryReport fromResults(List<BenchmarkResult> results) {
        if (results == null) {
            throw new IllegalArgumentException("Lista benchmark rezultata ne sme biti null.");
        }

        return new BenchmarkSummaryReport(
                buildGroupSummaries(results),
                buildBestScoreSummaries(results)
        );
    }

    public List<GroupSummary> getGroupSummaries() {
        return groupSummaries;
    }

    public List<BestScoreSummary> getBestScoreSummaries() {
        return bestScoreSummaries;
    }

    public String formatForConsole() {
        StringBuilder builder = new StringBuilder();

        builder.append(System.lineSeparator());
        builder.append("===== BENCHMARK SUMMARY =====");
        builder.append(System.lineSeparator());

        for (GroupSummary summary : groupSummaries) {
            builder.append(
                    String.format(
                            Locale.US,
                            "%s | algorithm=%s | scenarios=%d | avgAllocated=%.2f | avgScore=%.2f | medianTimeMs=%.3f | stoppedByLimit=%d | status OPTIMAL/FEASIBLE/OTHER/N/A=%d/%d/%d/%d",
                            summary.getScenarioSize(),
                            summary.getAlgorithmName(),
                            summary.getScenarioCount(),
                            summary.getAverageAllocatedRequests(),
                            summary.getAverageTotalPriorityScore(),
                            summary.getMedianExecutionTimeMs(),
                            summary.getStoppedByLimitCount(),
                            summary.getOptimalStatusCount(),
                            summary.getFeasibleStatusCount(),
                            summary.getOtherStatusCount(),
                            summary.getNotApplicableStatusCount()
                    )
            );
            builder.append(System.lineSeparator());
        }

        builder.append(System.lineSeparator());
        builder.append("===== BEST SCORE COUNTS =====");
        builder.append(System.lineSeparator());

        for (BestScoreSummary summary : bestScoreSummaries) {
            builder.append(
                    String.format(
                            Locale.US,
                            "%s | bestOrTiedScenarios=%d | exclusiveBestScenarios=%d | tiedBestScenarios=%d",
                            summary.getAlgorithmName(),
                            summary.getBestOrTiedScenarios(),
                            summary.getExclusiveBestScenarios(),
                            summary.getTiedBestScenarios()
                    )
            );
            builder.append(System.lineSeparator());
        }

        return builder.toString();
    }

    private static List<GroupSummary> buildGroupSummaries(List<BenchmarkResult> results) {
        Map<String, List<BenchmarkResult>> bySizeAndAlgorithm = new TreeMap<>();

        for (BenchmarkResult result : results) {
            String key = scenarioSize(result) + "|" + result.getAlgorithmName();
            bySizeAndAlgorithm.computeIfAbsent(key, ignored -> new ArrayList<>()).add(result);
        }

        List<GroupSummary> summaries = new ArrayList<>();

        for (Map.Entry<String, List<BenchmarkResult>> entry : bySizeAndAlgorithm.entrySet()) {
            List<BenchmarkResult> groupResults = entry.getValue();
            BenchmarkResult first = groupResults.get(0);

            summaries.add(
                    new GroupSummary(
                            scenarioSize(first),
                            first.getAlgorithmName(),
                            groupResults.size(),
                            averageAllocated(groupResults),
                            averageScore(groupResults),
                            medianTime(groupResults),
                            stoppedByLimitCount(groupResults),
                            statusCount(groupResults, "OPTIMAL"),
                            statusCount(groupResults, "FEASIBLE"),
                            otherStatusCount(groupResults),
                            notApplicableStatusCount(groupResults)
                    )
            );
        }

        summaries.sort(
                Comparator.comparing((GroupSummary summary) -> sizeOrder(summary.getScenarioSize()))
                        .thenComparing(GroupSummary::getAlgorithmName)
        );

        return summaries;
    }

    private static List<BestScoreSummary> buildBestScoreSummaries(List<BenchmarkResult> results) {
        Map<String, List<BenchmarkResult>> byScenario = new LinkedHashMap<>();

        for (BenchmarkResult result : results) {
            String key = result.getScenarioName() + "#" + result.getSeed();
            byScenario.computeIfAbsent(key, ignored -> new ArrayList<>()).add(result);
        }

        Map<String, MutableBestScoreSummary> byAlgorithm = new TreeMap<>();

        for (BenchmarkResult result : results) {
            byAlgorithm.computeIfAbsent(
                    result.getAlgorithmName(),
                    MutableBestScoreSummary::new
            );
        }

        for (List<BenchmarkResult> scenarioResults : byScenario.values()) {
            int bestScore = scenarioResults.stream()
                    .mapToInt(BenchmarkResult::getTotalPriorityScore)
                    .max()
                    .orElse(0);

            List<BenchmarkResult> winners = scenarioResults.stream()
                    .filter(result -> result.getTotalPriorityScore() == bestScore)
                    .collect(Collectors.toList());

            boolean tie = winners.size() > 1;

            for (BenchmarkResult winner : winners) {
                MutableBestScoreSummary summary = byAlgorithm.get(winner.getAlgorithmName());
                summary.bestOrTiedScenarioCount++;

                if (tie) {
                    summary.tiedBestScenarioCount++;
                }
            }
        }

        List<BestScoreSummary> summaries = new ArrayList<>();

        for (MutableBestScoreSummary summary : byAlgorithm.values()) {
            summaries.add(
                    new BestScoreSummary(
                            summary.algorithmName,
                            summary.bestOrTiedScenarioCount,
                            summary.tiedBestScenarioCount
                    )
            );
        }

        return summaries;
    }

    private static String scenarioSize(BenchmarkResult result) {
        String scenarioName = result.getScenarioName();
        int separatorIndex = scenarioName.indexOf('-');

        if (separatorIndex < 0) {
            return scenarioName;
        }

        return scenarioName.substring(0, separatorIndex);
    }

    private static int sizeOrder(String scenarioSize) {
        if ("TINY".equals(scenarioSize)) {
            return 0;
        }

        if ("SMALL".equals(scenarioSize)) {
            return 1;
        }

        if ("MEDIUM".equals(scenarioSize)) {
            return 2;
        }

        if ("LARGE".equals(scenarioSize)) {
            return 3;
        }

        return 4;
    }

    private static double averageAllocated(List<BenchmarkResult> results) {
        return results.stream()
                .mapToInt(BenchmarkResult::getAllocatedRequests)
                .average()
                .orElse(0);
    }

    private static double averageScore(List<BenchmarkResult> results) {
        return results.stream()
                .mapToInt(BenchmarkResult::getTotalPriorityScore)
                .average()
                .orElse(0);
    }

    private static double medianTime(List<BenchmarkResult> results) {
        List<Double> times = results.stream()
                .map(BenchmarkResult::getExecutionTimeMs)
                .sorted()
                .collect(Collectors.toList());

        int middle = times.size() / 2;

        if (times.size() % 2 == 1) {
            return times.get(middle);
        }

        return (times.get(middle - 1) + times.get(middle)) / 2.0;
    }

    private static int stoppedByLimitCount(List<BenchmarkResult> results) {
        int count = 0;

        for (BenchmarkResult result : results) {
            if (result.isStoppedByLimit()) {
                count++;
            }
        }

        return count;
    }

    private static int statusCount(List<BenchmarkResult> results, String expectedStatus) {
        int count = 0;

        for (BenchmarkResult result : results) {
            if (expectedStatus.equals(result.getAlgorithmStatus())) {
                count++;
            }
        }

        return count;
    }

    private static int otherStatusCount(List<BenchmarkResult> results) {
        int count = 0;

        for (BenchmarkResult result : results) {
            String status = result.getAlgorithmStatus();

            if (status != null
                    && !status.isBlank()
                    && !"OPTIMAL".equals(status)
                    && !"FEASIBLE".equals(status)) {
                count++;
            }
        }

        return count;
    }

    private static int notApplicableStatusCount(List<BenchmarkResult> results) {
        int count = 0;

        for (BenchmarkResult result : results) {
            String status = result.getAlgorithmStatus();

            if (status == null || status.isBlank()) {
                count++;
            }
        }

        return count;
    }

    public static class GroupSummary {

        private String scenarioSize;
        private String algorithmName;
        private int scenarioCount;
        private double averageAllocatedRequests;
        private double averageTotalPriorityScore;
        private double medianExecutionTimeMs;
        private int stoppedByLimitCount;
        private int optimalStatusCount;
        private int feasibleStatusCount;
        private int otherStatusCount;
        private int notApplicableStatusCount;

        private GroupSummary(
                String scenarioSize,
                String algorithmName,
                int scenarioCount,
                double averageAllocatedRequests,
                double averageTotalPriorityScore,
                double medianExecutionTimeMs,
                int stoppedByLimitCount,
                int optimalStatusCount,
                int feasibleStatusCount,
                int otherStatusCount,
                int notApplicableStatusCount
        ) {
            this.scenarioSize = scenarioSize;
            this.algorithmName = algorithmName;
            this.scenarioCount = scenarioCount;
            this.averageAllocatedRequests = averageAllocatedRequests;
            this.averageTotalPriorityScore = averageTotalPriorityScore;
            this.medianExecutionTimeMs = medianExecutionTimeMs;
            this.stoppedByLimitCount = stoppedByLimitCount;
            this.optimalStatusCount = optimalStatusCount;
            this.feasibleStatusCount = feasibleStatusCount;
            this.otherStatusCount = otherStatusCount;
            this.notApplicableStatusCount = notApplicableStatusCount;
        }

        public String getScenarioSize() {
            return scenarioSize;
        }

        public String getAlgorithmName() {
            return algorithmName;
        }

        public int getScenarioCount() {
            return scenarioCount;
        }

        public double getAverageAllocatedRequests() {
            return averageAllocatedRequests;
        }

        public double getAverageTotalPriorityScore() {
            return averageTotalPriorityScore;
        }

        public double getMedianExecutionTimeMs() {
            return medianExecutionTimeMs;
        }

        public int getStoppedByLimitCount() {
            return stoppedByLimitCount;
        }

        public int getOptimalStatusCount() {
            return optimalStatusCount;
        }

        public int getFeasibleStatusCount() {
            return feasibleStatusCount;
        }

        public int getOtherStatusCount() {
            return otherStatusCount;
        }

        public int getNotApplicableStatusCount() {
            return notApplicableStatusCount;
        }
    }

    public static class BestScoreSummary {

        private String algorithmName;
        private int bestOrTiedScenarios;
        private int tiedBestScenarios;

        private BestScoreSummary(
                String algorithmName,
                int bestOrTiedScenarios,
                int tiedBestScenarios
        ) {
            this.algorithmName = algorithmName;
            this.bestOrTiedScenarios = bestOrTiedScenarios;
            this.tiedBestScenarios = tiedBestScenarios;
        }

        public String getAlgorithmName() {
            return algorithmName;
        }

        public int getBestOrTiedScenarios() {
            return bestOrTiedScenarios;
        }

        public int getExclusiveBestScenarios() {
            return bestOrTiedScenarios - tiedBestScenarios;
        }

        public int getTiedBestScenarios() {
            return tiedBestScenarios;
        }

        public int getBestScoreScenarioCount() {
            return getBestOrTiedScenarios();
        }

        public int getTieBestScoreScenarioCount() {
            return getTiedBestScenarios();
        }
    }

    private static class MutableBestScoreSummary {

        private String algorithmName;
        private int bestOrTiedScenarioCount;
        private int tiedBestScenarioCount;

        private MutableBestScoreSummary(String algorithmName) {
            this.algorithmName = algorithmName;
        }
    }
}
