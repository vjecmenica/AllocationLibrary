package allocation.api.dto;

import allocation.service.AllocationAlgorithmType;

import java.util.List;
import java.util.Map;

/**
 * Response returned after comparing all supported algorithms on one request.
 */
public record AllocationComparisonApiResponse(
        Map<AllocationAlgorithmType, ComparisonEntryDto> results,
        int bestTotalPriorityScore,
        List<AllocationAlgorithmType> bestScoreAlgorithms,
        AllocationAlgorithmType fastestAlgorithm
) {

    /**
     * Result for one algorithm in this specific comparison run.
     */
    public record ComparisonEntryDto(
            AllocationAlgorithmType algorithm,
            double measuredExecutionTimeMs,
            AllocationApiResponse.AllocationResultDto allocationResult
    ) {
    }
}
