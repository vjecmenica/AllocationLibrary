package allocation.benchmark;

import allocation.algorithm.AllocationAlgorithm;
import allocation.algorithm.BacktrackingAllocationAlgorithm;
import allocation.algorithm.CpSatAllocationAlgorithm;
import allocation.algorithm.GreedyAllocationAlgorithm;
import allocation.generator.GeneratedScenario;
import allocation.model.AllocationResult;
import allocation.model.AllocationStatistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public class BenchmarkRunner {

    public List<BenchmarkResult> run(
            GeneratedScenario scenario,
            BenchmarkConfiguration configuration
    ) {
        if (scenario == null) {
            throw new IllegalArgumentException("Scenario ne sme biti null.");
        }

        if (configuration == null) {
            throw new IllegalArgumentException("Benchmark konfiguracija ne sme biti null.");
        }

        List<BenchmarkResult> results = new ArrayList<>();

        results.add(
                runAlgorithm(
                        scenario,
                        configuration,
                        GreedyAllocationAlgorithm::new
                )
        );

        results.add(
                runAlgorithm(
                        scenario,
                        configuration,
                        () -> new BacktrackingAllocationAlgorithm(configuration.getBacktrackingTimeLimitMs())
                )
        );

        results.add(
                runAlgorithm(
                        scenario,
                        configuration,
                        () -> new CpSatAllocationAlgorithm(configuration.getCpSatTimeLimitSeconds())
                )
        );

        return results;
    }

    private BenchmarkResult runAlgorithm(
            GeneratedScenario scenario,
            BenchmarkConfiguration configuration,
            Supplier<AllocationAlgorithm> algorithmSupplier
    ) {
        List<AllocationResult> runResults = new ArrayList<>();
        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < configuration.getRepetitions(); i++) {
            AllocationAlgorithm algorithm = algorithmSupplier.get();

            AllocationResult result = algorithm.allocate(
                    scenario.getResources(),
                    scenario.getRequests()
            );

            runResults.add(result);
            executionTimes.add(result.getStatistics().getExecutionTimeMs());
        }

        AllocationResult bestResult = chooseRepresentativeResult(runResults);
        assertStableWhenNotStopped(runResults);

        AllocationStatistics statistics = bestResult.getStatistics();

        return new BenchmarkResult(
                scenario.getName(),
                scenario.getSeed(),
                scenario.getResources().size(),
                scenario.getRequests().size(),
                algorithmSupplier.get().getName(),
                statistics.getAllocatedRequests(),
                statistics.getRejectedRequests(),
                statistics.getTotalPriorityScore(),
                median(executionTimes),
                statistics.getExploredStates(),
                statistics.isStoppedByLimit(),
                statistics.getAlgorithmStatus(),
                statistics.getObjectiveValue()
        );
    }

    private AllocationResult chooseRepresentativeResult(List<AllocationResult> results) {
        return results.stream()
                .max(
                        Comparator.comparingInt((AllocationResult result) ->
                                        result.getStatistics().getTotalPriorityScore()
                                )
                                .thenComparingInt(result ->
                                        result.getStatistics().getAllocatedRequests()
                                )
                                .thenComparing(result ->
                                        !result.getStatistics().isStoppedByLimit()
                                )
                )
                .orElseThrow();
    }

    private void assertStableWhenNotStopped(List<AllocationResult> results) {
        AllocationResult reference = null;

        for (AllocationResult result : results) {
            if (result.getStatistics().isStoppedByLimit()) {
                continue;
            }

            if (reference == null) {
                reference = result;
                continue;
            }

            if (reference.getStatistics().getTotalPriorityScore()
                    != result.getStatistics().getTotalPriorityScore()
                    || reference.getStatistics().getAllocatedRequests()
                    != result.getStatistics().getAllocatedRequests()) {
                throw new IllegalStateException("Algoritam nije dao stabilan rezultat bez vremenskog limita.");
            }
        }
    }

    private long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);

        int middle = sorted.size() / 2;

        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }

        return (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
