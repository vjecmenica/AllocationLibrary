package allocation.api.dto;

import java.util.List;

/**
 * Request body for comparing all supported allocation algorithms.
 */
public record AllocationComparisonApiRequest(
        Long backtrackingTimeLimitMs,
        Double cpSatTimeLimitSeconds,
        List<AllocationApiRequest.ResourceDto> resources,
        List<AllocationApiRequest.AllocationRequestDto> requests
) {
}
