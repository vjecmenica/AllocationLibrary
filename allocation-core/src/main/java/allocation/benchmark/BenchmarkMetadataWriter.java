package allocation.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public class BenchmarkMetadataWriter {

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
                .append("  \"javaVersion\": ").append(quoted(System.getProperty("java.version"))).append(",\n")
                .append("  \"operatingSystem\": {\n")
                .append("    \"name\": ").append(quoted(System.getProperty("os.name"))).append(",\n")
                .append("    \"version\": ").append(quoted(System.getProperty("os.version"))).append(",\n")
                .append("    \"architecture\": ").append(quoted(System.getProperty("os.arch"))).append("\n")
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
                .append(configuration.getScaleResourceTypeCount()).append("\n")
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
                .append("    \"metadata\": ").append(quoted(outputPaths.metadata().toString())).append("\n")
                .append("  }\n")
                .append("}\n");

        return json.toString();
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
