package allocation.benchmark;

import allocation.generator.GeneratedScenario;
import allocation.generator.RandomScenarioGenerator;
import allocation.generator.ScenarioGenerationConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BenchmarkRunnerTest {

    @Test
    void runnerReturnsOneResultPerAlgorithm() {
        GeneratedScenario scenario = new RandomScenarioGenerator().generate(
                new ScenarioGenerationConfig(
                        "BENCH",
                        8,
                        8,
                        1001L,
                        LocalDateTime.of(2026, 7, 15, 8, 0),
                        4,
                        0.30,
                        0.10
                )
        );

        List<BenchmarkResult> results = new BenchmarkRunner().run(
                scenario,
                new BenchmarkConfiguration(2, 500, 2.0)
        );

        assertEquals(3, results.size());

        Set<String> algorithmNames = results.stream()
                .map(BenchmarkResult::getAlgorithmName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("GREEDY", "BACKTRACKING", "CP-SAT"), algorithmNames);

        for (BenchmarkResult result : results) {
            assertEquals("BENCH", result.getScenarioName());
            assertEquals(1001L, result.getSeed());
            assertEquals(8, result.getResourceCount());
            assertEquals(8, result.getRequestCount());
            assertEquals(
                    result.getRequestCount(),
                    result.getAllocatedRequests() + result.getRejectedRequests()
            );
        }
    }

    @Test
    void benchmarkConfigurationRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkConfiguration(0, 100, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkConfiguration(1, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkConfiguration(1, 100, 0));
    }
}
