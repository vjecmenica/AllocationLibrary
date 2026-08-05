package allocation.benchmark;

import allocation.generator.GeneratedScenario;
import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.RejectedRequest;
import allocation.model.Resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts stable request-level outcomes from an already completed measured result.
 */
public class BenchmarkRequestOutcomeExtractor {

    public List<BenchmarkRequestOutcome> extract(
            BenchmarkResult benchmarkResult,
            GeneratedScenario scenario,
            AllocationResult allocationResult
    ) {
        if (benchmarkResult == null || scenario == null || allocationResult == null) {
            throw new IllegalArgumentException("Outcome extraction arguments must not be null.");
        }

        Map<String, List<Allocation>> allocationsByRequest = groupAllocations(
                allocationResult.getAllocations()
        );
        Map<String, List<RejectedRequest>> rejectionsByRequest = groupRejections(
                allocationResult.getRejectedRequests()
        );
        List<BenchmarkRequestOutcome> outcomes = new ArrayList<>();

        for (AllocationRequest request : scenario.getRequests()) {
            List<Allocation> allocations = allocationsByRequest.getOrDefault(request.getId(), List.of());
            List<RejectedRequest> rejections = rejectionsByRequest.getOrDefault(request.getId(), List.of());
            outcomes.add(resolve(benchmarkResult, request, allocations, rejections));
        }

        return List.copyOf(outcomes);
    }

    private BenchmarkRequestOutcome resolve(
            BenchmarkResult benchmarkResult,
            AllocationRequest request,
            List<Allocation> allocations,
            List<RejectedRequest> rejections
    ) {
        if (allocations.size() == 1 && rejections.isEmpty()) {
            List<Resource> assignedResources = allocations.get(0).getAssignedResources();
            List<Resource> stableResources = assignedResources == null ? List.of() : assignedResources;
            return outcome(
                    benchmarkResult,
                    request,
                    BenchmarkRequestOutcomeStatus.ACCEPTED,
                    stableResources.stream().map(Resource::getId).toList(),
                    stableResources.stream().map(Resource::getName).toList(),
                    ""
            );
        }

        if (allocations.isEmpty() && rejections.size() == 1) {
            return outcome(
                    benchmarkResult,
                    request,
                    BenchmarkRequestOutcomeStatus.REJECTED,
                    List.of(),
                    List.of(),
                    rejections.get(0).getReason()
            );
        }

        return outcome(
                benchmarkResult,
                request,
                BenchmarkRequestOutcomeStatus.UNKNOWN,
                List.of(),
                List.of(),
                diagnosticMessage(allocations, rejections)
        );
    }

    private BenchmarkRequestOutcome outcome(
            BenchmarkResult result,
            AllocationRequest request,
            BenchmarkRequestOutcomeStatus status,
            List<String> assignedResourceIds,
            List<String> assignedResourceNames,
            String rejectionReason
    ) {
        return new BenchmarkRequestOutcome(
                result.getBenchmarkRunId(),
                result.getGeneratedAt(),
                result.getProfile(),
                result.getSeed(),
                result.getScenarioFingerprint(),
                result.getRepetition(),
                result.getAlgorithm(),
                result.getExecutionOrderPosition(),
                request.getId(),
                request.getName(),
                request.getPriority(),
                request.getTimeWindow().getStart(),
                request.getTimeWindow().getEnd(),
                status,
                assignedResourceIds,
                assignedResourceNames,
                rejectionReason
        );
    }

    private String diagnosticMessage(
            List<Allocation> allocations,
            List<RejectedRequest> rejections
    ) {
        if (!allocations.isEmpty() && !rejections.isEmpty()) {
            return "Inconsistent result: request is both accepted and rejected.";
        }

        if (allocations.size() > 1) {
            return "Inconsistent result: multiple allocations were returned for the request.";
        }

        if (rejections.size() > 1) {
            return "Inconsistent result: multiple rejections were returned for the request.";
        }

        return "Inconsistent result: request has neither an allocation nor a rejection.";
    }

    private Map<String, List<Allocation>> groupAllocations(List<Allocation> allocations) {
        Map<String, List<Allocation>> grouped = new LinkedHashMap<>();

        if (allocations == null) {
            return grouped;
        }

        for (Allocation allocation : allocations) {
            grouped.computeIfAbsent(allocation.getRequest().getId(), ignored -> new ArrayList<>())
                    .add(allocation);
        }

        return grouped;
    }

    private Map<String, List<RejectedRequest>> groupRejections(List<RejectedRequest> rejections) {
        Map<String, List<RejectedRequest>> grouped = new LinkedHashMap<>();

        if (rejections == null) {
            return grouped;
        }

        for (RejectedRequest rejection : rejections) {
            grouped.computeIfAbsent(rejection.getRequest().getId(), ignored -> new ArrayList<>())
                    .add(rejection);
        }

        return grouped;
    }
}
