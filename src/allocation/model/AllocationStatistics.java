package allocation.model;

public class AllocationStatistics {

    private int totalRequests;
    private int allocatedRequests;
    private int rejectedRequests;
    private long executionTimeMs;
    private int totalPriorityScore;

    public AllocationStatistics(
            int totalRequests,
            int allocatedRequests,
            int rejectedRequests,
            long executionTimeMs,
            int totalPriorityScore
    ) {
        this.totalRequests = totalRequests;
        this.allocatedRequests = allocatedRequests;
        this.rejectedRequests = rejectedRequests;
        this.executionTimeMs = executionTimeMs;
        this.totalPriorityScore = totalPriorityScore;
    }

    public int getTotalRequests() {
        return totalRequests;
    }

    public int getAllocatedRequests() {
        return allocatedRequests;
    }

    public int getRejectedRequests() {
        return rejectedRequests;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public int getTotalPriorityScore() {
        return totalPriorityScore;
    }
}