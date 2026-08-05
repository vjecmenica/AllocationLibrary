package allocation.benchmark;

import allocation.generator.GeneratedScenario;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Calculates a canonical SHA-256 fingerprint of the complete logical scenario.
 */
public final class BenchmarkScenarioFingerprint {

    private static final String FORMAT_MARKER = "allocation-benchmark-scenario-v1";

    private BenchmarkScenarioFingerprint() {
    }

    public static String calculate(GeneratedScenario scenario) {
        if (scenario == null) {
            throw new IllegalArgumentException("Scenario must not be null.");
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeString(output, FORMAT_MARKER);
                writeResources(output, scenario.getResources());
                writeRequests(output, scenario.getRequests());
            }

            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return toLowercaseHex(digest);
        } catch (IOException exception) {
            throw new IllegalStateException("Scenario fingerprint could not be calculated.", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static void writeResources(DataOutputStream output, List<Resource> resources) throws IOException {
        writeListSize(output, resources, "Resource list");

        for (Resource resource : resources) {
            if (resource == null) {
                throw new IllegalArgumentException("Resource list must not contain null elements.");
            }

            writeString(output, resource.getId());
            writeString(output, resource.getName());
            writeString(output, resource.getType());
            writeCapacities(output, resource.getCapacities());
            writeAvailability(output, resource.getAvailability());
        }
    }

    private static void writeAvailability(
            DataOutputStream output,
            List<TimeWindow> availability
    ) throws IOException {
        if (availability == null) {
            output.writeInt(-1);
            return;
        }

        output.writeInt(availability.size());

        for (TimeWindow window : availability) {
            if (window == null) {
                throw new IllegalArgumentException("Availability list must not contain null elements.");
            }

            writeString(output, window.getStart().toString());
            writeString(output, window.getEnd().toString());
        }
    }

    private static void writeRequests(
            DataOutputStream output,
            List<AllocationRequest> requests
    ) throws IOException {
        writeListSize(output, requests, "Request list");

        for (AllocationRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("Request list must not contain null elements.");
            }

            writeString(output, request.getId());
            writeString(output, request.getName());
            writeString(output, request.getTimeWindow().getStart().toString());
            writeString(output, request.getTimeWindow().getEnd().toString());
            output.writeInt(request.getPriority());
            writeRequirements(output, request.getResourceRequirements());
        }
    }

    private static void writeRequirements(
            DataOutputStream output,
            List<ResourceRequirement> requirements
    ) throws IOException {
        writeListSize(output, requirements, "Resource requirement list");

        for (ResourceRequirement requirement : requirements) {
            if (requirement == null) {
                throw new IllegalArgumentException(
                        "Resource requirement list must not contain null elements."
                );
            }

            writeString(output, requirement.getResourceType());
            output.writeInt(requirement.getQuantity());
            writeCapacities(output, requirement.getRequiredCapacities());
        }
    }

    private static void writeCapacities(
            DataOutputStream output,
            Map<String, Integer> capacities
    ) throws IOException {
        if (capacities == null) {
            output.writeInt(-1);
            return;
        }

        List<Map.Entry<String, Integer>> entries = capacities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .toList();
        output.writeInt(entries.size());

        for (Map.Entry<String, Integer> entry : entries) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Capacity entries must not contain null values.");
            }

            writeString(output, entry.getKey());
            output.writeInt(entry.getValue());
        }
    }

    private static void writeListSize(
            DataOutputStream output,
            List<?> values,
            String fieldName
    ) throws IOException {
        if (values == null) {
            throw new IllegalArgumentException(fieldName + " must not be null.");
        }

        output.writeInt(values.size());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("Fingerprint string value must not be null.");
        }

        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String toLowercaseHex(byte[] bytes) {
        StringBuilder hexadecimal = new StringBuilder(bytes.length * 2);

        for (byte value : bytes) {
            hexadecimal.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            hexadecimal.append(Character.forDigit(value & 0x0f, 16));
        }

        return hexadecimal.toString();
    }
}
