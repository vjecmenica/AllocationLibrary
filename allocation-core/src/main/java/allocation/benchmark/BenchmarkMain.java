package allocation.benchmark;

import allocation.generator.GeneratedScenario;
import allocation.generator.RandomScenarioGenerator;
import allocation.generator.ScenarioGenerationConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BenchmarkMain {

    private static final long[] SEEDS = {1001L, 1002L, 1003L};

    public static void main(String[] args) throws IOException {
        RandomScenarioGenerator generator = new RandomScenarioGenerator();
        BenchmarkRunner runner = new BenchmarkRunner();
        BenchmarkCsvWriter csvWriter = new BenchmarkCsvWriter();

        List<BenchmarkResult> allResults = new ArrayList<>();

        for (ScenarioGenerationConfig generationConfig : createGenerationConfigs()) {
            GeneratedScenario scenario = generator.generate(generationConfig);
            BenchmarkConfiguration benchmarkConfiguration = createBenchmarkConfiguration(generationConfig);

            List<BenchmarkResult> scenarioResults = runner.run(scenario, benchmarkConfiguration);
            allResults.addAll(scenarioResults);

            printResults(scenarioResults);
        }

        Path outputPath = Path.of("target", "benchmark", "allocation-benchmark.csv");
        csvWriter.write(outputPath, allResults);

        System.out.println(BenchmarkSummaryReport.fromResults(allResults).formatForConsole());

        System.out.println();
        System.out.println("CSV written to: " + outputPath.toAbsolutePath());
    }

    private static List<ScenarioGenerationConfig> createGenerationConfigs() {
        List<ScenarioGenerationConfig> configs = new ArrayList<>();

        for (long seed : SEEDS) {
            configs.add(
                    new ScenarioGenerationConfig(
                            "TINY-" + seed,
                            8,
                            8,
                            seed,
                            LocalDateTime.of(2026, 7, 9, 8, 0),
                            4,
                            0.35,
                            0.15
                    )
            );
        }

        for (long seed : SEEDS) {
            configs.add(
                    new ScenarioGenerationConfig(
                            "SMALL-" + seed,
                            20,
                            20,
                            seed,
                            LocalDateTime.of(2026, 7, 10, 8, 0),
                            5,
                            0.45,
                            0.20
                    )
            );
        }

        for (long seed : SEEDS) {
            configs.add(
                    new ScenarioGenerationConfig(
                            "MEDIUM-" + seed,
                            50,
                            50,
                            seed,
                            LocalDateTime.of(2026, 7, 11, 8, 0),
                            8,
                            0.30,
                            0.12
                    )
            );
        }

        for (long seed : SEEDS) {
            configs.add(
                    new ScenarioGenerationConfig(
                            "LARGE-" + seed,
                            100,
                            100,
                            seed,
                            LocalDateTime.of(2026, 7, 12, 8, 0),
                            12,
                            0.12,
                            0.04
                    )
            );
        }

        return configs;
    }

    private static BenchmarkConfiguration createBenchmarkConfiguration(
            ScenarioGenerationConfig generationConfig
    ) {
        if (generationConfig.getResourceCount() == 8) {
            return new BenchmarkConfiguration(3, 5000, 5.0);
        }

        if (generationConfig.getResourceCount() >= 100) {
            return new BenchmarkConfiguration(3, 200, 5.0);
        }

        return new BenchmarkConfiguration(3, 1000, 5.0);
    }

    private static void printResults(List<BenchmarkResult> results) {
        for (BenchmarkResult result : results) {
            System.out.println(
                    result.getScenarioName()
                            + " | seed="
                            + result.getSeed()
                            + " | algorithm="
                            + result.getAlgorithmName()
                            + " | allocated="
                            + result.getAllocatedRequests()
                            + " | rejected="
                            + result.getRejectedRequests()
                            + " | score="
                            + result.getTotalPriorityScore()
                            + " | medianTimeMs="
                            + String.format(Locale.US, "%.3f", result.getExecutionTimeMs())
                            + " | stoppedByLimit="
                            + result.isStoppedByLimit()
                            + " | status="
                            + valueOrEmpty(result.getAlgorithmStatus())
            );
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
