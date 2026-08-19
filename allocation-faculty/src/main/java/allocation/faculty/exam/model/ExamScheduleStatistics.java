package allocation.faculty.exam.model;

/**
 * Summary and solver diagnostics for one faculty scheduling run.
 */
public final class ExamScheduleStatistics {

    private final int totalExams;
    private final int scheduledExams;
    private final int unscheduledExams;
    private final String solverStatus;
    private final long executionTimeMs;
    private final boolean stoppedByLimit;

    public ExamScheduleStatistics(
            int totalExams,
            int scheduledExams,
            int unscheduledExams,
            String solverStatus,
            long executionTimeMs,
            boolean stoppedByLimit
    ) {
        if (totalExams < 0 || scheduledExams < 0 || unscheduledExams < 0) {
            throw new IllegalArgumentException("Exam statistics counts must not be negative.");
        }
        if (scheduledExams + unscheduledExams != totalExams) {
            throw new IllegalArgumentException("Exam statistics counts must be consistent.");
        }
        if (solverStatus == null || solverStatus.isBlank()) {
            throw new IllegalArgumentException("Solver status must not be blank.");
        }
        if (executionTimeMs < 0) {
            throw new IllegalArgumentException("Execution time must not be negative.");
        }

        this.totalExams = totalExams;
        this.scheduledExams = scheduledExams;
        this.unscheduledExams = unscheduledExams;
        this.solverStatus = solverStatus;
        this.executionTimeMs = executionTimeMs;
        this.stoppedByLimit = stoppedByLimit;
    }

    public int getTotalExams() {
        return totalExams;
    }

    public int getScheduledExams() {
        return scheduledExams;
    }

    public int getUnscheduledExams() {
        return unscheduledExams;
    }

    public String getSolverStatus() {
        return solverStatus;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public boolean isStoppedByLimit() {
        return stoppedByLimit;
    }
}
