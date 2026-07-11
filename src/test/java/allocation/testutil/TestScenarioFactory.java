package allocation.testutil;

import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TestScenarioFactory {

    private TestScenarioFactory() {
    }

    public static ScenarioData examScenario() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 1, 8, 0);
        LocalDateTime dayEnd = LocalDateTime.of(2026, 7, 1, 18, 0);
        TimeWindow wholeDay = new TimeWindow(dayStart, dayEnd);

        List<Resource> resources = List.of(
                new Resource("R1", "Sala 1", "ROOM", Map.of("people", 40), List.of(wholeDay)),
                new Resource("R2", "Sala 2", "ROOM", Map.of("people", 80), List.of(wholeDay)),
                new Resource("R3", "Sala 3", "ROOM", Map.of("people", 25), List.of(wholeDay)),
                new Resource("S1", "Asistent Marko", "STAFF", Map.of(), List.of(wholeDay)),
                new Resource("S2", "Asistent Jovan", "STAFF", Map.of(), List.of(wholeDay)),
                new Resource("S3", "Asistent Nikola", "STAFF", Map.of(), List.of(wholeDay))
        );

        List<AllocationRequest> requests = List.of(
                new AllocationRequest(
                        "REQ1",
                        "Ispit iz OOP2",
                        LocalDateTime.of(2026, 7, 1, 10, 0),
                        180,
                        10,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 35)),
                                new ResourceRequirement("STAFF", 2, Map.of())
                        )
                ),
                new AllocationRequest(
                        "REQ2",
                        "Ispit iz baza podataka",
                        LocalDateTime.of(2026, 7, 1, 10, 0),
                        120,
                        9,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 70)),
                                new ResourceRequirement("STAFF", 2, Map.of())
                        )
                ),
                new AllocationRequest(
                        "REQ3",
                        "Ispit iz algoritama",
                        LocalDateTime.of(2026, 7, 1, 13, 0),
                        120,
                        8,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 30)),
                                new ResourceRequirement("STAFF", 1, Map.of())
                        )
                ),
                new AllocationRequest(
                        "REQ4",
                        "Ispit iz vestacke inteligencije",
                        LocalDateTime.of(2026, 7, 1, 11, 0),
                        120,
                        7,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 90)),
                                new ResourceRequirement("STAFF", 1, Map.of())
                        )
                ),
                new AllocationRequest(
                        "REQ5",
                        "Ispit iz matematike",
                        LocalDateTime.of(2026, 7, 1, 15, 0),
                        120,
                        6,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 20)),
                                new ResourceRequirement("STAFF", 1, Map.of())
                        )
                )
        );

        return new ScenarioData(resources, requests);
    }

    public static ScenarioData greedyTrapScenario() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 1, 8, 0);
        LocalDateTime dayEnd = LocalDateTime.of(2026, 7, 1, 18, 0);
        TimeWindow wholeDay = new TimeWindow(dayStart, dayEnd);

        List<Resource> resources = List.of(
                new Resource("R_BIG", "Velika sala", "ROOM", Map.of("people", 100), List.of(wholeDay)),
                new Resource("R_SMALL", "Mala sala", "ROOM", Map.of("people", 30), List.of(wholeDay))
        );

        List<AllocationRequest> requests = List.of(
                new AllocationRequest(
                        "REQ_SMALL",
                        "Mali ispit",
                        LocalDateTime.of(2026, 7, 1, 10, 0),
                        120,
                        10,
                        List.of(new ResourceRequirement("ROOM", 1, Map.of("people", 30)))
                ),
                new AllocationRequest(
                        "REQ_BIG",
                        "Veliki ispit",
                        LocalDateTime.of(2026, 7, 1, 10, 0),
                        120,
                        9,
                        List.of(new ResourceRequirement("ROOM", 1, Map.of("people", 100)))
                )
        );

        return new ScenarioData(resources, requests);
    }

    public static ScenarioData complexMultiResourceScenario() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 2, 8, 0);
        LocalDateTime dayEnd = LocalDateTime.of(2026, 7, 2, 18, 0);
        TimeWindow wholeDay = new TimeWindow(dayStart, dayEnd);

        List<Resource> resources = List.of(
                new Resource("C_ROOM_BIG", "Auditorium", "ROOM", Map.of("people", 120), List.of(wholeDay)),
                new Resource("C_ROOM_LAB", "Computer lab", "ROOM", Map.of("people", 40), List.of(wholeDay)),
                new Resource("C_ROOM_SMALL", "Seminar room", "ROOM", Map.of("people", 30), List.of(wholeDay)),
                new Resource("C_STAFF_ANA", "Assistant Ana", "STAFF", Map.of(), List.of(wholeDay)),
                new Resource("C_STAFF_BORIS", "Assistant Boris", "STAFF", Map.of(), List.of(wholeDay)),
                new Resource("C_STAFF_CECA", "Assistant Ceca", "STAFF", Map.of(), List.of(wholeDay)),
                new Resource("C_PROJECTOR_1", "Projector 1", "PROJECTOR", Map.of(), List.of(wholeDay))
        );

        List<AllocationRequest> requests = List.of(
                new AllocationRequest(
                        "C_REQ_SMALL_WORKSHOP",
                        "Small workshop",
                        LocalDateTime.of(2026, 7, 2, 10, 0),
                        120,
                        10,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 30)),
                                new ResourceRequirement("STAFF", 1, Map.of())
                        )
                ),
                new AllocationRequest(
                        "C_REQ_KEYNOTE",
                        "Large keynote with projector",
                        LocalDateTime.of(2026, 7, 2, 10, 0),
                        120,
                        9,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 100)),
                                new ResourceRequirement("STAFF", 1, Map.of()),
                                new ResourceRequirement("PROJECTOR", 1, Map.of())
                        )
                ),
                new AllocationRequest(
                        "C_REQ_LAB_SESSION",
                        "Parallel lab session",
                        LocalDateTime.of(2026, 7, 2, 10, 0),
                        120,
                        8,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 40)),
                                new ResourceRequirement("STAFF", 2, Map.of())
                        )
                ),
                new AllocationRequest(
                        "C_REQ_AFTERNOON_DEMO",
                        "Afternoon project demo",
                        LocalDateTime.of(2026, 7, 2, 13, 0),
                        90,
                        7,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 30)),
                                new ResourceRequirement("STAFF", 1, Map.of()),
                                new ResourceRequirement("PROJECTOR", 1, Map.of())
                        )
                )
        );

        return new ScenarioData(resources, requests);
    }

    public static ScenarioData projectorConflictScenario() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 3, 8, 0);
        LocalDateTime dayEnd = LocalDateTime.of(2026, 7, 3, 18, 0);
        TimeWindow wholeDay = new TimeWindow(dayStart, dayEnd);

        List<Resource> resources = List.of(
                new Resource("P_ROOM_1", "Room 1", "ROOM", Map.of("people", 50), List.of(wholeDay)),
                new Resource("P_ROOM_2", "Room 2", "ROOM", Map.of("people", 50), List.of(wholeDay)),
                new Resource("P_STAFF_1", "Staff 1", "STAFF", Map.of(), List.of(wholeDay)),
                new Resource("P_STAFF_2", "Staff 2", "STAFF", Map.of(), List.of(wholeDay)),
                new Resource("P_PROJECTOR_1", "Projector 1", "PROJECTOR", Map.of(), List.of(wholeDay))
        );

        List<AllocationRequest> requests = List.of(
                new AllocationRequest(
                        "P_REQ_1",
                        "Projector session 1",
                        LocalDateTime.of(2026, 7, 3, 10, 0),
                        120,
                        10,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 30)),
                                new ResourceRequirement("STAFF", 1, Map.of()),
                                new ResourceRequirement("PROJECTOR", 1, Map.of())
                        )
                ),
                new AllocationRequest(
                        "P_REQ_2",
                        "Projector session 2",
                        LocalDateTime.of(2026, 7, 3, 10, 30),
                        120,
                        9,
                        List.of(
                                new ResourceRequirement("ROOM", 1, Map.of("people", 30)),
                                new ResourceRequirement("STAFF", 1, Map.of()),
                                new ResourceRequirement("PROJECTOR", 1, Map.of())
                        )
                )
        );

        return new ScenarioData(resources, requests);
    }

    public static ScenarioData timeLimitScenario() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 1, 8, 0);
        LocalDateTime dayEnd = LocalDateTime.of(2026, 7, 1, 18, 0);
        TimeWindow wholeDay = new TimeWindow(dayStart, dayEnd);

        List<Resource> resources = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            resources.add(
                    new Resource(
                            "ROOM_" + i,
                            "Sala " + i,
                            "ROOM",
                            Map.of("people", 100),
                            List.of(wholeDay)
                    )
            );
        }

        List<AllocationRequest> requests = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            requests.add(
                    new AllocationRequest(
                            "REQ_LIMIT_" + i,
                            "Ispit " + i,
                            LocalDateTime.of(2026, 7, 1, 10, 0),
                            120,
                            11 - i,
                            List.of(new ResourceRequirement("ROOM", 1, Map.of("people", 30)))
                    )
            );
        }

        return new ScenarioData(resources, requests);
    }

    public static final class ScenarioData {

        private final List<Resource> resources;
        private final List<AllocationRequest> requests;

        public ScenarioData(List<Resource> resources, List<AllocationRequest> requests) {
            this.resources = resources;
            this.requests = requests;
        }

        public List<Resource> getResources() {
            return resources;
        }

        public List<AllocationRequest> getRequests() {
            return requests;
        }
    }
}
