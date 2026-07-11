package allocation.model;

public class AllocationStatistics {

    private int totalRequests;
    private int allocatedRequests;
    private int rejectedRequests;
    private long executionTimeMs;
    private int totalPriorityScore;

    private long exploredStates;
    private boolean stoppedByLimit;
    private String algorithmStatus;
    private double objectiveValue;

    public AllocationStatistics(
            int totalRequests,
            int allocatedRequests,
            int rejectedRequests,
            long executionTimeMs,
            int totalPriorityScore
    ) {
        this(
                totalRequests,
                allocatedRequests,
                rejectedRequests,
                executionTimeMs,
                totalPriorityScore,
                0,
                false,
                null,
                0
        );
    }

    public AllocationStatistics(
            int totalRequests,
            int allocatedRequests,
            int rejectedRequests,
            long executionTimeMs,
            int totalPriorityScore,
            long exploredStates,
            boolean stoppedByLimit
    ) {
        this(
                totalRequests,
                allocatedRequests,
                rejectedRequests,
                executionTimeMs,
                totalPriorityScore,
                exploredStates,
                stoppedByLimit,
                null,
                0
        );
    }

    public AllocationStatistics(
            int totalRequests,
            int allocatedRequests,
            int rejectedRequests,
            long executionTimeMs,
            int totalPriorityScore,
            long exploredStates,
            boolean stoppedByLimit,
            String algorithmStatus,
            double objectiveValue
    ) {
        this.totalRequests = totalRequests;
        this.allocatedRequests = allocatedRequests;
        this.rejectedRequests = rejectedRequests;
        this.executionTimeMs = executionTimeMs;
        this.totalPriorityScore = totalPriorityScore;
        this.exploredStates = exploredStates;
        this.stoppedByLimit = stoppedByLimit;
        this.algorithmStatus = algorithmStatus;
        this.objectiveValue = objectiveValue;
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

    public long getExploredStates() {
        return exploredStates;
    }

    public boolean isStoppedByLimit() {
        return stoppedByLimit;
    }

    public String getAlgorithmStatus() {
        return algorithmStatus;
    }

    public double getObjectiveValue() {
        return objectiveValue;
    }
}
