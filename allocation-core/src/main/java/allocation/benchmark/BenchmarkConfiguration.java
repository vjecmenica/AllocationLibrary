package allocation.benchmark;

import allocation.service.AllocationOptions;

import java.nio.file.Path;
import java.util.List;

public class BenchmarkConfiguration {

    public static final int DEFAULT_WARMUP_RUNS = 1;
    public static final int DEFAULT_MEASURED_RUNS = 3;
    public static final Path DEFAULT_OUTPUT_DIRECTORY = Path.of("benchmark-results");
    public static final int DEFAULT_SCALE_RESOURCE_COUNT = 20;
    public static final int DEFAULT_SCALE_REQUEST_COUNT = 20;
    public static final int DEFAULT_SCALE_RESOURCE_TYPE_COUNT = 3;

    private final List<BenchmarkProfile> profiles;
    private final List<Long> seeds;
    private final int warmupRuns;
    private final int measuredRuns;
    private final long backtrackingTimeLimitMs;
    private final double cpSatTimeLimitSeconds;
    private final Path outputDirectory;
    private final int scaleResourceCount;
    private final int scaleRequestCount;
    private final int scaleResourceTypeCount;

    public BenchmarkConfiguration(
            List<BenchmarkProfile> profiles,
            List<Long> seeds,
            int warmupRuns,
            int measuredRuns,
            long backtrackingTimeLimitMs,
            double cpSatTimeLimitSeconds,
            Path outputDirectory,
            int scaleResourceCount,
            int scaleRequestCount,
            int scaleResourceTypeCount
    ) {
        validateProfiles(profiles);
        validateSeeds(seeds);

        if (warmupRuns < 0) {
            throw new IllegalArgumentException("Warmup run count must not be negative.");
        }

        if (measuredRuns <= 0) {
            throw new IllegalArgumentException("Measured run count must be positive.");
        }

        if (backtrackingTimeLimitMs <= 0) {
            throw new IllegalArgumentException("Backtracking time limit must be positive.");
        }

        if (!Double.isFinite(cpSatTimeLimitSeconds) || cpSatTimeLimitSeconds <= 0) {
            throw new IllegalArgumentException("CP-SAT time limit must be a positive finite number.");
        }

        if (outputDirectory == null) {
            throw new IllegalArgumentException("Output directory must not be null.");
        }

        if (scaleResourceCount <= 0) {
            throw new IllegalArgumentException("SCALE resource count must be positive.");
        }

        if (scaleRequestCount <= 0) {
            throw new IllegalArgumentException("SCALE request count must be positive.");
        }

        if (scaleResourceTypeCount <= 0 || scaleResourceTypeCount > scaleResourceCount) {
            throw new IllegalArgumentException(
                    "SCALE resource type count must be positive and not exceed the resource count."
            );
        }

        this.profiles = List.copyOf(profiles);
        this.seeds = List.copyOf(seeds);
        this.warmupRuns = warmupRuns;
        this.measuredRuns = measuredRuns;
        this.backtrackingTimeLimitMs = backtrackingTimeLimitMs;
        this.cpSatTimeLimitSeconds = cpSatTimeLimitSeconds;
        this.outputDirectory = outputDirectory;
        this.scaleResourceCount = scaleResourceCount;
        this.scaleRequestCount = scaleRequestCount;
        this.scaleResourceTypeCount = scaleResourceTypeCount;
    }

    public BenchmarkConfiguration(
            int repetitions,
            long backtrackingTimeLimitMs,
            double cpSatTimeLimitSeconds
    ) {
        this(
                List.of(BenchmarkProfile.BALANCED_SMALL),
                List.of(42L),
                0,
                repetitions,
                backtrackingTimeLimitMs,
                cpSatTimeLimitSeconds,
                DEFAULT_OUTPUT_DIRECTORY,
                DEFAULT_SCALE_RESOURCE_COUNT,
                DEFAULT_SCALE_REQUEST_COUNT,
                DEFAULT_SCALE_RESOURCE_TYPE_COUNT
        );
    }

    public static BenchmarkConfiguration defaults() {
        return new BenchmarkConfiguration(
                List.of(BenchmarkProfile.BALANCED_SMALL),
                List.of(42L),
                DEFAULT_WARMUP_RUNS,
                DEFAULT_MEASURED_RUNS,
                AllocationOptions.DEFAULT_BACKTRACKING_TIME_LIMIT_MS,
                AllocationOptions.DEFAULT_CP_SAT_TIME_LIMIT_SECONDS,
                DEFAULT_OUTPUT_DIRECTORY,
                DEFAULT_SCALE_RESOURCE_COUNT,
                DEFAULT_SCALE_REQUEST_COUNT,
                DEFAULT_SCALE_RESOURCE_TYPE_COUNT
        );
    }

    public List<BenchmarkProfile> getProfiles() {
        return profiles;
    }

    public List<Long> getSeeds() {
        return seeds;
    }

    public int getWarmupRuns() {
        return warmupRuns;
    }

    public int getMeasuredRuns() {
        return measuredRuns;
    }

    public int getRepetitions() {
        return measuredRuns;
    }

    public long getBacktrackingTimeLimitMs() {
        return backtrackingTimeLimitMs;
    }

    public double getCpSatTimeLimitSeconds() {
        return cpSatTimeLimitSeconds;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public int getScaleResourceCount() {
        return scaleResourceCount;
    }

    public int getScaleRequestCount() {
        return scaleRequestCount;
    }

    public int getScaleResourceTypeCount() {
        return scaleResourceTypeCount;
    }

    private static void validateProfiles(List<BenchmarkProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            throw new IllegalArgumentException("At least one benchmark profile is required.");
        }

        if (profiles.stream().anyMatch(profile -> profile == null)) {
            throw new IllegalArgumentException("Benchmark profile list must not contain null elements.");
        }
    }

    private static void validateSeeds(List<Long> seeds) {
        if (seeds == null || seeds.isEmpty()) {
            throw new IllegalArgumentException("At least one benchmark seed is required.");
        }

        if (seeds.stream().anyMatch(seed -> seed == null)) {
            throw new IllegalArgumentException("Benchmark seed list must not contain null elements.");
        }
    }
}
