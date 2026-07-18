package allocation.api.dto;

import allocation.service.AllocationAlgorithmType;
import allocation.service.AllocationGoal;
import allocation.service.AllocationSelectionMode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Request body for executing one allocation algorithm through the REST API.
 */
public record AllocationApiRequest(
        AllocationSelectionMode selectionMode,
        AllocationAlgorithmType algorithm,
        AllocationGoal goal,
        Long backtrackingTimeLimitMs,
        Double cpSatTimeLimitSeconds,
        List<ResourceDto> resources,
        List<AllocationRequestDto> requests
) {

    /**
     * Resource payload matching the core Resource model.
     */
    public record ResourceDto(
            String id,
            String name,
            String type,
            Map<String, Integer> capacities,
            List<TimeWindowDto> availability
    ) {
    }

    /**
     * Time window payload using ISO-8601 LocalDateTime values.
     */
    public record TimeWindowDto(
            LocalDateTime start,
            LocalDateTime end
    ) {
    }

    /**
     * Allocation request payload matching the core AllocationRequest model.
     */
    public record AllocationRequestDto(
            String id,
            String name,
            LocalDateTime startTime,
            Integer durationMinutes,
            Integer priority,
            List<ResourceRequirementDto> resourceRequirements
    ) {
    }

    /**
     * Resource requirement payload matching the core ResourceRequirement model.
     */
    public record ResourceRequirementDto(
            String resourceType,
            Integer quantity,
            Map<String, Integer> requiredCapacities
    ) {
    }
}
