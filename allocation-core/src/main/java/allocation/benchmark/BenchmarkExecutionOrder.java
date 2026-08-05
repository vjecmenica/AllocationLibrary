package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides deterministic balanced algorithm orders while preserving one canonical output order.
 */
public final class BenchmarkExecutionOrder {

    public static final List<AllocationAlgorithmType> CANONICAL_ORDER = List.of(
            AllocationAlgorithmType.GREEDY,
            AllocationAlgorithmType.BACKTRACKING,
            AllocationAlgorithmType.CP_SAT
    );

    private BenchmarkExecutionOrder() {
    }

    /**
     * Returns a cyclic rotation for a one-based warmup or measured iteration.
     */
    public static List<AllocationAlgorithmType> forIteration(int iteration) {
        if (iteration <= 0) {
            throw new IllegalArgumentException("Benchmark iteration must be positive.");
        }

        int offset = (iteration - 1) % CANONICAL_ORDER.size();
        List<AllocationAlgorithmType> order = new ArrayList<>(CANONICAL_ORDER.size());

        for (int index = 0; index < CANONICAL_ORDER.size(); index++) {
            order.add(CANONICAL_ORDER.get((index + offset) % CANONICAL_ORDER.size()));
        }

        return List.copyOf(order);
    }

    /**
     * Balances the initial warmup order across deterministic profile and seed combinations.
     */
    public static List<AllocationAlgorithmType> forWarmup(
            int warmupIteration,
            BenchmarkProfile profile,
            long seed
    ) {
        if (profile == null) {
            throw new IllegalArgumentException("Benchmark profile must not be null.");
        }

        if (warmupIteration <= 0) {
            throw new IllegalArgumentException("Warmup iteration must be positive.");
        }

        int offset = Math.floorMod(profile.ordinal() + Long.hashCode(seed), CANONICAL_ORDER.size());
        return forIteration(warmupIteration + offset);
    }
}
