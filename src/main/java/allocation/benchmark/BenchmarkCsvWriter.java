package allocation.benchmark;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BenchmarkCsvWriter {

    private static final String HEADER = "scenario,seed,resources,requests,algorithm,allocated,rejected,score,timeMs,exploredStates,stoppedByLimit,status,objectiveValue";

    public void write(Path outputPath, List<BenchmarkResult> results) throws IOException {
        if (outputPath == null) {
            throw new IllegalArgumentException("Putanja CSV fajla ne sme biti null.");
        }

        if (results == null) {
            throw new IllegalArgumentException("Lista rezultata ne sme biti null.");
        }

        Path parent = outputPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();

            for (BenchmarkResult result : results) {
                writer.write(toCsvLine(result));
                writer.newLine();
            }
        }
    }

    private String toCsvLine(BenchmarkResult result) {
        return String.join(
                ",",
                escape(result.getScenarioName()),
                String.valueOf(result.getSeed()),
                String.valueOf(result.getResourceCount()),
                String.valueOf(result.getRequestCount()),
                escape(result.getAlgorithmName()),
                String.valueOf(result.getAllocatedRequests()),
                String.valueOf(result.getRejectedRequests()),
                String.valueOf(result.getTotalPriorityScore()),
                String.valueOf(result.getExecutionTimeMs()),
                String.valueOf(result.getExploredStates()),
                String.valueOf(result.isStoppedByLimit()),
                escape(result.getAlgorithmStatus()),
                String.valueOf(result.getObjectiveValue())
        );
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        boolean needsEscaping = value.contains(",")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r");

        if (!needsEscaping) {
            return value;
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
