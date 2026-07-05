package allocation.test;

import allocation.algorithm.AllocationAlgorithm;
import allocation.algorithm.GreedyAllocationAlgorithm;
import allocation.algorithm.BacktrackingAllocationAlgorithm;
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

//        List<Resource> resources = createTestResources();
//        List<AllocationRequest> requests = createTestRequests();
        List<Resource> resources = createGreedyTrapResources();
        List<AllocationRequest> requests = createGreedyTrapRequests();

        System.out.println("===== TEST PODACI =====");
        System.out.println("Broj resursa: " + resources.size());
        System.out.println("Broj zahteva: " + requests.size());
        System.out.println();

        List<AllocationAlgorithm> algorithms = new ArrayList<>();
        algorithms.add(new GreedyAllocationAlgorithm());
        algorithms.add(new BacktrackingAllocationAlgorithm());

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

    private static List<Resource> createTestResources() {
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

    private static List<AllocationRequest> createTestRequests() {
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
        }
    }
}