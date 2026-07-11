package allocation.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkSummaryReportTest {

    @Test
    void summaryGroupsResultsAndCountsBestScoreTies() {
        List<BenchmarkResult> results = List.of(
                result("TINY-1001", 1001L, "GREEDY", 4, 4, 20, 0.20, false, null),
                result("TINY-1001", 1001L, "BACKTRACKING", 5, 3, 25, 1.50, false, null),
                result("TINY-1001", 1001L, "CP-SAT", 5, 3, 25, 0.80, false, "OPTIMAL"),
                result("TINY-1002", 1002L, "GREEDY", 3, 5, 18, 0.25, false, null),
                result("TINY-1002", 1002L, "BACKTRACKING", 4, 4, 22, 5.00, true, null),
                result("TINY-1002", 1002L, "CP-SAT", 4, 4, 23, 0.90, false, "FEASIBLE")
        );

        BenchmarkSummaryReport report = BenchmarkSummaryReport.fromResults(results);

        BenchmarkSummaryReport.GroupSummary cpSatSummary = report.getGroupSummaries()
                .stream()
                .filter(summary -> "TINY".equals(summary.getScenarioSize()))
                .filter(summary -> "CP-SAT".equals(summary.getAlgorithmName()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, cpSatSummary.getScenarioCount());
        assertEquals(4.5, cpSatSummary.getAverageAllocatedRequests(), 0.0001);
        assertEquals(24.0, cpSatSummary.getAverageTotalPriorityScore(), 0.0001);
        assertEquals(0.85, cpSatSummary.getMedianExecutionTimeMs(), 0.0001);
        assertEquals(1, cpSatSummary.getOptimalStatusCount());
        assertEquals(1, cpSatSummary.getFeasibleStatusCount());

        BenchmarkSummaryReport.BestScoreSummary backtrackingBest = report.getBestScoreSummaries()
                .stream()
                .filter(summary -> "BACKTRACKING".equals(summary.getAlgorithmName()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, backtrackingBest.getBestScoreScenarioCount());
        assertEquals(1, backtrackingBest.getTieBestScoreScenarioCount());

        BenchmarkSummaryReport.BestScoreSummary cpSatBest = report.getBestScoreSummaries()
                .stream()
                .filter(summary -> "CP-SAT".equals(summary.getAlgorithmName()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, cpSatBest.getBestScoreScenarioCount());
        assertEquals(1, cpSatBest.getTieBestScoreScenarioCount());
        assertTrue(report.formatForConsole().contains("BENCHMARK SUMMARY"));
    }

    private BenchmarkResult result(
            String scenarioName,
            long seed,
            String algorithmName,
            int allocated,
            int rejected,
            int score,
            double timeMs,
            boolean stoppedByLimit,
            String status
    ) {
        return new BenchmarkResult(
                scenarioName,
                seed,
                8,
                8,
                algorithmName,
                allocated,
                rejected,
                score,
                timeMs,
                0,
                stoppedByLimit,
                status,
                0
        );
    }
}
