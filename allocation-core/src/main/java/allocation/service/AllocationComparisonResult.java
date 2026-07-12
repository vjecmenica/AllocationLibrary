package allocation.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Results of running every supported concrete allocation algorithm on the same
 * scenario.
 */
public class AllocationComparisonResult {

    private final Map<AllocationAlgorithmType, AlgorithmComparisonEntry> entries;
    private final int bestTotalPriorityScore;
    private final List<AllocationAlgorithmType> bestScoreAlgorithms;
    private final AllocationAlgorithmType fastestAlgorithm;

    /**
     * Creates a comparison result from entries for GREEDY, BACKTRACKING and CP_SAT.
     */
    public AllocationComparisonResult(
            Map<AllocationAlgorithmType, AlgorithmComparisonEntry> entries
    ) {
        if (entries == null) {
            throw new IllegalArgumentException("Comparison entry map must not be null.");
        }

        EnumMap<AllocationAlgorithmType, AlgorithmComparisonEntry> copy =
                new EnumMap<>(AllocationAlgorithmType.class);

        for (AllocationAlgorithmType algorithm : AllocationAlgorithmType.values()) {
            AlgorithmComparisonEntry entry = entries.get(algorithm);

            if (entry == null) {
                throw new IllegalArgumentException("Comparison entry is missing for algorithm: " + algorithm);
            }

            if (entry.getAlgorithm() != algorithm) {
                throw new IllegalArgumentException("Comparison entry algorithm does not match map key: " + algorithm);
            }

            copy.put(algorithm, entry);
        }

        if (entries.size() != AllocationAlgorithmType.values().length) {
            throw new IllegalArgumentException("Comparison entry map must contain only concrete allocation algorithms.");
        }

        this.entries = Collections.unmodifiableMap(copy);
        this.bestTotalPriorityScore = calculateBestTotalPriorityScore(copy);
        this.bestScoreAlgorithms = List.copyOf(findBestScoreAlgorithms(copy, bestTotalPriorityScore));
        this.fastestAlgorithm = findFastestAlgorithm(copy);
    }

    /**
     * Returns all comparison entries keyed by concrete algorithm.
     */
    public Map<AllocationAlgorithmType, AlgorithmComparisonEntry> getEntries() {
        return entries;
    }

    /**
     * Returns the comparison entry for one concrete algorithm.
     */
    public AlgorithmComparisonEntry getEntry(AllocationAlgorithmType algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("Algorithm must not be null.");
        }

        return entries.get(algorithm);
    }

    /**
     * Returns the best total priority score found by any compared algorithm.
     */
    public int getBestTotalPriorityScore() {
        return bestTotalPriorityScore;
    }

    /**
     * Returns every algorithm that reached the best total priority score.
     */
    public List<AllocationAlgorithmType> getBestScoreAlgorithms() {
        return bestScoreAlgorithms;
    }

    /**
     * Returns the algorithm with the lowest measured wall-clock execution time.
     */
    public AllocationAlgorithmType getFastestAlgorithm() {
        return fastestAlgorithm;
    }

    private int calculateBestTotalPriorityScore(
            Map<AllocationAlgorithmType, AlgorithmComparisonEntry> entries
    ) {
        int bestScore = Integer.MIN_VALUE;

        for (AlgorithmComparisonEntry entry : entries.values()) {
            int score = entry.getAllocationResult()
                    .getStatistics()
                    .getTotalPriorityScore();

            if (score > bestScore) {
                bestScore = score;
            }
        }

        return bestScore;
    }

    private List<AllocationAlgorithmType> findBestScoreAlgorithms(
            Map<AllocationAlgorithmType, AlgorithmComparisonEntry> entries,
            int bestScore
    ) {
        List<AllocationAlgorithmType> bestAlgorithms = new ArrayList<>();

        for (AllocationAlgorithmType algorithm : AllocationAlgorithmType.values()) {
            int score = entries.get(algorithm)
                    .getAllocationResult()
                    .getStatistics()
                    .getTotalPriorityScore();

            if (score == bestScore) {
                bestAlgorithms.add(algorithm);
            }
        }

        return bestAlgorithms;
    }

    private AllocationAlgorithmType findFastestAlgorithm(
            Map<AllocationAlgorithmType, AlgorithmComparisonEntry> entries
    ) {
        AllocationAlgorithmType fastest = null;
        double fastestTimeMs = Double.MAX_VALUE;

        for (AllocationAlgorithmType algorithm : AllocationAlgorithmType.values()) {
            double executionTimeMs = entries.get(algorithm).getExecutionTimeMs();

            if (executionTimeMs < fastestTimeMs) {
                fastest = algorithm;
                fastestTimeMs = executionTimeMs;
            }
        }

        return fastest;
    }
}
