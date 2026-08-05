package allocation.benchmark;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkMetadataWriterTest {

    @Test
    void sourceCommitUsesDocumentedPrecedence() {
        Map<String, String> environment = Map.of(
                "BENCHMARK_GIT_COMMIT", "benchmark-env",
                "GITHUB_SHA", "github-sha"
        );

        String propertyJson = json(
                Map.of("benchmark.sourceCommit", "system-property"),
                environment
        );
        String benchmarkEnvironmentJson = json(Map.of(), environment);
        String githubJson = json(Map.of(), Map.of("GITHUB_SHA", "github-sha"));
        String unknownJson = json(Map.of(), Map.of());

        assertTrue(propertyJson.contains("\"sourceCommit\": \"system-property\""));
        assertTrue(benchmarkEnvironmentJson.contains("\"sourceCommit\": \"benchmark-env\""));
        assertTrue(githubJson.contains("\"sourceCommit\": \"github-sha\""));
        assertTrue(unknownJson.contains("\"sourceCommit\": \"UNKNOWN\""));
    }

    @Test
    void metadataContainsSchemaProjectAndJavaProvenance() {
        String json = json(
                Map.of(
                        "benchmark.projectVersion", "9.8.7",
                        "java.version", "17-test",
                        "java.vendor", "Test Vendor",
                        "java.vm.name", "Test VM",
                        "os.name", "Test OS",
                        "os.version", "1",
                        "os.arch", "test-arch"
                ),
                Map.of()
        );

        assertTrue(json.contains("\"schemaVersion\": 3"));
        assertTrue(json.contains("\"projectVersion\": \"9.8.7\""));
        assertTrue(json.contains("\"javaVendor\": \"Test Vendor\""));
        assertTrue(json.contains("\"javaVmName\": \"Test VM\""));
        assertTrue(json.contains("\"requestOutcomes\""));
        assertTrue(json.contains("\"scenarioSnapshots\""));
    }

    @Test
    void unavailableProjectVersionUsesUnknown() {
        assertTrue(json(Map.of(), Map.of()).contains("\"projectVersion\": \"UNKNOWN\""));
    }

    private String json(Map<String, String> properties, Map<String, String> environment) {
        BenchmarkMetadataWriter writer = new BenchmarkMetadataWriter(
                properties::get,
                environment::get
        );
        BenchmarkConfiguration configuration = new BenchmarkConfiguration(
                List.of(BenchmarkProfile.GREEDY_TRAP),
                List.of(42L),
                0,
                1,
                500,
                1.0,
                Path.of("benchmark-results"),
                10,
                10,
                3
        );
        BenchmarkRun run = new BenchmarkRun(
                "run-1",
                Instant.parse("2026-09-01T08:00:00Z"),
                configuration,
                List.of(),
                List.of()
        );
        BenchmarkOutputPaths paths = new BenchmarkOutputPaths(
                Path.of("raw-results.csv"),
                Path.of("summary-results.csv"),
                Path.of("metadata.json")
        );

        return writer.toJson(run, paths);
    }
}
