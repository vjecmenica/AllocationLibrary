package allocation.benchmark;

import allocation.generator.GeneratedScenario;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkScenarioFingerprintTest {

    private final BenchmarkScenarioFactory scenarioFactory = new BenchmarkScenarioFactory();

    @Test
    void sameScenarioProducesSameLowercaseSha256Fingerprint() {
        GeneratedScenario first = scenario(BenchmarkProfile.BALANCED_SMALL, 42);
        GeneratedScenario second = scenario(BenchmarkProfile.BALANCED_SMALL, 42);

        String firstFingerprint = BenchmarkScenarioFingerprint.calculate(first);

        assertEquals(firstFingerprint, BenchmarkScenarioFingerprint.calculate(second));
        assertTrue(firstFingerprint.matches("[0-9a-f]{64}"));
    }

    @Test
    void differentSeedChangesRandomScenarioFingerprint() {
        assertNotEquals(
                BenchmarkScenarioFingerprint.calculate(scenario(BenchmarkProfile.BALANCED_SMALL, 42)),
                BenchmarkScenarioFingerprint.calculate(scenario(BenchmarkProfile.BALANCED_SMALL, 43))
        );
    }

    @Test
    void resourceCapacityChangeChangesFingerprint() {
        GeneratedScenario original = scenario(BenchmarkProfile.GREEDY_TRAP, 42);
        Resource source = original.getResources().get(0);
        Map<String, Integer> capacities = new LinkedHashMap<>(source.getCapacities());
        capacities.put("people", capacities.get("people") + 1);
        Resource changed = new Resource(
                source.getId(),
                source.getName(),
                source.getType(),
                capacities,
                source.getAvailability()
        );
        List<Resource> resources = new ArrayList<>(original.getResources());
        resources.set(0, changed);

        assertNotEquals(
                BenchmarkScenarioFingerprint.calculate(original),
                BenchmarkScenarioFingerprint.calculate(copy(original, resources, original.getRequests()))
        );
    }

    @Test
    void resourceOrderChangeChangesFingerprint() {
        GeneratedScenario original = scenario(BenchmarkProfile.GREEDY_TRAP, 42);
        List<Resource> reversed = new ArrayList<>(original.getResources());
        java.util.Collections.reverse(reversed);

        assertNotEquals(
                BenchmarkScenarioFingerprint.calculate(original),
                BenchmarkScenarioFingerprint.calculate(copy(original, reversed, original.getRequests()))
        );
    }

    @Test
    void requestPriorityChangeChangesFingerprint() {
        GeneratedScenario original = scenario(BenchmarkProfile.GREEDY_TRAP, 42);
        AllocationRequest source = original.getRequests().get(0);
        int durationMinutes = Math.toIntExact(
                Duration.between(
                        source.getTimeWindow().getStart(),
                        source.getTimeWindow().getEnd()
                ).toMinutes()
        );
        AllocationRequest changed = new AllocationRequest(
                source.getId(),
                source.getName(),
                source.getTimeWindow().getStart(),
                durationMinutes,
                source.getPriority() + 1,
                source.getResourceRequirements()
        );
        List<AllocationRequest> requests = new ArrayList<>(original.getRequests());
        requests.set(0, changed);

        assertNotEquals(
                BenchmarkScenarioFingerprint.calculate(original),
                BenchmarkScenarioFingerprint.calculate(copy(original, original.getResources(), requests))
        );
    }

    @Test
    void capacityMapInsertionOrderDoesNotChangeFingerprint() {
        GeneratedScenario original = scenario(BenchmarkProfile.GREEDY_TRAP, 42);
        Resource source = original.getResources().get(0);
        Map<String, Integer> firstOrder = new LinkedHashMap<>();
        firstOrder.put("people", 100);
        firstOrder.put("seats", 90);
        Map<String, Integer> secondOrder = new LinkedHashMap<>();
        secondOrder.put("seats", 90);
        secondOrder.put("people", 100);

        assertEquals(
                fingerprintWithFirstResourceCapacities(original, source, firstOrder),
                fingerprintWithFirstResourceCapacities(original, source, secondOrder)
        );
    }

    private String fingerprintWithFirstResourceCapacities(
            GeneratedScenario original,
            Resource source,
            Map<String, Integer> capacities
    ) {
        Resource changed = new Resource(
                source.getId(),
                source.getName(),
                source.getType(),
                capacities,
                source.getAvailability()
        );
        List<Resource> resources = new ArrayList<>(original.getResources());
        resources.set(0, changed);
        return BenchmarkScenarioFingerprint.calculate(copy(original, resources, original.getRequests()));
    }

    private GeneratedScenario scenario(BenchmarkProfile profile, long seed) {
        return scenarioFactory.create(profile, seed, configuration());
    }

    private GeneratedScenario copy(
            GeneratedScenario original,
            List<Resource> resources,
            List<AllocationRequest> requests
    ) {
        return new GeneratedScenario(original.getName(), original.getSeed(), resources, requests);
    }

    private BenchmarkConfiguration configuration() {
        return new BenchmarkConfiguration(
                List.of(BenchmarkProfile.BALANCED_SMALL),
                List.of(42L),
                0,
                1,
                500,
                1.0,
                Path.of("benchmark-results"),
                20,
                20,
                3
        );
    }
}
