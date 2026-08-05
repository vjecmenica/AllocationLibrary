package allocation.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class BenchmarkMetadataWriter {

    static final String UNKNOWN = "UNKNOWN";

    private final Function<String, String> propertyLookup;
    private final Function<String, String> environmentLookup;

    public BenchmarkMetadataWriter() {
        this(System::getProperty, System::getenv);
    }

    BenchmarkMetadataWriter(
            Function<String, String> propertyLookup,
            Function<String, String> environmentLookup
    ) {
        this.propertyLookup = propertyLookup;
        this.environmentLookup = environmentLookup;
    }

    public void write(
            Path outputPath,
            BenchmarkRun run,
            BenchmarkOutputPaths outputPaths
    ) throws IOException {
        if (outputPath == null || run == null || outputPaths == null) {
            throw new IllegalArgumentException("Metadata output arguments must not be null.");
        }

        Path parent = outputPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(outputPath, toJson(run, outputPaths), StandardCharsets.UTF_8);
    }

    String toJson(BenchmarkRun run, BenchmarkOutputPaths outputPaths) {
        BenchmarkConfiguration configuration = run.getConfiguration();
        StringBuilder json = new StringBuilder();

        json.append("{\n")
                .append("  \"schemaVersion\": ").append(run.getSchemaVersion()).append(",\n")
                .append("  \"benchmarkRunId\": ").append(quoted(run.getBenchmarkRunId())).append(",\n")
                .append("  \"generatedAt\": ").append(quoted(run.getGeneratedAt().toString())).append(",\n")
                .append("  \"projectVersion\": ").append(quoted(projectVersion())).append(",\n")
                .append("  \"sourceCommit\": ").append(quoted(sourceCommit())).append(",\n")
                .append("  \"javaVersion\": ").append(quoted(property("java.version"))).append(",\n")
                .append("  \"javaVendor\": ").append(quoted(property("java.vendor"))).append(",\n")
                .append("  \"javaVmName\": ").append(quoted(property("java.vm.name"))).append(",\n")
                .append("  \"operatingSystem\": {\n")
                .append("    \"name\": ").append(quoted(property("os.name"))).append(",\n")
                .append("    \"version\": ").append(quoted(property("os.version"))).append(",\n")
                .append("    \"architecture\": ").append(quoted(property("os.arch"))).append("\n")
                .append("  },\n")
                .append("  \"availableProcessors\": ")
                .append(Runtime.getRuntime().availableProcessors()).append(",\n")
                .append("  \"configuration\": {\n")
                .append("    \"warmupRuns\": ").append(configuration.getWarmupRuns()).append(",\n")
                .append("    \"measuredRuns\": ").append(configuration.getMeasuredRuns()).append(",\n")
                .append("    \"backtrackingTimeLimitMs\": ")
                .append(configuration.getBacktrackingTimeLimitMs()).append(",\n")
                .append("    \"cpSatTimeLimitSeconds\": ")
                .append(decimal(configuration.getCpSatTimeLimitSeconds())).append(",\n")
                .append("    \"outputDirectory\": ")
                .append(quoted(configuration.getOutputDirectory().toString())).append(",\n")
                .append("    \"scaleResourceCount\": ")
                .append(configuration.getScaleResourceCount()).append(",\n")
                .append("    \"scaleRequestCount\": ")
                .append(configuration.getScaleRequestCount()).append(",\n")
                .append("    \"scaleResourceTypeCount\": ")
                .append(configuration.getScaleResourceTypeCount()).append(",\n")
                .append("    \"overwrite\": ").append(configuration.isOverwrite()).append("\n")
                .append("  },\n")
                .append("  \"profiles\": ")
                .append(stringArray(configuration.getProfiles().stream().map(Enum::name).toList()))
                .append(",\n")
                .append("  \"seeds\": ").append(longArray(configuration.getSeeds())).append(",\n")
                .append("  \"algorithms\": ")
                .append(stringArray(BenchmarkRunner.ALGORITHM_ORDER.stream().map(Enum::name).toList()))
                .append(",\n")
                .append("  \"files\": {\n")
                .append("    \"rawResults\": ").append(quoted(outputPaths.rawResults().toString())).append(",\n")
                .append("    \"summaryResults\": ").append(quoted(outputPaths.summaryResults().toString())).append(",\n")
                .append("    \"requestOutcomes\": ")
                .append(quoted(outputPaths.requestOutcomes().toString())).append(",\n")
                .append("    \"scenarioSnapshots\": ")
                .append(quoted(outputPaths.scenarioSnapshots().toString())).append(",\n")
                .append("    \"metadata\": ").append(quoted(outputPaths.metadata().toString())).append("\n")
                .append("  }\n")
                .append("}\n");

        return json.toString();
    }

    private String projectVersion() {
        String configuredVersion = propertyLookup.apply("benchmark.projectVersion");

        if (hasText(configuredVersion)) {
            return configuredVersion;
        }

        String implementationVersion = BenchmarkMetadataWriter.class
                .getPackage()
                .getImplementationVersion();
        return hasText(implementationVersion) ? implementationVersion : UNKNOWN;
    }

    private String sourceCommit() {
        for (String value : List.of(
                valueOrEmpty(propertyLookup.apply("benchmark.sourceCommit")),
                valueOrEmpty(environmentLookup.apply("BENCHMARK_GIT_COMMIT")),
                valueOrEmpty(environmentLookup.apply("GITHUB_SHA"))
        )) {
            if (hasText(value)) {
                return value;
            }
        }

        return UNKNOWN;
    }

    private String property(String name) {
        String value = propertyLookup.apply(name);
        return hasText(value) ? value : UNKNOWN;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private String stringArray(List<String> values) {
        return values.stream().map(this::quoted).collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    private String longArray(List<Long> values) {
        return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    private String quoted(String value) {
        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);

            switch (character) {
                case '\"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }

        return escaped.toString();
    }
}
