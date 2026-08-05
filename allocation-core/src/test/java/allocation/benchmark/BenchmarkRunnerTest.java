package allocation.benchmark;

import allocation.generator.GeneratedScenario;
import allocation.model.AllocationResult;
import allocation.model.AllocationStatistics;
import allocation.model.Resource;
import allocation.service.AllocationAlgorithmType;
import allocation.service.AllocationExecutionResult;
import allocation.service.AllocationOptions;
import allocation.service.AllocationSelectionMode;
import allocation.service.ResourceAllocator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkRunnerTest {

    @Test
    void smokeRunExecutesAllThreeAlgorithms() {
        BenchmarkConfiguration configuration = configuration(0, 1);

        BenchmarkRun run = new BenchmarkRunner().run(configuration);

        assertEquals(3, run.getRawResults().size());
        assertEquals(
                List.of(
                        AllocationAlgorithmType.GREEDY,
                        AllocationAlgorithmType.BACKTRACKING,
                        AllocationAlgorithmType.CP_SAT
                ),
                run.getRawResults().stream().map(BenchmarkResult::getAlgorithm).toList()
        );
        assertEquals(List.of(10, 19, 19), scores(run));
        assertTrue(run.getRawResults().stream().allMatch(result -> result.getSchemaVersion() == 2));
        assertTrue(run.getRawResults().stream().allMatch(result -> result.getMeasuredExecutionTimeMs() >= 0));
        assertEquals(6, run.getRequestOutcomes().size());
        assertEquals(1, run.getScenarioSnapshots().size());
        assertEquals(
                List.of(
                        "GREEDY-REQ_SMALL-ACCEPTED",
                        "GREEDY-REQ_BIG-REJECTED",
                        "BACKTRACKING-REQ_SMALL-ACCEPTED",
                        "BACKTRACKING-REQ_BIG-ACCEPTED",
                        "CP_SAT-REQ_SMALL-ACCEPTED",
                        "CP_SAT-REQ_BIG-ACCEPTED"
                ),
                run.getRequestOutcomes().stream()
                        .map(outcome -> outcome.getAlgorithm() + "-" + outcome.getRequestId()
                                + "-" + outcome.getOutcome())
                        .toList()
        );
    }

    @Test
    void warmupExecutionsAreNotIncludedInRawResults() {
        CountingResourceAllocator allocator = new CountingResourceAllocator(false);
        BenchmarkRunner runner = runner(allocator);

        BenchmarkRun run = runner.run(configuration(2, 1));

        assertEquals(9, allocator.callCount);
        assertEquals(3, run.getRawResults().size());
        assertEquals(3, run.getSummaryResults().size());
    }

    @Test
    void rawResultsUseStableRepetitionAndAlgorithmOrder() {
        BenchmarkRun run = runner(new CountingResourceAllocator(false)).run(configuration(0, 3));

        assertEquals(
                List.of(
                        "1-GREEDY",
                        "1-BACKTRACKING",
                        "1-CP_SAT",
                        "2-GREEDY",
                        "2-BACKTRACKING",
                        "2-CP_SAT",
                        "3-GREEDY",
                        "3-BACKTRACKING",
                        "3-CP_SAT"
                ),
                run.getRawResults().stream()
                        .map(result -> result.getRepetition() + "-" + result.getAlgorithm())
                        .toList()
        );
        assertEquals(18, run.getRequestOutcomes().size());
        assertEquals(
                List.of(1, 2, 3),
                run.getRequestOutcomes().stream()
                        .map(BenchmarkRequestOutcome::getRepetition)
                        .distinct()
                        .toList()
        );
        assertEquals(1, run.getScenarioSnapshots().size());
    }

    @Test
    void measuredExecutionPositionsReflectRotatedCallOrder() {
        CountingResourceAllocator allocator = new CountingResourceAllocator(false);

        BenchmarkRun run = runner(allocator).run(configuration(0, 3));

        assertEquals(
                List.of(
                        AllocationAlgorithmType.GREEDY,
                        AllocationAlgorithmType.BACKTRACKING,
                        AllocationAlgorithmType.CP_SAT,
                        AllocationAlgorithmType.BACKTRACKING,
                        AllocationAlgorithmType.CP_SAT,
                        AllocationAlgorithmType.GREEDY,
                        AllocationAlgorithmType.CP_SAT,
                        AllocationAlgorithmType.GREEDY,
                        AllocationAlgorithmType.BACKTRACKING
                ),
                allocator.executedAlgorithms
        );
        assertEquals(
                List.of(1, 2, 3, 3, 1, 2, 2, 3, 1),
                run.getRawResults().stream()
                        .map(BenchmarkResult::getExecutionOrderPosition)
                        .toList()
        );
    }

    @Test
    void warmupExecutionsUseTheSameBalancedRotation() {
        CountingResourceAllocator allocator = new CountingResourceAllocator(false);

        runner(allocator).run(configuration(3, 1));

        assertEquals(
                List.of(
                        AllocationAlgorithmType.GREEDY,
                        AllocationAlgorithmType.BACKTRACKING,
                        AllocationAlgorithmType.CP_SAT,
                        AllocationAlgorithmType.BACKTRACKING,
                        AllocationAlgorithmType.CP_SAT,
                        AllocationAlgorithmType.GREEDY,
                        AllocationAlgorithmType.CP_SAT,
                        AllocationAlgorithmType.GREEDY,
                        AllocationAlgorithmType.BACKTRACKING
                ),
                allocator.executedAlgorithms.subList(0, 9)
        );
    }

    @Test
    void everyAlgorithmAndRepetitionSharesOneScenarioFingerprint() {
        BenchmarkRun run = runner(new CountingResourceAllocator(false)).run(configuration(0, 3));

        assertEquals(
                1,
                run.getRawResults().stream()
                        .map(BenchmarkResult::getScenarioFingerprint)
                        .distinct()
                        .count()
        );
        assertTrue(
                run.getRawResults().get(0).getScenarioFingerprint().matches("[0-9a-f]{64}")
        );
        assertTrue(run.getRequestOutcomes().stream().allMatch(outcome ->
                outcome.getScenarioFingerprint().equals(
                        run.getRawResults().get(0).getScenarioFingerprint()
                )
        ));
        assertEquals(
                run.getRawResults().get(0).getScenarioFingerprint(),
                run.getScenarioSnapshots().get(0).getScenarioFingerprint()
        );
    }

    @Test
    void algorithmsReceiveCopiesAndCannotMutateOriginalScenario() {
        BenchmarkConfiguration configuration = configuration(0, 1);
        GeneratedScenario original = new BenchmarkScenarioFactory().create(
                BenchmarkProfile.GREEDY_TRAP,
                42L,
                configuration
        );
        int originalCapacity = original.getResources().get(0).getCapacity("people");
        BenchmarkRunner runner = runner(new CountingResourceAllocator(true));

        runner.runScenario(BenchmarkProfile.GREEDY_TRAP, original, configuration);

        assertEquals(originalCapacity, original.getResources().get(0).getCapacity("people"));
        assertFalse(original.getResources().get(0).getCapacities().containsKey("mutated"));
    }

    @Test
    void benchmarkConfigurationRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkConfiguration(0, 100, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkConfiguration(1, 0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new BenchmarkConfiguration(1, 100, 0));
    }

    private BenchmarkRunner runner(ResourceAllocator allocator) {
        return new BenchmarkRunner(
                allocator,
                new BenchmarkScenarioFactory(),
                Clock.fixed(Instant.parse("2026-09-01T08:00:00Z"), ZoneOffset.UTC),
                () -> UUID.fromString("00000000-0000-0000-0000-000000000001")
        );
    }

    private BenchmarkConfiguration configuration(int warmups, int measuredRuns) {
        return new BenchmarkConfiguration(
                List.of(BenchmarkProfile.GREEDY_TRAP),
                List.of(42L),
                warmups,
                measuredRuns,
                500,
                1.0,
                Path.of("benchmark-results"),
                10,
                10,
                3
        );
    }

    private List<Integer> scores(BenchmarkRun run) {
        return run.getRawResults().stream().map(BenchmarkResult::getTotalPriorityScore).toList();
    }

    private static class CountingResourceAllocator extends ResourceAllocator {

        private final boolean mutateInput;
        private int callCount;
        private final List<AllocationAlgorithmType> executedAlgorithms = new ArrayList<>();

        private CountingResourceAllocator(boolean mutateInput) {
            this.mutateInput = mutateInput;
        }

        @Override
        public AllocationExecutionResult execute(
                List<Resource> resources,
                List<allocation.model.AllocationRequest> requests,
                AllocationOptions options
        ) {
            callCount++;
            executedAlgorithms.add(options.getAlgorithmType());

            if (mutateInput) {
                resources.get(0).getCapacities().put("mutated", 1);
            }

            int score = switch (options.getAlgorithmType()) {
                case GREEDY -> 10;
                case BACKTRACKING, CP_SAT -> 19;
            };
            String status = options.getAlgorithmType() == AllocationAlgorithmType.CP_SAT
                    ? "OPTIMAL"
                    : null;
            AllocationResult result = new AllocationResult(
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new AllocationStatistics(
                            requests.size(),
                            0,
                            requests.size(),
                            0,
                            score,
                            0,
                            false,
                            status,
                            0
                    )
            );

            return new AllocationExecutionResult(
                    AllocationSelectionMode.EXPLICIT,
                    options.getAlgorithmType(),
                    options.getAlgorithmType(),
                    null,
                    "Selected for a benchmark test.",
                    0.25,
                    result
            );
        }
    }
}
