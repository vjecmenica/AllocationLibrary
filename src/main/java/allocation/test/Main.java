package allocation.test;

import allocation.algorithm.AllocationAlgorithm;
import allocation.algorithm.BacktrackingAllocationAlgorithm;
import allocation.algorithm.CpSatAllocationAlgorithm;
import allocation.algorithm.GreedyAllocationAlgorithm;
import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.RejectedRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        List<AllocationAlgorithm> algorithms = List.of(
                new GreedyAllocationAlgorithm(),
                new BacktrackingAllocationAlgorithm(),
                new CpSatAllocationAlgorithm()
        );

        runScenario(
                "SCENARIO 1 - Raspored ispita",
                createExamResources(),
                createExamRequests(),
                algorithms
        );

        runScenario(
                "SCENARIO 2 - Greedy trap",
                createGreedyTrapResources(),
                createGreedyTrapRequests(),
                algorithms
        );

        runScenario(
                "SCENARIO 3 - Backtracking time limit",
                createTimeLimitResources(),
                createTimeLimitRequests(),
                List.of(
                        new GreedyAllocationAlgorithm(),
                        new BacktrackingAllocationAlgorithm(1),
                        new CpSatAllocationAlgorithm()
                )
        );

        runScenario(
                "SCENARIO 4 - Complex multi-resource optimization",
                createComplexOptimizationResources(),
                createComplexOptimizationRequests(),
                algorithms
        );
    }

    private static void runScenario(
            String scenarioName,
            List<Resource> resources,
            List<AllocationRequest> requests,
            List<AllocationAlgorithm> algorithms
    ) {
        System.out.println();
        System.out.println("##################################################");
        System.out.println(scenarioName);
        System.out.println("##################################################");
        System.out.println();

        System.out.println("===== TEST PODACI =====");
        System.out.println("Broj resursa: " + resources.size());
        System.out.println("Broj zahteva: " + requests.size());
        System.out.println();

        for (AllocationAlgorithm algorithm : algorithms) {
            System.out.println("=================================");
            System.out.println("Algoritam: " + algorithm.getName());
            System.out.println("=================================");

            try {
                AllocationResult result = algorithm.allocate(resources, requests);
                printResult(result);
            } catch (Exception e) {
                System.out.println("Greška prilikom izvršavanja algoritma:");
                System.out.println(e.getMessage());
                e.printStackTrace();
            }

            System.out.println();
        }
    }

    private static List<Resource> createExamResources() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 1, 8, 0);
        LocalDateTime dayEnd = LocalDateTime.of(2026, 7, 1, 18, 0);

        TimeWindow wholeDay = new TimeWindow(dayStart, dayEnd);

        Resource sala1 = new Resource(
                "R1",
                "Sala 1",
                "ROOM",
                Map.of("people", 40),
                List.of(wholeDay)
        );

        Resource sala2 = new Resource(
                "R2",
                "Sala 2",
                "ROOM",
                Map.of("people", 80),
                List.of(wholeDay)
        );

        Resource sala3 = new Resource(
                "R3",
                "Sala 3",
                "ROOM",
                Map.of("people", 25),
                List.of(wholeDay)
        );

        Resource asistent1 = new Resource(
                "S1",
                "Asistent Marko",
                "STAFF",
                Map.of(),
                List.of(wholeDay)
        );

        Resource asistent2 = new Resource(
                "S2",
                "Asistent Jovan",
                "STAFF",
                Map.of(),
                List.of(wholeDay)
        );

        Resource asistent3 = new Resource(
                "S3",
                "Asistent Nikola",
                "STAFF",
                Map.of(),
                List.of(wholeDay)
        );

        return List.of(
                sala1,
                sala2,
                sala3,
                asistent1,
                asistent2,
                asistent3
        );
    }

    private static List<AllocationRequest> createExamRequests() {
        AllocationRequest oop2 = new AllocationRequest(
                "REQ1",
                "Ispit iz OOP2",
                LocalDateTime.of(2026, 7, 1, 10, 0),
                180,
                10,
                List.of(
                        new ResourceRequirement("ROOM", 1, Map.of("people", 35)),
                        new ResourceRequirement("STAFF", 2, Map.of())
                )
        );

        AllocationRequest baze = new AllocationRequest(
                "REQ2",
                "Ispit iz baza podataka",
                LocalDateTime.of(2026, 7, 1, 10, 0),
                120,
                9,
                List.of(
                        new ResourceRequirement("ROOM", 1, Map.of("people", 70)),
                        new ResourceRequirement("STAFF", 2, Map.of())
                )
        );

        AllocationRequest algoritmi = new AllocationRequest(
                "REQ3",
                "Ispit iz algoritama",
                LocalDateTime.of(2026, 7, 1, 13, 0),
                120,
                8,
                List.of(
                        new ResourceRequirement("ROOM", 1, Map.of("people", 30)),
                        new ResourceRequirement("STAFF", 1, Map.of())
                )
        );

        AllocationRequest vestacka = new AllocationRequest(
                "REQ4",
                "Ispit iz veštačke inteligencije",
                LocalDateTime.of(2026, 7, 1, 11, 0),
                120,
                7,
                List.of(
                        new ResourceRequirement("ROOM", 1, Map.of("people", 90)),
                        new ResourceRequirement("STAFF", 1, Map.of())
                )
        );

        AllocationRequest matematika = new AllocationRequest(
                "REQ5",
                "Ispit iz matematike",
                LocalDateTime.of(2026, 7, 1, 15, 0),
                120,
                6,
                List.of(
                        new ResourceRequirement("ROOM", 1, Map.of("people", 20)),
                        new ResourceRequirement("STAFF", 1, Map.of())
                )
        );

        return List.of(oop2, baze, algoritmi, vestacka, matematika);
    }

    private static List<Resource> createGreedyTrapResources() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 1, 8, 0);
        LocalDateTime dayEnd = LocalDateTime.of(2026, 7, 1, 18, 0);

        TimeWindow wholeDay = new TimeWindow(dayStart, dayEnd);

        Resource velikaSala = new Resource(
                "R_BIG",
                "Velika sala",
                "ROOM",
                Map.of("people", 100),
                List.of(wholeDay)
        );

        Resource malaSala = new Resource(
                "R_SMALL",
                "Mala sala",
                "ROOM",
                Map.of("people", 30),
                List.of(wholeDay)
        );

        return List.of(velikaSala, malaSala);
    }

    private static List<AllocationRequest> createGreedyTrapRequests() {
        AllocationRequest maliIspit = new AllocationRequest(
                "REQ_SMALL",
                "Mali ispit",
                LocalDateTime.of(2026, 7, 1, 10, 0),
                120,
                10,
                List.of(
                        new ResourceRequirement("ROOM", 1, Map.of("people", 30))
                )
        );

        AllocationRequest velikiIspit = new AllocationRequest(
                "REQ_BIG",
                "Veliki ispit",
                LocalDateTime.of(2026, 7, 1, 10, 0),
                120,
                9,
                List.of(
                        new ResourceRequirement("ROOM", 1, Map.of("people", 100))
                )
        );

        return List.of(maliIspit, velikiIspit);
    }

    private static List<Resource> createTimeLimitResources() {
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

        return resources;
    }

    private static List<AllocationRequest> createTimeLimitRequests() {
        List<AllocationRequest> requests = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            requests.add(
                    new AllocationRequest(
                            "REQ_LIMIT_" + i,
                            "Ispit " + i,
                            LocalDateTime.of(2026, 7, 1, 10, 0),
                            120,
                            11 - i,
                            List.of(
                                    new ResourceRequirement("ROOM", 1, Map.of("people", 30))
                            )
                    )
            );
        }

        return requests;
    }

    private static List<Resource> createComplexOptimizationResources() {
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 2, 8, 0);
        LocalDateTime dayEnd = LocalDateTime.of(2026, 7, 2, 18, 0);

        TimeWindow wholeDay = new TimeWindow(dayStart, dayEnd);

        Resource auditorium = new Resource(
                "C_ROOM_BIG",
                "Auditorium",
                "ROOM",
                Map.of("people", 120),
                List.of(wholeDay)
        );

        Resource lab = new Resource(
                "C_ROOM_LAB",
                "Computer lab",
                "ROOM",
                Map.of("people", 40),
                List.of(wholeDay)
        );

        Resource seminarRoom = new Resource(
                "C_ROOM_SMALL",
                "Seminar room",
                "ROOM",
                Map.of("people", 30),
                List.of(wholeDay)
        );

        Resource assistantAna = new Resource(
                "C_STAFF_ANA",
                "Assistant Ana",
                "STAFF",
                Map.of(),
                List.of(wholeDay)
        );

        Resource assistantBoris = new Resource(
                "C_STAFF_BORIS",
                "Assistant Boris",
                "STAFF",
                Map.of(),
                List.of(wholeDay)
        );

        Resource assistantCeca = new Resource(
                "C_STAFF_CECA",
                "Assistant Ceca",
                "STAFF",
                Map.of(),
                List.of(wholeDay)
        );

        Resource projector = new Resource(
                "C_PROJECTOR_1",
                "Projector 1",
                "PROJECTOR",
                Map.of(),
                List.of(wholeDay)
        );

        return List.of(
                auditorium,
                lab,
                seminarRoom,
                assistantAna,
                assistantBoris,
                assistantCeca,
                projector
        );
    }

    private static List<AllocationRequest> createComplexOptimizationRequests() {
        AllocationRequest smallWorkshop = new AllocationRequest(
                "C_REQ_SMALL_WORKSHOP",
                "Small workshop",
                LocalDateTime.of(2026, 7, 2, 10, 0),
                120,
                10,
                List.of(
                        new ResourceRequirement("ROOM", 1, Map.of("people", 30)),
                        new ResourceRequirement("STAFF", 1, Map.of())
                )
        );

        AllocationRequest keynoteWithProjector = new AllocationRequest(
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
        );

        AllocationRequest labSession = new AllocationRequest(
                "C_REQ_LAB_SESSION",
                "Parallel lab session",
                LocalDateTime.of(2026, 7, 2, 10, 0),
                120,
                8,
                List.of(
                        new ResourceRequirement("ROOM", 1, Map.of("people", 40)),
                        new ResourceRequirement("STAFF", 2, Map.of())
                )
        );

        AllocationRequest afternoonDemo = new AllocationRequest(
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
        );

        return List.of(
                smallWorkshop,
                keynoteWithProjector,
                labSession,
                afternoonDemo
        );
    }

    private static void printResult(AllocationResult result) {
        if (result == null) {
            System.out.println("Rezultat je null. Verovatno algoritam još nije implementiran.");
            return;
        }

        System.out.println("Uspešno alocirani zahtevi:");
        System.out.println();

        if (result.getAllocations() == null || result.getAllocations().isEmpty()) {
            System.out.println("Nema uspešno alociranih zahteva.");
        } else {
            for (Allocation allocation : result.getAllocations()) {
                System.out.println("- " + allocation.getRequest().getName());

                System.out.println("  Dodeljeni resursi:");

                if (allocation.getAssignedResources() == null || allocation.getAssignedResources().isEmpty()) {
                    System.out.println("    nema");
                } else {
                    for (Resource resource : allocation.getAssignedResources()) {
                        System.out.println("    - " + resource.getName() + " [" + resource.getType() + "]");
                    }
                }

                System.out.println("  Termin: "
                        + allocation.getRequest().getTimeWindow().getStart()
                        + " - "
                        + allocation.getRequest().getTimeWindow().getEnd());

                System.out.println();
            }
        }

        System.out.println("Odbijeni zahtevi:");
        System.out.println();

        if (result.getRejectedRequests() == null || result.getRejectedRequests().isEmpty()) {
            System.out.println("Nema odbijenih zahteva.");
        } else {
            for (RejectedRequest rejected : result.getRejectedRequests()) {
                System.out.println("- " + rejected.getRequest().getName());
                System.out.println("  Razlog: " + rejected.getReason());
                System.out.println();
            }
        }

        if (result.getStatistics() != null) {
            System.out.println("Statistika:");
            System.out.println("Ukupno zahteva: " + result.getStatistics().getTotalRequests());
            System.out.println("Alocirano: " + result.getStatistics().getAllocatedRequests());
            System.out.println("Odbijeno: " + result.getStatistics().getRejectedRequests());
            System.out.println("Vreme izvršavanja: " + result.getStatistics().getExecutionTimeMs() + " ms");
            System.out.println("Ukupan prioritet alociranih zahteva: " + result.getStatistics().getTotalPriorityScore());
            System.out.println("Broj obiđenih stanja: " + result.getStatistics().getExploredStates());
            System.out.println("Prekinut zbog limita: " + result.getStatistics().isStoppedByLimit());

            if (result.getStatistics().getAlgorithmStatus() != null) {
                System.out.println("Status algoritma: " + result.getStatistics().getAlgorithmStatus());
            }

            if (result.getStatistics().getObjectiveValue() > 0) {
                System.out.println("Vrednost ciljne funkcije: " + result.getStatistics().getObjectiveValue());
            }
        }
    }
}
