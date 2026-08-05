package allocation.benchmark;

import allocation.generator.GeneratedScenario;
import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.AllocationStatistics;
import allocation.model.RejectedRequest;
import allocation.model.Resource;
import allocation.service.AllocationAlgorithmType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BenchmarkRequestOutcomeExtractorTest {

    private final BenchmarkRequestOutcomeExtractor extractor =
            new BenchmarkRequestOutcomeExtractor();
    private GeneratedScenario scenario;
    private AllocationRequest smallRequest;
    private AllocationRequest bigRequest;
    private Resource bigRoom;
    private Resource smallRoom;

    @BeforeEach
    void setUp() {
        BenchmarkConfiguration configuration = new BenchmarkConfiguration(
                List.of(BenchmarkProfile.GREEDY_TRAP),
                List.of(42L),
                0,
                1,
                500,
                1.0,
                Path.of("benchmark-results"),
                10,
                10,
                3
        );
        scenario = new BenchmarkScenarioFactory().create(
                BenchmarkProfile.GREEDY_TRAP,
                42,
                configuration
        );
        bigRoom = scenario.getResources().get(0);
        smallRoom = scenario.getResources().get(1);
        smallRequest = scenario.getRequests().get(0);
        bigRequest = scenario.getRequests().get(1);
    }

    @Test
    void extractsAcceptedRejectedAndAssignedResourcesInOriginalRequestOrder() {
        List<Allocation> allocations = new ArrayList<>(List.of(
                new Allocation(smallRequest, List.of(bigRoom, smallRoom))
        ));
        List<RejectedRequest> rejections = new ArrayList<>(List.of(
                new RejectedRequest(bigRequest, "No suitable room, \"capacity\" missing.")
        ));
        AllocationResult allocationResult = result(allocations, rejections);

        List<BenchmarkRequestOutcome> outcomes = extractor.extract(
                benchmarkResult(),
                scenario,
                allocationResult
        );

        assertEquals(List.of("REQ_SMALL", "REQ_BIG"), outcomes.stream()
                .map(BenchmarkRequestOutcome::getRequestId).toList());
        assertEquals(BenchmarkRequestOutcomeStatus.ACCEPTED, outcomes.get(0).getOutcome());
        assertEquals(List.of("R_BIG", "R_SMALL"), outcomes.get(0).getAssignedResourceIds());
        assertEquals(List.of("Large room", "Small room"), outcomes.get(0).getAssignedResourceNames());
        assertEquals("", outcomes.get(0).getRejectionReason());
        assertEquals(BenchmarkRequestOutcomeStatus.REJECTED, outcomes.get(1).getOutcome());
        assertEquals(List.of(), outcomes.get(1).getAssignedResourceIds());
        assertEquals("No suitable room, \"capacity\" missing.", outcomes.get(1).getRejectionReason());
        assertEquals(BenchmarkTestData.FINGERPRINT, outcomes.get(0).getScenarioFingerprint());
        assertEquals(2, outcomes.get(0).getRepetition());
        assertEquals(3, outcomes.get(0).getExecutionOrderPosition());

        assertEquals(1, allocations.size());
        assertEquals(1, rejections.size());
        assertSame(smallRequest, allocations.get(0).getRequest());
        assertSame(bigRequest, rejections.get(0).getRequest());
    }

    @Test
    void missingResultBecomesUnknown() {
        BenchmarkRequestOutcome outcome = extractForSmall(List.of(), List.of());

        assertEquals(BenchmarkRequestOutcomeStatus.UNKNOWN, outcome.getOutcome());
        assertEquals(
                "Inconsistent result: request has neither an allocation nor a rejection.",
                outcome.getRejectionReason()
        );
    }

    @Test
    void duplicateAllocationBecomesUnknown() {
        Allocation allocation = new Allocation(smallRequest, List.of(bigRoom));
        BenchmarkRequestOutcome outcome = extractForSmall(
                List.of(allocation, allocation),
                List.of()
        );

        assertEquals(BenchmarkRequestOutcomeStatus.UNKNOWN, outcome.getOutcome());
        assertEquals(
                "Inconsistent result: multiple allocations were returned for the request.",
                outcome.getRejectionReason()
        );
    }

    @Test
    void duplicateRejectionBecomesUnknown() {
        RejectedRequest rejection = new RejectedRequest(smallRequest, "Rejected");
        BenchmarkRequestOutcome outcome = extractForSmall(
                List.of(),
                List.of(rejection, rejection)
        );

        assertEquals(BenchmarkRequestOutcomeStatus.UNKNOWN, outcome.getOutcome());
        assertEquals(
                "Inconsistent result: multiple rejections were returned for the request.",
                outcome.getRejectionReason()
        );
    }

    @Test
    void simultaneousAllocationAndRejectionBecomesUnknown() {
        BenchmarkRequestOutcome outcome = extractForSmall(
                List.of(new Allocation(smallRequest, List.of(bigRoom))),
                List.of(new RejectedRequest(smallRequest, "Rejected"))
        );

        assertEquals(BenchmarkRequestOutcomeStatus.UNKNOWN, outcome.getOutcome());
        assertEquals(
                "Inconsistent result: request is both accepted and rejected.",
                outcome.getRejectionReason()
        );
    }

    private BenchmarkRequestOutcome extractForSmall(
            List<Allocation> allocations,
            List<RejectedRequest> rejections
    ) {
        return extractor.extract(benchmarkResult(), scenario, result(allocations, rejections))
                .get(0);
    }

    private BenchmarkResult benchmarkResult() {
        BenchmarkResult base = BenchmarkTestData.result(
                BenchmarkProfile.GREEDY_TRAP,
                42,
                2,
                AllocationAlgorithmType.GREEDY,
                1,
                10,
                1,
                false,
                null
        );
        return new BenchmarkResult(
                base.getBenchmarkRunId(),
                base.getGeneratedAt(),
                base.getProfile(),
                base.getSeed(),
                base.getScenarioFingerprint(),
                base.getRepetition(),
                base.getAlgorithm(),
                3,
                base.getResourceCount(),
                base.getRequestCount(),
                base.getBacktrackingTimeLimitMs(),
                base.getCpSatTimeLimitSeconds(),
                base.getTotalPriorityScore(),
                base.getAllocatedRequests(),
                base.getRejectedRequests(),
                base.getMeasuredExecutionTimeMs(),
                base.getAlgorithmExecutionTimeMs(),
                base.getExploredStates(),
                base.isStoppedByLimit(),
                base.getAlgorithmStatus(),
                base.getObjectiveValue()
        );
    }

    private AllocationResult result(
            List<Allocation> allocations,
            List<RejectedRequest> rejections
    ) {
        return new AllocationResult(
                allocations,
                rejections,
                new AllocationStatistics(2, allocations.size(), rejections.size(), 0, 0, 0, false)
        );
    }
}
