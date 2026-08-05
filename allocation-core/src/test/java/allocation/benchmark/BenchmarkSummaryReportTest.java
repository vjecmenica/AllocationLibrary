package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static allocation.benchmark.BenchmarkTestData.result;
import static allocation.benchmark.BenchmarkTestData.resultWithIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkSummaryReportTest {

    @Test
    void aggregationCalculatesMeanMedianMinimumAndMaximum() {
        List<BenchmarkResult> results = List.of(
                result(BenchmarkProfile.BALANCED_SMALL, 42, 1, AllocationAlgorithmType.GREEDY, 1, 10, 2, false, null),
                result(BenchmarkProfile.BALANCED_SMALL, 42, 2, AllocationAlgorithmType.GREEDY, 3, 14, 4, false, null),
                result(BenchmarkProfile.BALANCED_SMALL, 42, 3, AllocationAlgorithmType.GREEDY, 7, 18, 6, true, null),
                result(BenchmarkProfile.BALANCED_SMALL, 42, 4, AllocationAlgorithmType.GREEDY, 9, 22, 8, false, null)
        );

        BenchmarkSummaryResult summary = BenchmarkSummaryReport.fromResults(results)
                .getSummaries()
                .get(0);

        assertEquals(4, summary.getMeasuredRuns());
        assertEquals(2, summary.getSchemaVersion());
        assertEquals(BenchmarkTestData.FINGERPRINT, summary.getScenarioFingerprint());
        assertEquals(8, summary.getResourceCount());
        assertEquals(8, summary.getRequestCount());
        assertEquals(5.0, summary.getAverageMeasuredExecutionTimeMs(), 0.0001);
        assertEquals(5.0, summary.getMedianMeasuredExecutionTimeMs(), 0.0001);
        assertEquals(1.0, summary.getMinimumMeasuredExecutionTimeMs(), 0.0001);
        assertEquals(9.0, summary.getMaximumMeasuredExecutionTimeMs(), 0.0001);
        assertEquals(16.0, summary.getAverageTotalPriorityScore(), 0.0001);
        assertEquals(22, summary.getBestTotalPriorityScore());
        assertEquals(10, summary.getWorstTotalPriorityScore());
        assertEquals(5.0, summary.getAverageAllocatedRequests(), 0.0001);
    }

    @Test
    void aggregationCountsLimitsAndOptimalCpSatRuns() {
        List<BenchmarkResult> results = List.of(
                result(BenchmarkProfile.BALANCED_SMALL, 42, 1, AllocationAlgorithmType.CP_SAT, 1, 10, 2, false, "OPTIMAL"),
                result(BenchmarkProfile.BALANCED_SMALL, 42, 2, AllocationAlgorithmType.CP_SAT, 2, 10, 2, true, "FEASIBLE"),
                result(BenchmarkProfile.BALANCED_SMALL, 42, 3, AllocationAlgorithmType.CP_SAT, 3, 10, 2, false, "OPTIMAL")
        );

        BenchmarkSummaryResult summary = BenchmarkSummaryReport.fromResults(results)
                .getSummaries()
                .get(0);

        assertEquals(1, summary.getStoppedByLimitRuns());
        assertEquals(2, summary.getOptimalCpSatRuns());
        assertTrue(BenchmarkSummaryReport.fromResults(results).formatForConsole().contains("optimalCpSat=2"));
    }

    @Test
    void summariesUseStableProfileSeedAndAlgorithmOrder() {
        List<BenchmarkResult> results = List.of(
                result(BenchmarkProfile.CONFLICT_HEAVY, 2, 1, AllocationAlgorithmType.CP_SAT, 1, 1, 1, false, "OPTIMAL"),
                result(BenchmarkProfile.BALANCED_SMALL, 2, 1, AllocationAlgorithmType.BACKTRACKING, 1, 1, 1, false, null),
                result(BenchmarkProfile.BALANCED_SMALL, 1, 1, AllocationAlgorithmType.GREEDY, 1, 1, 1, false, null)
        );

        assertEquals(
                List.of(
                        "BALANCED_SMALL-1-GREEDY",
                        "BALANCED_SMALL-2-BACKTRACKING",
                        "CONFLICT_HEAVY-2-CP_SAT"
                ),
                BenchmarkSummaryReport.fromResults(results).getSummaries().stream()
                        .map(summary -> summary.getProfile() + "-" + summary.getSeed() + "-" + summary.getAlgorithm())
                        .toList()
        );
    }

    @Test
    void summaryRejectsNullResultElement() {
        List<BenchmarkResult> results = new java.util.ArrayList<>();
        results.add(result(
                BenchmarkProfile.BALANCED_SMALL,
                42,
                1,
                AllocationAlgorithmType.GREEDY,
                1,
                10,
                1,
                false,
                null
        ));
        results.add(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkSummaryReport.fromResults(results)
        );
    }

    @Test
    void summaryRejectsMixedBenchmarkRunIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkSummaryReport.fromResults(List.of(
                        identityResult("run-1", BenchmarkTestData.FINGERPRINT, 8, 8, 1),
                        identityResult("run-2", BenchmarkTestData.FINGERPRINT, 8, 8, 2)
                ))
        );
    }

    @Test
    void summaryRejectsMixedScenarioFingerprints() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkSummaryReport.fromResults(List.of(
                        identityResult("run-1", "0".repeat(64), 8, 8, 1),
                        identityResult("run-1", "1".repeat(64), 8, 8, 2)
                ))
        );
    }

    @Test
    void summaryRejectsMixedScenarioDimensions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkSummaryReport.fromResults(List.of(
                        identityResult("run-1", BenchmarkTestData.FINGERPRINT, 8, 8, 1),
                        identityResult("run-1", BenchmarkTestData.FINGERPRINT, 9, 8, 2)
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkSummaryReport.fromResults(List.of(
                        identityResult("run-1", BenchmarkTestData.FINGERPRINT, 8, 8, 1),
                        identityResult("run-1", BenchmarkTestData.FINGERPRINT, 8, 9, 2)
                ))
        );
    }

    private BenchmarkResult identityResult(
            String runId,
            String fingerprint,
            int resourceCount,
            int requestCount,
            int repetition
    ) {
        return resultWithIdentity(
                runId,
                fingerprint,
                resourceCount,
                requestCount,
                BenchmarkProfile.BALANCED_SMALL,
                42,
                repetition,
                AllocationAlgorithmType.GREEDY
        );
    }
}
