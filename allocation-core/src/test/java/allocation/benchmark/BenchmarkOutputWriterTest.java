package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static allocation.benchmark.BenchmarkTestData.result;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkOutputWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void missingOutputDirectoryIsCreatedWithAllFiveFiles() throws Exception {
        Path outputDirectory = tempDir.resolve("nested").resolve("benchmark-results");
        BenchmarkConfiguration configuration = configuration(outputDirectory, false);
        List<BenchmarkResult> raw = List.of(
                result(BenchmarkProfile.GREEDY_TRAP, 42, 1, AllocationAlgorithmType.GREEDY, 1, 10, 1, false, null),
                result(BenchmarkProfile.GREEDY_TRAP, 42, 1, AllocationAlgorithmType.BACKTRACKING, 2, 19, 2, false, null),
                result(BenchmarkProfile.GREEDY_TRAP, 42, 1, AllocationAlgorithmType.CP_SAT, 3, 19, 2, false, "OPTIMAL")
        );
        BenchmarkRun run = new BenchmarkRun(
                "run-1",
                Instant.parse("2026-09-01T08:00:00Z"),
                configuration,
                raw,
                BenchmarkSummaryReport.fromResults(raw).getSummaries()
        );

        BenchmarkOutputPaths paths = new BenchmarkOutputWriter().write(run);

        assertTrue(Files.isRegularFile(paths.rawResults()));
        assertTrue(Files.isRegularFile(paths.summaryResults()));
        assertTrue(Files.isRegularFile(paths.requestOutcomes()));
        assertTrue(Files.isRegularFile(paths.scenarioSnapshots()));
        assertTrue(Files.isRegularFile(paths.metadata()));
        assertEquals(4, Files.readAllLines(paths.rawResults()).size());
        assertEquals(4, Files.readAllLines(paths.summaryResults()).size());

        String metadata = Files.readString(paths.metadata(), StandardCharsets.UTF_8);
        assertTrue(metadata.contains("\"schemaVersion\": 3"));
        assertTrue(metadata.contains("\"benchmarkRunId\": \"run-1\""));
        assertTrue(metadata.contains("\"profiles\": [\"GREEDY_TRAP\"]"));
        assertTrue(metadata.contains("\"algorithms\": [\"GREEDY\", \"BACKTRACKING\", \"CP_SAT\"]"));
        assertTrue(metadata.contains("\"rawResults\""));
        assertTrue(metadata.contains("\"summaryResults\""));
        assertTrue(metadata.contains("\"requestOutcomes\""));
        assertTrue(metadata.contains("\"scenarioSnapshots\""));
    }

    @Test
    void metadataWriterEscapesJsonText() {
        BenchmarkConfiguration configuration = configuration(tempDir.resolve("output"), false);
        BenchmarkRun run = new BenchmarkRun(
                "run-\"1",
                Instant.parse("2026-09-01T08:00:00Z"),
                configuration,
                List.of(),
                List.of()
        );
        BenchmarkOutputPaths paths = new BenchmarkOutputPaths(
                Path.of("raw.csv"),
                Path.of("summary.csv"),
                Path.of("metadata.json")
        );

        String metadata = new BenchmarkMetadataWriter().toJson(run, paths);

        assertTrue(metadata.contains("run-\\\"1"));
    }

    @Test
    void everyExistingTargetPreventsAllFiveFilesFromBeingWritten() throws Exception {
        List<String> fileNames = outputFileNames();

        for (String existingFileName : fileNames) {
            Path outputDirectory = tempDir.resolve("protected-" + existingFileName);
            Files.createDirectories(outputDirectory);
            Path existingPath = outputDirectory.resolve(existingFileName);
            Files.writeString(existingPath, "original content", StandardCharsets.UTF_8);

            FileAlreadyExistsException exception = assertThrows(
                    FileAlreadyExistsException.class,
                    () -> new BenchmarkOutputWriter().write(
                            run(configuration(outputDirectory, false))
                    )
            );

            assertTrue(exception.getMessage().contains("--overwrite"));
            assertEquals("original content", Files.readString(existingPath, StandardCharsets.UTF_8));

            for (String fileName : fileNames) {
                if (!fileName.equals(existingFileName)) {
                    assertFalse(Files.exists(outputDirectory.resolve(fileName)));
                }
            }
        }
    }

    @Test
    void overwriteReplacesAllBenchmarkOutputFiles() throws Exception {
        Path outputDirectory = tempDir.resolve("overwrite-output");
        Files.createDirectories(outputDirectory);

        for (String fileName : outputFileNames()) {
            Files.writeString(outputDirectory.resolve(fileName), "old content", StandardCharsets.UTF_8);
        }

        BenchmarkOutputPaths paths = new BenchmarkOutputWriter().write(
                run(configuration(outputDirectory, true))
        );

        assertTrue(Files.readString(paths.rawResults()).startsWith("schemaVersion,"));
        assertTrue(Files.readString(paths.summaryResults()).startsWith("schemaVersion,"));
        assertTrue(Files.readString(paths.requestOutcomes()).startsWith("schemaVersion,"));
        assertTrue(Files.readString(paths.scenarioSnapshots()).startsWith("{"));
        assertTrue(Files.readString(paths.metadata()).startsWith("{"));
    }

    private BenchmarkRun run(BenchmarkConfiguration configuration) {
        List<BenchmarkResult> raw = List.of(
                result(BenchmarkProfile.GREEDY_TRAP, 42, 1, AllocationAlgorithmType.GREEDY, 1, 10, 1, false, null),
                result(BenchmarkProfile.GREEDY_TRAP, 42, 1, AllocationAlgorithmType.BACKTRACKING, 2, 19, 2, false, null),
                result(BenchmarkProfile.GREEDY_TRAP, 42, 1, AllocationAlgorithmType.CP_SAT, 3, 19, 2, false, "OPTIMAL")
        );

        return new BenchmarkRun(
                "run-1",
                Instant.parse("2026-09-01T08:00:00Z"),
                configuration,
                raw,
                BenchmarkSummaryReport.fromResults(raw).getSummaries()
        );
    }

    private BenchmarkConfiguration configuration(Path outputDirectory, boolean overwrite) {
        return new BenchmarkConfiguration(
                List.of(BenchmarkProfile.GREEDY_TRAP),
                List.of(42L),
                0,
                1,
                500,
                1.0,
                outputDirectory,
                10,
                10,
                3,
                overwrite
        );
    }

    private List<String> outputFileNames() {
        return List.of(
                BenchmarkOutputWriter.RAW_FILE_NAME,
                BenchmarkOutputWriter.SUMMARY_FILE_NAME,
                BenchmarkOutputWriter.REQUEST_OUTCOMES_FILE_NAME,
                BenchmarkOutputWriter.SCENARIO_SNAPSHOTS_FILE_NAME,
                BenchmarkOutputWriter.METADATA_FILE_NAME
        );
    }
}
