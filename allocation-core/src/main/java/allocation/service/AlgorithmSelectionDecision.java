package allocation.service;

/**
 * Result of automatic algorithm selection before an algorithm is executed.
 */
public class AlgorithmSelectionDecision {

    private final AllocationAlgorithmType selectedAlgorithm;
    private final String reason;

    /**
     * Creates a selection decision for one concrete allocation algorithm.
     */
    public AlgorithmSelectionDecision(
            AllocationAlgorithmType selectedAlgorithm,
            String reason
    ) {
        if (selectedAlgorithm == null) {
            throw new IllegalArgumentException("Selected algorithm must not be null.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Selection reason must not be blank.");
        }

        this.selectedAlgorithm = selectedAlgorithm;
        this.reason = reason;
    }

    /**
     * Returns the concrete algorithm selected for execution.
     */
    public AllocationAlgorithmType getSelectedAlgorithm() {
        return selectedAlgorithm;
    }

    /**
     * Returns a human-readable explanation for the selected algorithm.
     */
    public String getReason() {
        return reason;
    }
}
