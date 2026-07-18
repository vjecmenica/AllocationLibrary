package allocation.api.service;

import allocation.api.dto.AllocationApiRequest;
import allocation.api.dto.AllocationApiResponse;
import allocation.api.dto.AllocationComparisonApiRequest;
import allocation.api.dto.AllocationComparisonApiResponse;
import allocation.api.mapper.AllocationApiMapper;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.service.AllocationComparisonResult;
import allocation.service.AllocationExecutionResult;
import allocation.service.AllocationGoal;
import allocation.service.AllocationOptions;
import allocation.service.AllocationSelectionMode;
import allocation.service.AutomaticAllocationOptions;
import allocation.service.ResourceAllocator;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Small application service that connects REST DTOs with the core ResourceAllocator.
 */
@Service
public class AllocationApplicationService {

    private final AllocationApiMapper mapper;
    private final ResourceAllocator resourceAllocator;

    public AllocationApplicationService(AllocationApiMapper mapper) {
        this.mapper = mapper;
        this.resourceAllocator = new ResourceAllocator();
    }

    public AllocationApiResponse execute(AllocationApiRequest request) {
        validateAllocationRequest(request);

        List<Resource> resources = mapper.toResources(request.resources());
        List<AllocationRequest> requests = mapper.toRequests(request.requests());

        AllocationExecutionResult result;

        if (request.selectionMode() == AllocationSelectionMode.EXPLICIT) {
            result = resourceAllocator.execute(
                    resources,
                    requests,
                    explicitOptions(request)
            );
        } else {
            result = resourceAllocator.executeAutomatically(
                    resources,
                    requests,
                    automaticOptions(request)
            );
        }

        return mapper.toResponse(result);
    }

    public AllocationComparisonApiResponse compare(AllocationComparisonApiRequest request) {
        validateComparisonRequest(request);

        List<Resource> resources = mapper.toResources(request.resources());
        List<AllocationRequest> requests = mapper.toRequests(request.requests());
        AllocationComparisonResult result = resourceAllocator.compare(
                resources,
                requests,
                backtrackingTimeLimitOrDefault(request.backtrackingTimeLimitMs()),
                cpSatTimeLimitOrDefault(request.cpSatTimeLimitSeconds())
        );

        return mapper.toComparisonResponse(result);
    }

    private void validateAllocationRequest(AllocationApiRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null.");
        }

        if (request.selectionMode() == null) {
            throw new IllegalArgumentException("Selection mode must not be null.");
        }

        if (request.resources() == null) {
            throw new IllegalArgumentException("Resource list must not be null.");
        }

        if (request.requests() == null) {
            throw new IllegalArgumentException("Request list must not be null.");
        }

        validateTimeLimits(
                request.backtrackingTimeLimitMs(),
                request.cpSatTimeLimitSeconds()
        );

        if (request.selectionMode() == AllocationSelectionMode.EXPLICIT) {
            validateExplicitRequest(request);
        } else if (request.selectionMode() == AllocationSelectionMode.AUTO) {
            validateAutomaticRequest(request);
        }
    }

    private void validateComparisonRequest(AllocationComparisonApiRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body must not be null.");
        }

        if (request.resources() == null) {
            throw new IllegalArgumentException("Resource list must not be null.");
        }

        if (request.requests() == null) {
            throw new IllegalArgumentException("Request list must not be null.");
        }

        validateTimeLimits(
                request.backtrackingTimeLimitMs(),
                request.cpSatTimeLimitSeconds()
        );
    }

    private void validateExplicitRequest(AllocationApiRequest request) {
        if (request.algorithm() == null) {
            throw new IllegalArgumentException("Algorithm must not be null for explicit selection.");
        }

        if (request.goal() != null) {
            throw new IllegalArgumentException("Goal must be omitted for explicit selection.");
        }
    }

    private void validateAutomaticRequest(AllocationApiRequest request) {
        if (request.algorithm() != null) {
            throw new IllegalArgumentException("Algorithm must be omitted for automatic selection.");
        }
    }

    private void validateTimeLimits(
            Long backtrackingTimeLimitMs,
            Double cpSatTimeLimitSeconds
    ) {
        if (backtrackingTimeLimitMs != null && backtrackingTimeLimitMs <= 0) {
            throw new IllegalArgumentException("Backtracking time limit must be positive.");
        }

        if (cpSatTimeLimitSeconds != null && cpSatTimeLimitSeconds <= 0) {
            throw new IllegalArgumentException("CP-SAT time limit must be positive.");
        }
    }

    private AllocationOptions explicitOptions(AllocationApiRequest request) {
        return new AllocationOptions(
                request.algorithm(),
                backtrackingTimeLimitOrDefault(request.backtrackingTimeLimitMs()),
                cpSatTimeLimitOrDefault(request.cpSatTimeLimitSeconds())
        );
    }

    private AutomaticAllocationOptions automaticOptions(AllocationApiRequest request) {
        return AutomaticAllocationOptions.of(
                goalOrDefault(request.goal()),
                backtrackingTimeLimitOrDefault(request.backtrackingTimeLimitMs()),
                cpSatTimeLimitOrDefault(request.cpSatTimeLimitSeconds())
        );
    }

    private AllocationGoal goalOrDefault(AllocationGoal goal) {
        if (goal == null) {
            return AllocationGoal.BALANCED;
        }

        return goal;
    }

    private long backtrackingTimeLimitOrDefault(Long backtrackingTimeLimitMs) {
        if (backtrackingTimeLimitMs == null) {
            return AllocationOptions.DEFAULT_BACKTRACKING_TIME_LIMIT_MS;
        }

        return backtrackingTimeLimitMs;
    }

    private double cpSatTimeLimitOrDefault(Double cpSatTimeLimitSeconds) {
        if (cpSatTimeLimitSeconds == null) {
            return AllocationOptions.DEFAULT_CP_SAT_TIME_LIMIT_SECONDS;
        }

        return cpSatTimeLimitSeconds;
    }
}
