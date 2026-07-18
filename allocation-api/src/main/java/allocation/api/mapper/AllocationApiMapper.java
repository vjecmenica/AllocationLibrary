package allocation.api.mapper;

import allocation.api.dto.AllocationApiRequest;
import allocation.api.dto.AllocationApiResponse;
import allocation.api.dto.AllocationComparisonApiResponse;
import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.AllocationStatistics;
import allocation.model.RejectedRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;
import allocation.service.AlgorithmComparisonEntry;
import allocation.service.AllocationAlgorithmType;
import allocation.service.AllocationComparisonResult;
import allocation.service.AllocationExecutionResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Maps REST DTOs to core allocation models and core results back to REST DTOs.
 */
@Component
public class AllocationApiMapper {

    public List<Resource> toResources(List<AllocationApiRequest.ResourceDto> resources) {
        if (resources == null) {
            throw new IllegalArgumentException("Resource list must not be null.");
        }

        return resources.stream()
                .map(this::toResource)
                .toList();
    }

    public List<AllocationRequest> toRequests(List<AllocationApiRequest.AllocationRequestDto> requests) {
        if (requests == null) {
            throw new IllegalArgumentException("Request list must not be null.");
        }

        return requests.stream()
                .map(this::toAllocationRequest)
                .toList();
    }

    public AllocationApiResponse toResponse(AllocationExecutionResult result) {
        AllocationApiResponse.AllocationResultDto allocationResult =
                toAllocationResultDto(result.getAllocationResult());

        return new AllocationApiResponse(
                result.getSelectionMode(),
                result.getRequestedAlgorithm(),
                result.getExecutedAlgorithm(),
                result.getGoal(),
                result.getSelectionReason(),
                result.getExecutionTimeMs(),
                allocationResult.allocations(),
                allocationResult.rejectedRequests(),
                allocationResult.statistics()
        );
    }

    public AllocationComparisonApiResponse toComparisonResponse(AllocationComparisonResult result) {
        Map<AllocationAlgorithmType, AllocationComparisonApiResponse.ComparisonEntryDto> entries =
                new EnumMap<>(AllocationAlgorithmType.class);

        for (Map.Entry<AllocationAlgorithmType, AlgorithmComparisonEntry> entry : result.getEntries().entrySet()) {
            AlgorithmComparisonEntry comparisonEntry = entry.getValue();

            entries.put(
                    entry.getKey(),
                    new AllocationComparisonApiResponse.ComparisonEntryDto(
                            comparisonEntry.getAlgorithm(),
                            comparisonEntry.getExecutionTimeMs(),
                            toAllocationResultDto(comparisonEntry.getAllocationResult())
                    )
            );
        }

        return new AllocationComparisonApiResponse(
                entries,
                result.getBestTotalPriorityScore(),
                result.getBestScoreAlgorithms(),
                result.getFastestAlgorithm()
        );
    }

    private Resource toResource(AllocationApiRequest.ResourceDto resource) {
        if (resource == null) {
            throw new IllegalArgumentException("Resource list must not contain null elements.");
        }

        return new Resource(
                resource.id(),
                resource.name(),
                resource.type(),
                resource.capacities(),
                toTimeWindows(resource.availability())
        );
    }

    private List<TimeWindow> toTimeWindows(List<AllocationApiRequest.TimeWindowDto> availability) {
        if (availability == null) {
            return null;
        }

        return availability.stream()
                .map(this::toTimeWindow)
                .toList();
    }

    private TimeWindow toTimeWindow(AllocationApiRequest.TimeWindowDto timeWindow) {
        if (timeWindow == null) {
            throw new IllegalArgumentException("Availability list must not contain null elements.");
        }

        return new TimeWindow(
                timeWindow.start(),
                timeWindow.end()
        );
    }

    private AllocationRequest toAllocationRequest(AllocationApiRequest.AllocationRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Request list must not contain null elements.");
        }

        if (request.durationMinutes() == null) {
            throw new IllegalArgumentException("Duration must not be null.");
        }

        if (request.priority() == null) {
            throw new IllegalArgumentException("Priority must not be null.");
        }

        return new AllocationRequest(
                request.id(),
                request.name(),
                request.startTime(),
                request.durationMinutes(),
                request.priority(),
                toResourceRequirements(request.resourceRequirements())
        );
    }

    private List<ResourceRequirement> toResourceRequirements(
            List<AllocationApiRequest.ResourceRequirementDto> requirements
    ) {
        if (requirements == null) {
            return null;
        }

        return requirements.stream()
                .map(this::toResourceRequirement)
                .toList();
    }

    private ResourceRequirement toResourceRequirement(AllocationApiRequest.ResourceRequirementDto requirement) {
        if (requirement == null) {
            throw new IllegalArgumentException("Resource requirement list must not contain null elements.");
        }

        if (requirement.quantity() == null) {
            throw new IllegalArgumentException("Resource quantity must not be null.");
        }

        return new ResourceRequirement(
                requirement.resourceType(),
                requirement.quantity(),
                requirement.requiredCapacities()
        );
    }

    private AllocationApiResponse.AllocationResultDto toAllocationResultDto(AllocationResult result) {
        return new AllocationApiResponse.AllocationResultDto(
                result.getAllocations().stream()
                        .map(this::toAllocationDto)
                        .toList(),
                result.getRejectedRequests().stream()
                        .map(this::toRejectedRequestDto)
                        .toList(),
                toStatisticsDto(result.getStatistics())
        );
    }

    private AllocationApiResponse.AllocationDto toAllocationDto(Allocation allocation) {
        return new AllocationApiResponse.AllocationDto(
                toAllocationRequestDto(allocation.getRequest()),
                allocation.getAssignedResources().stream()
                        .map(this::toResourceDto)
                        .toList()
        );
    }

    private AllocationApiResponse.RejectedRequestDto toRejectedRequestDto(RejectedRequest rejectedRequest) {
        return new AllocationApiResponse.RejectedRequestDto(
                toAllocationRequestDto(rejectedRequest.getRequest()),
                rejectedRequest.getReason()
        );
    }

    private AllocationApiRequest.ResourceDto toResourceDto(Resource resource) {
        return new AllocationApiRequest.ResourceDto(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                resource.getCapacities(),
                toTimeWindowDtos(resource.getAvailability())
        );
    }

    private List<AllocationApiRequest.TimeWindowDto> toTimeWindowDtos(List<TimeWindow> availability) {
        if (availability == null) {
            return null;
        }

        return availability.stream()
                .map(this::toTimeWindowDto)
                .toList();
    }

    private AllocationApiRequest.TimeWindowDto toTimeWindowDto(TimeWindow timeWindow) {
        return new AllocationApiRequest.TimeWindowDto(
                timeWindow.getStart(),
                timeWindow.getEnd()
        );
    }

    private AllocationApiRequest.AllocationRequestDto toAllocationRequestDto(AllocationRequest request) {
        return new AllocationApiRequest.AllocationRequestDto(
                request.getId(),
                request.getName(),
                request.getTimeWindow().getStart(),
                durationMinutes(request.getTimeWindow()),
                request.getPriority(),
                request.getResourceRequirements().stream()
                        .map(this::toResourceRequirementDto)
                        .toList()
        );
    }

    private int durationMinutes(TimeWindow timeWindow) {
        return Math.toIntExact(Duration.between(timeWindow.getStart(), timeWindow.getEnd()).toMinutes());
    }

    private AllocationApiRequest.ResourceRequirementDto toResourceRequirementDto(
            ResourceRequirement requirement
    ) {
        return new AllocationApiRequest.ResourceRequirementDto(
                requirement.getResourceType(),
                requirement.getQuantity(),
                requirement.getRequiredCapacities()
        );
    }

    private AllocationApiResponse.StatisticsDto toStatisticsDto(AllocationStatistics statistics) {
        return new AllocationApiResponse.StatisticsDto(
                statistics.getTotalRequests(),
                statistics.getAllocatedRequests(),
                statistics.getRejectedRequests(),
                statistics.getExecutionTimeMs(),
                statistics.getTotalPriorityScore(),
                statistics.getExploredStates(),
                statistics.isStoppedByLimit(),
                statistics.getAlgorithmStatus(),
                statistics.getObjectiveValue()
        );
    }
}
