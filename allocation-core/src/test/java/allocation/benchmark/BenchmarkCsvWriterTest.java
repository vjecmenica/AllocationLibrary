package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static allocation.benchmark.BenchmarkTestData.result;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkCsvWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void rawWriterUsesStableHeaderAndInputOrder() throws Exception {
        Path outputPath = tempDir.resolve("raw.csv");
        List<BenchmarkResult> results = List.of(
                result(BenchmarkProfile.BALANCED_SMALL, 42, 1, AllocationAlgorithmType.GREEDY, 5.25, 10, 1, false, null),
                result(BenchmarkProfile.BALANCED_SMALL, 42, 1, AllocationAlgorithmType.CP_SAT, 7.75, 19, 2, false, "OPTIMAL")
        );

        new BenchmarkCsvWriter().writeRaw(outputPath, results);

        List<String> lines = Files.readAllLines(outputPath, StandardCharsets.UTF_8);
        assertEquals(
                "schemaVersion,benchmarkRunId,generatedAt,profile,seed,scenarioFingerprint,repetition,algorithm,executionOrderPosition,resourceCount,requestCount,backtrackingTimeLimitMs,cpSatTimeLimitSeconds,totalPriorityScore,allocatedRequests,rejectedRequests,measuredExecutionTimeMs,algorithmExecutionTimeMs,exploredStates,stoppedByLimit,algorithmStatus,objectiveValue",
                lines.get(0)
        );
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).startsWith("2,"));
        assertTrue(lines.get(1).contains("," + BenchmarkTestData.FINGERPRINT + ",1,GREEDY,1,"));
        assertTrue(lines.get(1).contains(",GREEDY,"));
        assertTrue(lines.get(2).contains(",CP_SAT,"));
        assertTrue(lines.get(1).contains(",5.250000,"));
    }

    @Test
    void summaryWriterUsesStableHeader() throws Exception {
        Path outputPath = tempDir.resolve("summary.csv");
        List<BenchmarkResult> raw = List.of(
                result(BenchmarkProfile.BALANCED_SMALL, 42, 1, AllocationAlgorithmType.GREEDY, 1, 10, 1, false, null),
                result(BenchmarkProfile.BALANCED_SMALL, 42, 2, AllocationAlgorithmType.GREEDY, 3, 12, 2, false, null)
        );

        new BenchmarkCsvWriter().writeSummary(
                outputPath,
                BenchmarkSummaryReport.fromResults(raw).getSummaries()
        );

        List<String> lines = Files.readAllLines(outputPath, StandardCharsets.UTF_8);
        assertEquals(
                "schemaVersion,benchmarkRunId,generatedAt,profile,seed,scenarioFingerprint,algorithm,resourceCount,requestCount,measuredRuns,averageMeasuredExecutionTimeMs,medianMeasuredExecutionTimeMs,minimumMeasuredExecutionTimeMs,maximumMeasuredExecutionTimeMs,averageTotalPriorityScore,bestTotalPriorityScore,worstTotalPriorityScore,averageAllocatedRequests,stoppedByLimitRuns,optimalCpSatRuns",
                lines.get(0)
        );
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).startsWith("2,"));
        assertTrue(lines.get(1).contains("," + BenchmarkTestData.FINGERPRINT + ",GREEDY,8,8,2,"));
        assertTrue(lines.get(1).contains(",2.000000,2.000000,1.000000,3.000000,"));
        assertTrue(Files.readString(outputPath).endsWith("\r\n"));
    }

    @Test
    void csvEscapingAndFormulaProtectionAreAppliedOnlyToText() {
        assertEquals("\"value,with,commas\"", BenchmarkCsv.text("value,with,commas"));
        assertEquals("\"value \"\"quoted\"\"\"", BenchmarkCsv.text("value \"quoted\""));
        assertEquals("\"line one\nline two\"", BenchmarkCsv.text("line one\nline two"));
        assertEquals("'=SUM(A1:A2)", BenchmarkCsv.text("=SUM(A1:A2)"));
        assertEquals("'  @command", BenchmarkCsv.text("  @command"));
        assertEquals("'*value", BenchmarkCsv.text("*value"));
        assertEquals("'-value", BenchmarkCsv.text("-value"));
        assertEquals("'+value", BenchmarkCsv.text("+value"));
        assertEquals("-10", BenchmarkCsv.number(-10));
    }
}
