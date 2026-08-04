package allocation.benchmark;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkCliArgumentsTest {

    @Test
    void defaultsAreSuitableForAQuickReproducibleRun() {
        BenchmarkConfiguration configuration = BenchmarkCliArguments.parse(new String[0]).configuration();

        assertEquals(List.of(BenchmarkProfile.BALANCED_SMALL), configuration.getProfiles());
        assertEquals(List.of(42L), configuration.getSeeds());
        assertEquals(1, configuration.getWarmupRuns());
        assertEquals(3, configuration.getMeasuredRuns());
        assertEquals(Path.of("benchmark-results"), configuration.getOutputDirectory());
    }

    @Test
    void parserSupportsAllExperimentOptions() {
        BenchmarkConfiguration configuration = BenchmarkCliArguments.parse(new String[]{
                "--profile", "GREEDY_TRAP,SCALE",
                "--seed", "42,43",
                "--warmups", "0",
                "--runs", "2",
                "--backtracking-limit-ms", "250",
                "--cp-sat-limit-seconds", "1.5",
                "--output", "custom-output",
                "--resources", "12",
                "--requests", "14",
                "--resource-types", "4"
        }).configuration();

        assertEquals(List.of(BenchmarkProfile.GREEDY_TRAP, BenchmarkProfile.SCALE), configuration.getProfiles());
        assertEquals(List.of(42L, 43L), configuration.getSeeds());
        assertEquals(0, configuration.getWarmupRuns());
        assertEquals(2, configuration.getMeasuredRuns());
        assertEquals(250, configuration.getBacktrackingTimeLimitMs());
        assertEquals(1.5, configuration.getCpSatTimeLimitSeconds(), 0.0001);
        assertEquals(12, configuration.getScaleResourceCount());
        assertEquals(14, configuration.getScaleRequestCount());
        assertEquals(4, configuration.getScaleResourceTypeCount());
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkCliArguments.parse(new String[]{"--unknown", "value"})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkCliArguments.parse(new String[]{"--runs"})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkCliArguments.parse(new String[]{"--runs", "0"})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkCliArguments.parse(new String[]{"--profile", "NOT_A_PROFILE"})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkCliArguments.parse(new String[]{"--unknown"})
        );
    }

    @Test
    void helpCanBeRequestedWithoutBuildingConfiguration() {
        BenchmarkCliArguments.ParsedArguments parsed = BenchmarkCliArguments.parse(new String[]{"--help"});

        assertTrue(parsed.helpRequested());
        assertTrue(BenchmarkCliArguments.usage().contains("--profile"));
    }

    @Test
    void mainReturnsNonZeroAndUsageForInvalidArguments() {
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

        int exitCode = BenchmarkMain.run(
                new String[]{"--unknown"},
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(errorBytes)
        );

        assertEquals(2, exitCode);
        assertTrue(errorBytes.toString().contains("Unknown argument"));
        assertTrue(errorBytes.toString().contains("Usage:"));
    }
}
