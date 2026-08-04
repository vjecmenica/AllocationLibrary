package allocation.benchmark;

import allocation.service.AllocationOptions;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class BenchmarkCliArguments {

    private static final String USAGE = """
            Usage: benchmark [options]
              --profile <name[,name...]>          Benchmark profile (default: BALANCED_SMALL)
              --seed <value[,value...]>           Deterministic seed (default: 42)
              --warmups <count>                    Warmup runs per algorithm (default: 1)
              --runs <count>                       Measured runs per algorithm (default: 3)
              --backtracking-limit-ms <ms>         Backtracking limit (default: 5000)
              --cp-sat-limit-seconds <seconds>     CP-SAT limit (default: 5.0)
              --output <directory>                 Output directory (default: benchmark-results)
              --resources <count>                  SCALE resource count (default: 20)
              --requests <count>                   SCALE request count (default: 20)
              --resource-types <count>              SCALE resource type count (default: 3)
              --help                               Show this help

            Profiles: GREEDY_TRAP, BALANCED_SMALL, BALANCED_MEDIUM,
                      CONFLICT_HEAVY, CAPACITY_HEAVY, SCALE
            """;

    private BenchmarkCliArguments() {
    }

    static ParsedArguments parse(String[] args) {
        if (args == null) {
            throw new IllegalArgumentException("Argument list must not be null.");
        }

        List<BenchmarkProfile> profiles = new ArrayList<>(List.of(BenchmarkProfile.BALANCED_SMALL));
        List<Long> seeds = new ArrayList<>(List.of(42L));
        int warmupRuns = BenchmarkConfiguration.DEFAULT_WARMUP_RUNS;
        int measuredRuns = BenchmarkConfiguration.DEFAULT_MEASURED_RUNS;
        long backtrackingTimeLimitMs = AllocationOptions.DEFAULT_BACKTRACKING_TIME_LIMIT_MS;
        double cpSatTimeLimitSeconds = AllocationOptions.DEFAULT_CP_SAT_TIME_LIMIT_SECONDS;
        Path outputDirectory = BenchmarkConfiguration.DEFAULT_OUTPUT_DIRECTORY;
        int scaleResourceCount = BenchmarkConfiguration.DEFAULT_SCALE_RESOURCE_COUNT;
        int scaleRequestCount = BenchmarkConfiguration.DEFAULT_SCALE_REQUEST_COUNT;
        int scaleResourceTypeCount = BenchmarkConfiguration.DEFAULT_SCALE_RESOURCE_TYPE_COUNT;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];

            if ("--help".equals(argument)) {
                return new ParsedArguments(null, true);
            }

            if (!isValueArgument(argument)) {
                throw new IllegalArgumentException("Unknown argument: " + argument);
            }

            String value = requiredValue(args, ++index, argument);

            switch (argument) {
                case "--profile" -> profiles = parseProfiles(value);
                case "--seed" -> seeds = parseSeeds(value);
                case "--warmups" -> warmupRuns = parseInteger(value, argument);
                case "--runs" -> measuredRuns = parseInteger(value, argument);
                case "--backtracking-limit-ms" ->
                        backtrackingTimeLimitMs = parseLong(value, argument);
                case "--cp-sat-limit-seconds" ->
                        cpSatTimeLimitSeconds = parseDouble(value, argument);
                case "--output" -> outputDirectory = parsePath(value, argument);
                case "--resources" -> scaleResourceCount = parseInteger(value, argument);
                case "--requests" -> scaleRequestCount = parseInteger(value, argument);
                case "--resource-types" -> scaleResourceTypeCount = parseInteger(value, argument);
                default -> throw new IllegalStateException("Unsupported validated argument: " + argument);
            }
        }

        return new ParsedArguments(
                new BenchmarkConfiguration(
                        profiles,
                        seeds,
                        warmupRuns,
                        measuredRuns,
                        backtrackingTimeLimitMs,
                        cpSatTimeLimitSeconds,
                        outputDirectory,
                        scaleResourceCount,
                        scaleRequestCount,
                        scaleResourceTypeCount
                ),
                false
        );
    }

    static String usage() {
        return USAGE;
    }

    private static boolean isValueArgument(String argument) {
        return switch (argument) {
            case "--profile",
                    "--seed",
                    "--warmups",
                    "--runs",
                    "--backtracking-limit-ms",
                    "--cp-sat-limit-seconds",
                    "--output",
                    "--resources",
                    "--requests",
                    "--resource-types" -> true;
            default -> false;
        };
    }

    private static String requiredValue(String[] args, int index, String argument) {
        if (!argument.startsWith("--")) {
            throw new IllegalArgumentException("Unexpected argument: " + argument);
        }

        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException("Missing value for argument: " + argument);
        }

        return args[index];
    }

    private static List<BenchmarkProfile> parseProfiles(String value) {
        try {
            return Arrays.stream(value.split(",", -1))
                    .map(String::trim)
                    .map(name -> BenchmarkProfile.valueOf(name.toUpperCase(Locale.ROOT)))
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid benchmark profile: " + value, exception);
        }
    }

    private static List<Long> parseSeeds(String value) {
        try {
            return Arrays.stream(value.split(",", -1))
                    .map(String::trim)
                    .map(Long::valueOf)
                    .toList();
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid seed value: " + value, exception);
        }
    }

    private static int parseInteger(String value, String argument) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer for " + argument + ": " + value, exception);
        }
    }

    private static long parseLong(String value, String argument) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer for " + argument + ": " + value, exception);
        }
    }

    private static double parseDouble(String value, String argument) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid number for " + argument + ": " + value, exception);
        }
    }

    private static Path parsePath(String value, String argument) {
        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Invalid path for " + argument + ": " + value, exception);
        }
    }

    record ParsedArguments(BenchmarkConfiguration configuration, boolean helpRequested) {
    }
}
