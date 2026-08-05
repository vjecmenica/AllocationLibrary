package allocation.benchmark;

import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkScenarioSnapshotWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void snapshotContainsCompleteScenarioOnceAndSortsCapacityKeys() throws Exception {
        Map<String, Integer> capacities = new LinkedHashMap<>();
        capacities.put("zeta", 20);
        capacities.put("alpha", 10);
        Map<String, Integer> requiredCapacities = new LinkedHashMap<>();
        requiredCapacities.put("seats", 30);
        requiredCapacities.put("people", 20);
        LocalDateTime start = LocalDateTime.parse("2026-09-01T08:00:00");
        Resource resource = new Resource(
                "R_1",
                "Room \"A\"",
                "ROOM",
                capacities,
                List.of(new TimeWindow(start, start.plusHours(8)))
        );
        AllocationRequest request = new AllocationRequest(
                "REQ_1",
                "Exam A",
                start.plusHours(1),
                120,
                9,
                List.of(new ResourceRequirement("ROOM", 1, requiredCapacities))
        );
        BenchmarkScenarioSnapshot snapshot = new BenchmarkScenarioSnapshot(
                BenchmarkProfile.BALANCED_SMALL,
                42,
                BenchmarkTestData.FINGERPRINT,
                List.of(resource),
                List.of(request)
        );
        Path output = tempDir.resolve("scenario-snapshots.json");

        new BenchmarkScenarioSnapshotWriter().write(output, List.of(snapshot));

        String json = Files.readString(output, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertEquals(1, occurrences(json, "\"scenarioFingerprint\""));
        assertTrue(json.contains("\"id\": \"R_1\""));
        assertTrue(json.contains("\"id\": \"REQ_1\""));
        assertTrue(json.contains("\"start\": \"2026-09-01T08:00\""));
        assertTrue(json.indexOf("\"alpha\"") < json.indexOf("\"zeta\""));
        assertTrue(json.indexOf("\"people\"") < json.indexOf("\"seats\""));
        assertTrue(json.endsWith("\n"));
    }

    private int occurrences(String value, String fragment) {
        int count = 0;
        int index = 0;

        while ((index = value.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }

        return count;
    }
}
