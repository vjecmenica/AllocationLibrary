package allocation.benchmark;

public class BenchmarkResult {

    private String scenarioName;
    private long seed;
    private int resourceCount;
    private int requestCount;
    private String algorithmName;
    private int allocatedRequests;
    private int rejectedRequests;
    private int totalPriorityScore;
    private long executionTimeMs;
    private long exploredStates;
    private boolean stoppedByLimit;
    private String algorithmStatus;
    private double objectiveValue;

    public BenchmarkResult(
            String scenarioName,
            long seed,
            int resourceCount,
            int requestCount,
            String algorithmName,
            int allocatedRequests,
            int rejectedRequests,
            int totalPriorityScore,
            long executionTimeMs,
            long exploredStates,
            boolean stoppedByLimit,
            String algorithmStatus,
            double objectiveValue
    ) {
        this.scenarioName = scenarioName;
        this.seed = seed;
        this.resourceCount = resourceCount;
        this.requestCount = requestCount;
        this.algorithmName = algorithmName;
        this.allocatedRequests = allocatedRequests;
        this.rejectedRequests = rejectedRequests;
        this.totalPriorityScore = totalPriorityScore;
        this.executionTimeMs = executionTimeMs;
        this.exploredStates = exploredStates;
        this.stoppedByLimit = stoppedByLimit;
        this.algorithmStatus = algorithmStatus;
        this.objectiveValue = objectiveValue;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public long getSeed() {
        return seed;
    }

    public int getResourceCount() {
        return resourceCount;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public int getAllocatedRequests() {
        return allocatedRequests;
    }

    public int getRejectedRequests() {
        return rejectedRequests;
    }

    public int getTotalPriorityScore() {
        return totalPriorityScore;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
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
