package allocation.benchmark;

import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BenchmarkScenarioSnapshotWriter {

    public void write(
            Path outputPath,
            List<BenchmarkScenarioSnapshot> snapshots
    ) throws IOException {
        if (outputPath == null || snapshots == null) {
            throw new IllegalArgumentException("Scenario snapshot output arguments must not be null.");
        }

        if (snapshots.stream().anyMatch(snapshot -> snapshot == null)) {
            throw new IllegalArgumentException("Scenario snapshot list must not contain null elements.");
        }

        Path parent = outputPath.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(outputPath, toJson(snapshots), StandardCharsets.UTF_8);
    }

    String toJson(List<BenchmarkScenarioSnapshot> snapshots) {
        StringBuilder json = new StringBuilder();
        json.append("{\n")
                .append("  \"schemaVersion\": ")
                .append(BenchmarkScenarioSnapshot.SCHEMA_VERSION)
                .append(",\n")
                .append("  \"scenarios\": [");

        if (!snapshots.isEmpty()) {
            json.append('\n');
        }

        for (int index = 0; index < snapshots.size(); index++) {
            appendSnapshot(json, snapshots.get(index), "    ");
            json.append(index + 1 < snapshots.size() ? ",\n" : "\n");
        }

        json.append("  ]\n}")
                .append('\n');
        return json.toString();
    }

    private void appendSnapshot(
            StringBuilder json,
            BenchmarkScenarioSnapshot snapshot,
            String indent
    ) {
        json.append(indent).append("{\n")
                .append(indent).append("  \"profile\": ")
                .append(BenchmarkJson.quoted(snapshot.getProfile().name())).append(",\n")
                .append(indent).append("  \"seed\": ").append(snapshot.getSeed()).append(",\n")
                .append(indent).append("  \"scenarioFingerprint\": ")
                .append(BenchmarkJson.quoted(snapshot.getScenarioFingerprint())).append(",\n")
                .append(indent).append("  \"resources\": [");

        appendResources(json, snapshot.getResources(), indent + "    ");
        json.append(indent).append("  ],\n")
                .append(indent).append("  \"requests\": [");
        appendRequests(json, snapshot.getRequests(), indent + "    ");
        json.append(indent).append("  ]\n")
                .append(indent).append('}');
    }

    private void appendResources(
            StringBuilder json,
            List<Resource> resources,
            String indent
    ) {
        if (!resources.isEmpty()) {
            json.append('\n');
        }

        for (int index = 0; index < resources.size(); index++) {
            Resource resource = resources.get(index);
            json.append(indent).append("{\n")
                    .append(indent).append("  \"id\": ")
                    .append(BenchmarkJson.quoted(resource.getId())).append(",\n")
                    .append(indent).append("  \"name\": ")
                    .append(BenchmarkJson.quoted(resource.getName())).append(",\n")
                    .append(indent).append("  \"type\": ")
                    .append(BenchmarkJson.quoted(resource.getType())).append(",\n")
                    .append(indent).append("  \"capacities\": ");
            appendIntegerMap(json, resource.getCapacities(), indent + "  ");
            json.append(",\n")
                    .append(indent).append("  \"availability\": [");
            appendTimeWindows(json, resource.getAvailability(), indent + "    ");
            json.append(indent).append("  ]\n")
                    .append(indent).append('}')
                    .append(index + 1 < resources.size() ? ",\n" : "\n");
        }
    }

    private void appendTimeWindows(
            StringBuilder json,
            List<TimeWindow> availability,
            String indent
    ) {
        List<TimeWindow> windows = availability == null ? List.of() : availability;

        if (!windows.isEmpty()) {
            json.append('\n');
        }

        for (int index = 0; index < windows.size(); index++) {
            TimeWindow window = windows.get(index);
            json.append(indent).append("{\n")
                    .append(indent).append("  \"start\": ")
                    .append(BenchmarkJson.quoted(window.getStart().toString())).append(",\n")
                    .append(indent).append("  \"end\": ")
                    .append(BenchmarkJson.quoted(window.getEnd().toString())).append("\n")
                    .append(indent).append('}')
                    .append(index + 1 < windows.size() ? ",\n" : "\n");
        }
    }

    private void appendRequests(
            StringBuilder json,
            List<AllocationRequest> requests,
            String indent
    ) {
        if (!requests.isEmpty()) {
            json.append('\n');
        }

        for (int index = 0; index < requests.size(); index++) {
            AllocationRequest request = requests.get(index);
            json.append(indent).append("{\n")
                    .append(indent).append("  \"id\": ")
                    .append(BenchmarkJson.quoted(request.getId())).append(",\n")
                    .append(indent).append("  \"name\": ")
                    .append(BenchmarkJson.quoted(request.getName())).append(",\n")
                    .append(indent).append("  \"start\": ")
                    .append(BenchmarkJson.quoted(request.getTimeWindow().getStart().toString()))
                    .append(",\n")
                    .append(indent).append("  \"end\": ")
                    .append(BenchmarkJson.quoted(request.getTimeWindow().getEnd().toString()))
                    .append(",\n")
                    .append(indent).append("  \"priority\": ").append(request.getPriority())
                    .append(",\n")
                    .append(indent).append("  \"requirements\": [");
            appendRequirements(json, request.getResourceRequirements(), indent + "    ");
            json.append(indent).append("  ]\n")
                    .append(indent).append('}')
                    .append(index + 1 < requests.size() ? ",\n" : "\n");
        }
    }

    private void appendRequirements(
            StringBuilder json,
            List<ResourceRequirement> requirements,
            String indent
    ) {
        if (!requirements.isEmpty()) {
            json.append('\n');
        }

        for (int index = 0; index < requirements.size(); index++) {
            ResourceRequirement requirement = requirements.get(index);
            json.append(indent).append("{\n")
                    .append(indent).append("  \"resourceType\": ")
                    .append(BenchmarkJson.quoted(requirement.getResourceType())).append(",\n")
                    .append(indent).append("  \"quantity\": ").append(requirement.getQuantity())
                    .append(",\n")
                    .append(indent).append("  \"requiredCapacities\": ");
            appendIntegerMap(json, requirement.getRequiredCapacities(), indent + "  ");
            json.append('\n')
                    .append(indent).append('}')
                    .append(index + 1 < requirements.size() ? ",\n" : "\n");
        }
    }

    private void appendIntegerMap(
            StringBuilder json,
            Map<String, Integer> values,
            String indent
    ) {
        Map<String, Integer> sorted = values == null ? Map.of() : new TreeMap<>(values);
        json.append('{');

        if (!sorted.isEmpty()) {
            json.append('\n');
        }

        int index = 0;

        for (Map.Entry<String, Integer> entry : sorted.entrySet()) {
            json.append(indent).append("  ")
                    .append(BenchmarkJson.quoted(entry.getKey()))
                    .append(": ").append(entry.getValue())
                    .append(++index < sorted.size() ? ",\n" : "\n");
        }

        if (!sorted.isEmpty()) {
            json.append(indent);
        }

        json.append('}');
    }
}
