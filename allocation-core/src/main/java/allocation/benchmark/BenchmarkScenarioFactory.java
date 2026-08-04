package allocation.benchmark;

import allocation.generator.GeneratedScenario;
import allocation.generator.RandomScenarioGenerator;
import allocation.generator.ScenarioGenerationConfig;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class BenchmarkScenarioFactory {

    private static final LocalDateTime BASE_START = LocalDateTime.of(2026, 9, 1, 8, 0);

    public GeneratedScenario create(
            BenchmarkProfile profile,
            long seed,
            BenchmarkConfiguration configuration
    ) {
        if (profile == null) {
            throw new IllegalArgumentException("Benchmark profile must not be null.");
        }

        if (configuration == null) {
            throw new IllegalArgumentException("Benchmark configuration must not be null.");
        }

        return switch (profile) {
            case GREEDY_TRAP -> greedyTrap(seed);
            case BALANCED_SMALL -> randomScenario(profile, seed, 8, 8, 4, 0.35, 0.15);
            case BALANCED_MEDIUM -> randomScenario(profile, seed, 30, 30, 7, 0.45, 0.25);
            case CONFLICT_HEAVY -> randomScenario(profile, seed, 12, 24, 2, 0.70, 0.45);
            case CAPACITY_HEAVY -> capacityHeavy(seed);
            case SCALE -> scale(seed, configuration);
        };
    }

    private GeneratedScenario randomScenario(
            BenchmarkProfile profile,
            long seed,
            int resourceCount,
            int requestCount,
            int timeSlotCount,
            double staffProbability,
            double projectorProbability
    ) {
        return new RandomScenarioGenerator().generate(
                new ScenarioGenerationConfig(
                        profile.name(),
                        resourceCount,
                        requestCount,
                        seed,
                        BASE_START,
                        timeSlotCount,
                        staffProbability,
                        projectorProbability
                )
        );
    }

    private GeneratedScenario greedyTrap(long seed) {
        TimeWindow availability = new TimeWindow(BASE_START, BASE_START.plusHours(8));
        List<Resource> resources = List.of(
                new Resource(
                        "R_BIG",
                        "Large room",
                        "ROOM",
                        Map.of("people", 100),
                        List.of(availability)
                ),
                new Resource(
                        "R_SMALL",
                        "Small room",
                        "ROOM",
                        Map.of("people", 30),
                        List.of(availability)
                )
        );
        List<AllocationRequest> requests = List.of(
                new AllocationRequest(
                        "REQ_SMALL",
                        "Small exam",
                        BASE_START.plusHours(2),
                        120,
                        10,
                        List.of(new ResourceRequirement("ROOM", 1, Map.of("people", 30)))
                ),
                new AllocationRequest(
                        "REQ_BIG",
                        "Large exam",
                        BASE_START.plusHours(2),
                        120,
                        9,
                        List.of(new ResourceRequirement("ROOM", 1, Map.of("people", 100)))
                )
        );

        return new GeneratedScenario(BenchmarkProfile.GREEDY_TRAP.name(), seed, resources, requests);
    }

    private GeneratedScenario capacityHeavy(long seed) {
        Random random = new Random(seed);
        TimeWindow availability = new TimeWindow(BASE_START, BASE_START.plusHours(10));
        List<Resource> resources = new ArrayList<>();
        int[] capacities = {20, 30, 40, 50, 60};

        for (int i = 1; i <= 12; i++) {
            resources.add(
                    new Resource(
                            "CAP_ROOM_" + i,
                            "Capacity room " + i,
                            "ROOM",
                            Map.of("people", capacities[random.nextInt(capacities.length)]),
                            List.of(availability)
                    )
            );
        }

        List<AllocationRequest> requests = new ArrayList<>();
        int[] requiredCapacities = {30, 50, 70, 90, 110, 130};

        for (int i = 1; i <= 18; i++) {
            requests.add(
                    new AllocationRequest(
                            "CAP_REQ_" + i,
                            "Capacity request " + i,
                            BASE_START.plusHours(random.nextInt(4)),
                            60 + random.nextInt(2) * 60,
                            random.nextInt(10) + 1,
                            List.of(
                                    new ResourceRequirement(
                                            "ROOM",
                                            1,
                                            Map.of(
                                                    "people",
                                                    requiredCapacities[
                                                            random.nextInt(requiredCapacities.length)
                                                    ]
                                            )
                                    )
                            )
                    )
            );
        }

        return new GeneratedScenario(
                BenchmarkProfile.CAPACITY_HEAVY.name(),
                seed,
                resources,
                requests
        );
    }

    private GeneratedScenario scale(long seed, BenchmarkConfiguration configuration) {
        Random random = new Random(seed);
        int resourceTypeCount = configuration.getScaleResourceTypeCount();
        TimeWindow availability = new TimeWindow(BASE_START, BASE_START.plusHours(12));
        List<Resource> resources = new ArrayList<>();

        for (int i = 0; i < configuration.getScaleResourceCount(); i++) {
            int typeIndex = i % resourceTypeCount;
            resources.add(
                    new Resource(
                            "SCALE_RES_" + (i + 1),
                            "Scale resource " + (i + 1),
                            scaleType(typeIndex),
                            Map.of("units", 20 + random.nextInt(101)),
                            List.of(availability)
                    )
            );
        }

        List<AllocationRequest> requests = new ArrayList<>();

        for (int i = 0; i < configuration.getScaleRequestCount(); i++) {
            int typeIndex = random.nextInt(resourceTypeCount);
            requests.add(
                    new AllocationRequest(
                            "SCALE_REQ_" + (i + 1),
                            "Scale request " + (i + 1),
                            BASE_START.plusMinutes((long) random.nextInt(6) * 60),
                            60 + random.nextInt(3) * 60,
                            random.nextInt(10) + 1,
                            List.of(
                                    new ResourceRequirement(
                                            scaleType(typeIndex),
                                            1,
                                            Map.of("units", 10 + random.nextInt(111))
                                    )
                            )
                    )
            );
        }

        return new GeneratedScenario(BenchmarkProfile.SCALE.name(), seed, resources, requests);
    }

    private String scaleType(int typeIndex) {
        return "TYPE_" + (typeIndex + 1);
    }
}
