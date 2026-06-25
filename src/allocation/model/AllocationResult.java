package allocation.model;

import java.util.List;

public class AllocationResult {

    private List<Allocation> allocations;
    private List<RejectedRequest> rejectedRequests;
    private AllocationStatistics statistics;

    public AllocationResult(
            List<Allocation> allocations,
            List<RejectedRequest> rejectedRequests,
            AllocationStatistics statistics
    ) {
        this.allocations = allocations;
        this.rejectedRequests = rejectedRequests;
        this.statistics = statistics;
    }

    public List<Allocation> getAllocations() {
        return allocations;
    }

    public List<RejectedRequest> getRejectedRequests() {
        return rejectedRequests;
    }

    public AllocationStatistics getStatistics() {
        return statistics;
    }
}