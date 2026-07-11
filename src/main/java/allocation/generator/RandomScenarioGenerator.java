package allocation.generator;

import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RandomScenarioGenerator {

    private static final String ROOM = "ROOM";
    private static final String STAFF = "STAFF";
    private static final String PROJECTOR = "PROJECTOR";

    public GeneratedScenario generate(ScenarioGenerationConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Konfiguracija generatora ne sme biti null.");
        }

        Random random = new Random(config.getSeed());
        ResourceTypeCounts typeCounts = calculateResourceTypeCounts(config.getResourceCount());

        List<Resource> resources = generateResources(config, typeCounts, random);
        List<AllocationRequest> requests = generateRequests(config, typeCounts, random);

        return new GeneratedScenario(
                config.getScenarioName(),
                config.getSeed(),
                resources,
                requests
        );
    }

    private List<Resource> generateResources(
            ScenarioGenerationConfig config,
            ResourceTypeCounts typeCounts,
            Random random
    ) {
        List<Resource> resources = new ArrayList<>();
        TimeWindow availability = new TimeWindow(
                config.getScenarioStart(),
                config.getScenarioStart().plusMinutes((long) (config.getTimeSlotCount() + 3) * 60)
        );

        for (int i = 1; i <= typeCounts.roomCount; i++) {
            int capacity = randomRoomCapacity(random, i);

            resources.add(
                    new Resource(
                            "ROOM_" + i,
                            "Room " + i,
                            ROOM,
                            Map.of("people", capacity),
                            List.of(availability)
                    )
            );
        }

        for (int i = 1; i <= typeCounts.staffCount; i++) {
            resources.add(
                    new Resource(
                            "STAFF_" + i,
                            "Staff " + i,
                            STAFF,
                            Map.of(),
                            List.of(availability)
                    )
            );
        }

        for (int i = 1; i <= typeCounts.projectorCount; i++) {
            resources.add(
                    new Resource(
                            "PROJECTOR_" + i,
                            "Projector " + i,
                            PROJECTOR,
                            Map.of(),
                            List.of(availability)
                    )
            );
        }

        return resources;
    }

    private List<AllocationRequest> generateRequests(
            ScenarioGenerationConfig config,
            ResourceTypeCounts typeCounts,
            Random random
    ) {
        List<AllocationRequest> requests = new ArrayList<>();

        for (int i = 1; i <= config.getRequestCount(); i++) {
            List<ResourceRequirement> requirements = new ArrayList<>();

            requirements.add(
                    new ResourceRequirement(
                            ROOM,
                            1,
                            Map.of("people", randomRequiredPeople(random))
                    )
            );

            if (typeCounts.staffCount > 0
                    && random.nextDouble() < config.getStaffRequirementProbability()) {
                requirements.add(
                        new ResourceRequirement(
                                STAFF,
                                randomStaffQuantity(random, typeCounts.staffCount),
                                Map.of()
                        )
                );
            }

            if (typeCounts.projectorCount > 0
                    && random.nextDouble() < config.getProjectorRequirementProbability()) {
                requirements.add(new ResourceRequirement(PROJECTOR, 1, Map.of()));
            }

            requests.add(
                    new AllocationRequest(
                            "REQ_" + i,
                            "Generated request " + i,
                            requestStartTime(config, random),
                            randomDurationMinutes(random),
                            randomPriority(random),
                            requirements
                    )
            );
        }

        return requests;
    }

    private ResourceTypeCounts calculateResourceTypeCounts(int totalResources) {
        if (totalResources == 1) {
            return new ResourceTypeCounts(1, 0, 0);
        }

        if (totalResources == 2) {
            return new ResourceTypeCounts(1, 1, 0);
        }

        int roomCount = Math.max(1, (int) Math.round(totalResources * 0.55));
        int staffCount = Math.max(1, (int) Math.round(totalResources * 0.30));
        int projectorCount = totalResources - roomCount - staffCount;

        if (projectorCount < 1) {
            projectorCount = 1;

            if (roomCount >= staffCount && roomCount > 1) {
                roomCount--;
            } else {
                staffCount--;
            }
        }

        while (roomCount + staffCount + projectorCount > totalResources) {
            if (roomCount > staffCount && roomCount > 1) {
                roomCount--;
            } else if (staffCount > 1) {
                staffCount--;
            } else {
                projectorCount--;
            }
        }

        while (roomCount + staffCount + projectorCount < totalResources) {
            roomCount++;
        }

        return new ResourceTypeCounts(roomCount, staffCount, projectorCount);
    }

    private int randomRoomCapacity(Random random, int roomIndex) {
        if (roomIndex == 1) {
            return 120;
        }

        int[] capacities = {25, 30, 40, 60, 80, 100};
        return capacities[random.nextInt(capacities.length)];
    }

    private int randomRequiredPeople(Random random) {
        int[] requiredPeopleOptions = {20, 25, 30, 40, 60, 80, 100, 120};
        return requiredPeopleOptions[random.nextInt(requiredPeopleOptions.length)];
    }

    private int randomStaffQuantity(Random random, int availableStaffCount) {
        if (availableStaffCount < 2) {
            return 1;
        }

        return random.nextDouble() < 0.25 ? 2 : 1;
    }

    private LocalDateTime requestStartTime(
            ScenarioGenerationConfig config,
            Random random
    ) {
        int slotIndex = random.nextInt(config.getTimeSlotCount());
        return config.getScenarioStart().plusMinutes((long) slotIndex * 60);
    }

    private int randomDurationMinutes(Random random) {
        int[] durations = {60, 90, 120, 180};
        return durations[random.nextInt(durations.length)];
    }

    private int randomPriority(Random random) {
        return random.nextInt(10) + 1;
    }

    private static class ResourceTypeCounts {

        private int roomCount;
        private int staffCount;
        private int projectorCount;

        private ResourceTypeCounts(int roomCount, int staffCount, int projectorCount) {
            this.roomCount = roomCount;
            this.staffCount = staffCount;
            this.projectorCount = projectorCount;
        }
    }
}
