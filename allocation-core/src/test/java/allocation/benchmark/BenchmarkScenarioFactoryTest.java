package allocation.benchmark;

import allocation.generator.GeneratedScenario;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkScenarioFactoryTest {

    private final BenchmarkScenarioFactory factory = new BenchmarkScenarioFactory();

    @Test
    void sameSeedAndConfigurationProduceIdenticalScenario() {
        BenchmarkConfiguration configuration = configuration(20, 20, 3);

        GeneratedScenario first = factory.create(BenchmarkProfile.BALANCED_MEDIUM, 1234, configuration);
        GeneratedScenario second = factory.create(BenchmarkProfile.BALANCED_MEDIUM, 1234, configuration);

        assertEquals(snapshot(first), snapshot(second));
    }

    @Test
    void differentSeedsProduceDifferentScenarios() {
        BenchmarkConfiguration configuration = configuration(20, 20, 3);

        GeneratedScenario first = factory.create(BenchmarkProfile.BALANCED_MEDIUM, 1234, configuration);
        GeneratedScenario second = factory.create(BenchmarkProfile.BALANCED_MEDIUM, 5678, configuration);

        assertNotEquals(snapshot(first), snapshot(second));
    }

    @Test
    void generatedProfilesContainUniqueIdsAndValidModelValues() {
        BenchmarkConfiguration configuration = configuration(15, 17, 3);

        for (BenchmarkProfile profile : BenchmarkProfile.values()) {
            GeneratedScenario scenario = factory.create(profile, 42, configuration);
            assertUniqueIds(scenario);
            assertValidValues(scenario);
        }
    }

    @Test
    void scaleProfileUsesConfiguredCountsAndResourceTypes() {
        BenchmarkConfiguration configuration = configuration(17, 23, 4);

        GeneratedScenario scenario = factory.create(BenchmarkProfile.SCALE, 42, configuration);

        assertEquals(17, scenario.getResources().size());
        assertEquals(23, scenario.getRequests().size());
        assertEquals(
                Set.of("TYPE_1", "TYPE_2", "TYPE_3", "TYPE_4"),
                scenario.getResources().stream().map(Resource::getType).collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void greedyTrapHasKnownStructure() {
        GeneratedScenario scenario = factory.create(
                BenchmarkProfile.GREEDY_TRAP,
                42,
                configuration(20, 20, 3)
        );

        assertEquals(List.of("R_BIG", "R_SMALL"), scenario.getResources().stream().map(Resource::getId).toList());
        assertEquals(List.of("REQ_SMALL", "REQ_BIG"), scenario.getRequests().stream().map(AllocationRequest::getId).toList());
        assertEquals(100, scenario.getResources().get(0).getCapacity("people"));
        assertEquals(30, scenario.getResources().get(1).getCapacity("people"));
        assertEquals(List.of(10, 9), scenario.getRequests().stream().map(AllocationRequest::getPriority).toList());
        assertEquals(
                scenario.getRequests().get(0).getTimeWindow().getStart(),
                scenario.getRequests().get(1).getTimeWindow().getStart()
        );
    }

    private BenchmarkConfiguration configuration(int resources, int requests, int types) {
        return new BenchmarkConfiguration(
                List.of(BenchmarkProfile.BALANCED_SMALL),
                List.of(42L),
                0,
                1,
                500,
                1.0,
                Path.of("benchmark-results"),
                resources,
                requests,
                types
        );
    }

    private void assertUniqueIds(GeneratedScenario scenario) {
        Set<String> resourceIds = new HashSet<>();
        Set<String> requestIds = new HashSet<>();

        assertTrue(scenario.getResources().stream().allMatch(resource -> resourceIds.add(resource.getId())));
        assertTrue(scenario.getRequests().stream().allMatch(request -> requestIds.add(request.getId())));
    }

    private void assertValidValues(GeneratedScenario scenario) {
        for (Resource resource : scenario.getResources()) {
            assertTrue(resource.getAvailability() != null);

            for (TimeWindow window : resource.getAvailability()) {
                assertTrue(window.getStart().isBefore(window.getEnd()));
            }
        }

        for (AllocationRequest request : scenario.getRequests()) {
            assertTrue(request.getTimeWindow().getStart().isBefore(request.getTimeWindow().getEnd()));
            assertTrue(request.getPriority() >= 1 && request.getPriority() <= 10);
            assertTrue(request.getResourceRequirements().stream().allMatch(requirement -> requirement.getQuantity() > 0));
        }
    }

    private String snapshot(GeneratedScenario scenario) {
        StringBuilder builder = new StringBuilder();
        builder.append(scenario.getName()).append('|').append(scenario.getSeed()).append('\n');

        for (Resource resource : scenario.getResources()) {
            builder.append(resource.getId()).append('|')
                    .append(resource.getName()).append('|')
                    .append(resource.getType()).append('|')
                    .append(new TreeMap<>(resource.getCapacities())).append('|');

            for (TimeWindow window : resource.getAvailability()) {
                builder.append(window.getStart()).append('-').append(window.getEnd()).append(';');
            }

            builder.append('\n');
        }

        for (AllocationRequest request : scenario.getRequests()) {
            builder.append(request.getId()).append('|')
                    .append(request.getName()).append('|')
                    .append(request.getTimeWindow().getStart()).append('|')
                    .append(request.getTimeWindow().getEnd()).append('|')
                    .append(request.getPriority()).append('|');

            for (ResourceRequirement requirement : request.getResourceRequirements()) {
                Map<String, Integer> capacities = requirement.getRequiredCapacities();
                builder.append(requirement.getResourceType()).append(':')
                        .append(requirement.getQuantity()).append(':')
                        .append(capacities == null ? Map.of() : new TreeMap<>(capacities)).append(';');
            }

            builder.append('\n');
        }

        return builder.toString();
    }
}
