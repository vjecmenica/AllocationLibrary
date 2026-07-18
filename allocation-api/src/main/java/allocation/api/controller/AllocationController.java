package allocation.api.controller;

import allocation.api.dto.AllocationApiRequest;
import allocation.api.dto.AllocationApiResponse;
import allocation.api.dto.AllocationComparisonApiRequest;
import allocation.api.dto.AllocationComparisonApiResponse;
import allocation.api.service.AllocationApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for stateless allocation execution and comparison.
 */
@RestController
@RequestMapping("/api/allocations")
public class AllocationController {

    private final AllocationApplicationService allocationApplicationService;

    public AllocationController(AllocationApplicationService allocationApplicationService) {
        this.allocationApplicationService = allocationApplicationService;
    }

    @PostMapping
    public AllocationApiResponse allocate(@RequestBody AllocationApiRequest request) {
        return allocationApplicationService.execute(request);
    }

    @PostMapping("/compare")
    public AllocationComparisonApiResponse compare(@RequestBody AllocationComparisonApiRequest request) {
        return allocationApplicationService.compare(request);
    }
}
