package allocation.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkCsvWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writerCreatesCsvWithHeaderAndRows() throws Exception {
        Path outputPath = tempDir.resolve("benchmark.csv");

        List<BenchmarkResult> results = List.of(
                new BenchmarkResult(
                        "Scenario, \"A\"",
                        1001L,
                        10,
                        20,
                        "GREEDY",
                        8,
                        12,
                        42,
                        5,
                        0,
                        false,
                        null,
                        0
                ),
                new BenchmarkResult(
                        "Scenario B",
                        1002L,
                        10,
                        20,
                        "CP-SAT",
                        9,
                        11,
                        50,
                        7,
                        0,
                        false,
                        "OPTIMAL",
                        55.0
                )
        );

        new BenchmarkCsvWriter().write(outputPath, results);

        List<String> lines = Files.readAllLines(outputPath);

        assertEquals(
                "scenario,seed,resources,requests,algorithm,allocated,rejected,score,timeMs,exploredStates,stoppedByLimit,status,objectiveValue",
                lines.get(0)
        );
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).startsWith("\"Scenario, \"\"A\"\"\""));
        assertTrue(lines.get(2).contains("CP-SAT"));
    }
}
