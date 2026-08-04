package allocation.benchmark;

import allocation.generator.GeneratedScenario;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.AllocationStatistics;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;
import allocation.service.AllocationAlgorithmType;
import allocation.service.AllocationExecutionResult;
import allocation.service.AllocationOptions;
import allocation.service.ResourceAllocator;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Executes reproducible scenarios through the public allocation API.
 * Warmup executions are discarded; every measured execution is retained as a raw result.
 */
public class BenchmarkRunner {

    public static final List<AllocationAlgorithmType> ALGORITHM_ORDER = List.of(
            AllocationAlgorithmType.GREEDY,
            AllocationAlgorithmType.BACKTRACKING,
            AllocationAlgorithmType.CP_SAT
    );

    private final ResourceAllocator resourceAllocator;
    private final BenchmarkScenarioFactory scenarioFactory;
    private final Clock clock;
    private final Supplier<UUID> runIdSupplier;

    public BenchmarkRunner() {
        this(
                new ResourceAllocator(),
                new BenchmarkScenarioFactory(),
                Clock.systemUTC(),
                UUID::randomUUID
        );
    }

    BenchmarkRunner(
            ResourceAllocator resourceAllocator,
            BenchmarkScenarioFactory scenarioFactory,
            Clock clock,
            Supplier<UUID> runIdSupplier
    ) {
        this.resourceAllocator = resourceAllocator;
        this.scenarioFactory = scenarioFactory;
        this.clock = clock;
        this.runIdSupplier = runIdSupplier;
    }

    public BenchmarkRun run(BenchmarkConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Benchmark configuration must not be null.");
        }

        String benchmarkRunId = runIdSupplier.get().toString();
        Instant generatedAt = clock.instant();
        List<BenchmarkResult> results = new ArrayList<>();

        for (BenchmarkProfile profile : configuration.getProfiles()) {
            for (long seed : configuration.getSeeds()) {
                GeneratedScenario scenario = scenarioFactory.create(profile, seed, configuration);
                runScenario(
                        benchmarkRunId,
                        generatedAt,
                        profile,
                        scenario,
                        configuration,
                        results
                );
            }
        }

        List<BenchmarkSummaryResult> summaries = BenchmarkSummaryReport
                .fromResults(results)
                .getSummaries();

        return new BenchmarkRun(
                benchmarkRunId,
                generatedAt,
                configuration,
                results,
                summaries
        );
    }

    BenchmarkRun runScenario(
            BenchmarkProfile profile,
            GeneratedScenario scenario,
            BenchmarkConfiguration configuration
    ) {
        if (profile == null) {
            throw new IllegalArgumentException("Benchmark profile must not be null.");
        }

        if (scenario == null) {
            throw new IllegalArgumentException("Scenario must not be null.");
        }

        if (configuration == null) {
            throw new IllegalArgumentException("Benchmark configuration must not be null.");
        }

        String benchmarkRunId = runIdSupplier.get().toString();
        Instant generatedAt = clock.instant();
        List<BenchmarkResult> results = new ArrayList<>();

        runScenario(
                benchmarkRunId,
                generatedAt,
                profile,
                scenario,
                configuration,
                results
        );

        return new BenchmarkRun(
                benchmarkRunId,
                generatedAt,
                configuration,
                results,
                BenchmarkSummaryReport.fromResults(results).getSummaries()
        );
    }

    private void runScenario(
            String benchmarkRunId,
            Instant generatedAt,
            BenchmarkProfile profile,
            GeneratedScenario scenario,
            BenchmarkConfiguration configuration,
            List<BenchmarkResult> results
    ) {
        for (int warmup = 0; warmup < configuration.getWarmupRuns(); warmup++) {
            for (AllocationAlgorithmType algorithm : ALGORITHM_ORDER) {
                execute(scenario, configuration, algorithm);
            }
        }

        for (int repetition = 1; repetition <= configuration.getMeasuredRuns(); repetition++) {
            for (AllocationAlgorithmType algorithm : ALGORITHM_ORDER) {
                AllocationExecutionResult execution = execute(scenario, configuration, algorithm);
                results.add(
                        toBenchmarkResult(
                                benchmarkRunId,
                                generatedAt,
                                profile,
                                scenario,
                                configuration,
                                repetition,
                                algorithm,
                                execution
                        )
                );
            }
        }
    }

    private AllocationExecutionResult execute(
            GeneratedScenario scenario,
            BenchmarkConfiguration configuration,
            AllocationAlgorithmType algorithm
    ) {
        ScenarioCopy copy = copyScenario(scenario);

        return resourceAllocator.execute(
                copy.resources(),
                copy.requests(),
                new AllocationOptions(
                        algorithm,
                        configuration.getBacktrackingTimeLimitMs(),
                        configuration.getCpSatTimeLimitSeconds()
                )
        );
    }

    private BenchmarkResult toBenchmarkResult(
            String benchmarkRunId,
            Instant generatedAt,
            BenchmarkProfile profile,
            GeneratedScenario scenario,
            BenchmarkConfiguration configuration,
            int repetition,
            AllocationAlgorithmType algorithm,
            AllocationExecutionResult execution
    ) {
        AllocationResult allocationResult = execution.getAllocationResult();
        AllocationStatistics statistics = allocationResult.getStatistics();

        return new BenchmarkResult(
                benchmarkRunId,
                generatedAt,
                profile,
                scenario.getSeed(),
                repetition,
                algorithm,
                scenario.getResources().size(),
                scenario.getRequests().size(),
                configuration.getBacktrackingTimeLimitMs(),
                configuration.getCpSatTimeLimitSeconds(),
                statistics.getTotalPriorityScore(),
                statistics.getAllocatedRequests(),
                statistics.getRejectedRequests(),
                execution.getExecutionTimeMs(),
                statistics.getExecutionTimeMs(),
                statistics.getExploredStates(),
                statistics.isStoppedByLimit(),
                statistics.getAlgorithmStatus(),
                statistics.getObjectiveValue()
        );
    }

    private ScenarioCopy copyScenario(GeneratedScenario scenario) {
        List<Resource> resources = scenario.getResources().stream()
                .map(this::copyResource)
                .toList();
        List<AllocationRequest> requests = scenario.getRequests().stream()
                .map(this::copyRequest)
                .toList();

        return new ScenarioCopy(resources, requests);
    }

    private Resource copyResource(Resource resource) {
        Map<String, Integer> capacities = resource.getCapacities() == null
                ? null
                : new LinkedHashMap<>(resource.getCapacities());
        List<TimeWindow> availability = resource.getAvailability() == null
                ? null
                : resource.getAvailability().stream()
                        .map(window -> new TimeWindow(window.getStart(), window.getEnd()))
                        .toList();

        return new Resource(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                capacities,
                availability
        );
    }

    private AllocationRequest copyRequest(AllocationRequest request) {
        List<ResourceRequirement> requirements = request.getResourceRequirements().stream()
                .map(this::copyRequirement)
                .toList();
        long durationMinutes = java.time.Duration.between(
                request.getTimeWindow().getStart(),
                request.getTimeWindow().getEnd()
        ).toMinutes();

        return new AllocationRequest(
                request.getId(),
                request.getName(),
                request.getTimeWindow().getStart(),
                Math.toIntExact(durationMinutes),
                request.getPriority(),
                requirements
        );
    }

    private ResourceRequirement copyRequirement(ResourceRequirement requirement) {
        Map<String, Integer> requiredCapacities = requirement.getRequiredCapacities() == null
                ? null
                : new LinkedHashMap<>(requirement.getRequiredCapacities());

        return new ResourceRequirement(
                requirement.getResourceType(),
                requirement.getQuantity(),
                requiredCapacities
        );
    }

    private record ScenarioCopy(
            List<Resource> resources,
            List<AllocationRequest> requests
    ) {
    }
}
