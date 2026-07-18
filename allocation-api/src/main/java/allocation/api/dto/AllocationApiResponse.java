package allocation.api.dto;

import allocation.service.AllocationAlgorithmType;
import allocation.service.AllocationGoal;
import allocation.service.AllocationSelectionMode;

import java.util.List;

/**
 * Response returned after executing one allocation algorithm.
 */
public record AllocationApiResponse(
        AllocationSelectionMode selectionMode,
        AllocationAlgorithmType requestedAlgorithm,
        AllocationAlgorithmType executedAlgorithm,
        AllocationGoal goal,
        String selectionReason,
        double measuredExecutionTimeMs,
        List<AllocationDto> allocations,
        List<RejectedRequestDto> rejectedRequests,
        StatisticsDto statistics
) {

    /**
     * Allocation result payload used by comparison entries.
     */
    public record AllocationResultDto(
            List<AllocationDto> allocations,
            List<RejectedRequestDto> rejectedRequests,
            StatisticsDto statistics
    ) {
    }

    /**
     * Accepted request and its assigned resources.
     */
    public record AllocationDto(
            AllocationApiRequest.AllocationRequestDto request,
            List<AllocationApiRequest.ResourceDto> assignedResources
    ) {
    }

    /**
     * Rejected request and the rejection reason produced by the core library.
     */
    public record RejectedRequestDto(
            AllocationApiRequest.AllocationRequestDto request,
            String reason
    ) {
    }

    /**
     * Core allocation statistics. algorithmExecutionTimeMs is the long value
     * measured inside the core algorithm statistics.
     */
    public record StatisticsDto(
            int totalRequests,
            int allocatedRequests,
            int rejectedRequests,
            long algorithmExecutionTimeMs,
            int totalPriorityScore,
            long exploredStates,
            boolean stoppedByLimit,
            String algorithmStatus,
            double objectiveValue
    ) {
    }
}
