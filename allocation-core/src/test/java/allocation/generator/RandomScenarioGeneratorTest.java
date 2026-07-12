package allocation.generator;

import allocation.algorithm.BacktrackingAllocationAlgorithm;
import allocation.algorithm.CpSatAllocationAlgorithm;
import allocation.algorithm.GreedyAllocationAlgorithm;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static allocation.testutil.AllocationResultValidator.assertValid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomScenarioGeneratorTest {

    private final RandomScenarioGenerator generator = new RandomScenarioGenerator();

    @Test
    void sameSeedAndConfigProduceIdenticalScenario() {
        ScenarioGenerationConfig config = config("TEST", 12, 15, 1234L);

        GeneratedScenario first = generator.generate(config);
        GeneratedScenario second = generator.generate(config);

        assertEquals(snapshot(first), snapshot(second));
    }

    @Test
    void differentSeedsProduceDifferentScenarios() {
        GeneratedScenario first = generator.generate(config("TEST", 12, 15, 1234L));
        GeneratedScenario second = generator.generate(config("TEST", 12, 15, 5678L));

        assertNotEquals(snapshot(first), snapshot(second));
    }

    @Test
    void generatorCreatesConfiguredNumberOfResourcesAndRequests() {
        GeneratedScenario scenario = generator.generate(config("COUNT", 14, 17, 1001L));

        assertEquals(14, scenario.getResources().size());
        assertEquals(17, scenario.getRequests().size());
    }

    @Test
    void everyGeneratedResourceHasUniqueId() {
        GeneratedScenario scenario = generator.generate(config("RESOURCES", 20, 10, 1001L));

        Set<String> ids = new HashSet<>();

        for (Resource resource : scenario.getResources()) {
            assertTrue(ids.add(resource.getId()));
        }
    }

    @Test
    void everyGeneratedRequestHasUniqueId() {
        GeneratedScenario scenario = generator.generate(config("REQUESTS", 10, 20, 1001L));

        Set<String> ids = new HashSet<>();

        for (AllocationRequest request : scenario.getRequests()) {
            assertTrue(ids.add(request.getId()));
        }
    }

    @Test
    void everyGeneratedRequestHasRoomRequirement() {
        GeneratedScenario scenario = generator.generate(config("ROOMS", 10, 20, 1001L));

        for (AllocationRequest request : scenario.getRequests()) {
            assertTrue(
                    request.getResourceRequirements()
                            .stream()
                            .anyMatch(requirement -> "ROOM".equals(requirement.getResourceType()))
            );
        }
    }

    @Test
    void prioritiesAndTimeWindowsAreValid() {
        GeneratedScenario scenario = generator.generate(config("VALID", 10, 20, 1001L));

        for (Resource resource : scenario.getResources()) {
            assertValidTimeWindows(resource.getAvailability());
        }

        for (AllocationRequest request : scenario.getRequests()) {
            assertTrue(request.getPriority() >= 1);
            assertTrue(request.getPriority() <= 10);
            assertTrue(request.getTimeWindow().getStart().isBefore(request.getTimeWindow().getEnd()));
        }
    }

    @Test
    void generatedSmallScenarioProducesValidResultsForAllAlgorithms() {
        GeneratedScenario scenario = generator.generate(config("ALGORITHMS", 8, 8, 1001L));

        AllocationResult greedyResult = new GreedyAllocationAlgorithm().allocate(
                scenario.getResources(),
                scenario.getRequests()
        );
        AllocationResult backtrackingResult = new BacktrackingAllocationAlgorithm(500).allocate(
                scenario.getResources(),
                scenario.getRequests()
        );
        AllocationResult cpSatResult = new CpSatAllocationAlgorithm(2.0).allocate(
                scenario.getResources(),
                scenario.getRequests()
        );

        assertValid(greedyResult, scenario.getResources(), scenario.getRequests());
        assertValid(backtrackingResult, scenario.getResources(), scenario.getRequests());
        assertValid(cpSatResult, scenario.getResources(), scenario.getRequests());
    }

    private ScenarioGenerationConfig config(
            String name,
            int resourceCount,
            int requestCount,
            long seed
    ) {
        return new ScenarioGenerationConfig(
                name,
                resourceCount,
                requestCount,
                seed,
                LocalDateTime.of(2026, 7, 15, 8, 0),
                5,
                0.45,
                0.20
        );
    }

    private void assertValidTimeWindows(List<TimeWindow> windows) {
        assertTrue(windows != null && !windows.isEmpty());

        for (TimeWindow window : windows) {
            assertTrue(window.getStart().isBefore(window.getEnd()));
        }
    }

    private String snapshot(GeneratedScenario scenario) {
        StringBuilder builder = new StringBuilder();

        builder.append(scenario.getName())
                .append('|')
                .append(scenario.getSeed())
                .append('\n');

        for (Resource resource : scenario.getResources()) {
            builder.append("RESOURCE|")
                    .append(resource.getId())
                    .append('|')
                    .append(resource.getName())
                    .append('|')
                    .append(resource.getType())
                    .append('|')
                    .append(new TreeMap<>(resource.getCapacities()))
                    .append('|');

            for (TimeWindow window : resource.getAvailability()) {
                builder.append(window.getStart())
                        .append('-')
                        .append(window.getEnd())
                        .append(';');
            }

            builder.append('\n');
        }

        for (AllocationRequest request : scenario.getRequests()) {
            builder.append("REQUEST|")
                    .append(request.getId())
                    .append('|')
                    .append(request.getName())
                    .append('|')
                    .append(request.getTimeWindow().getStart())
                    .append('|')
                    .append(request.getTimeWindow().getEnd())
                    .append('|')
                    .append(request.getPriority())
                    .append('|');

            for (ResourceRequirement requirement : request.getResourceRequirements()) {
                Map<String, Integer> capacities = requirement.getRequiredCapacities();

                builder.append(requirement.getResourceType())
                        .append(':')
                        .append(requirement.getQuantity())
                        .append(':')
                        .append(capacities == null ? Map.of() : new TreeMap<>(capacities))
                        .append(';');
            }

            builder.append('\n');
        }

        return builder.toString();
    }
}
