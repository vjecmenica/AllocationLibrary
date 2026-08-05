package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BenchmarkExecutionOrderTest {

    @Test
    void firstThreeIterationsUseBalancedRotations() {
        assertEquals(
                List.of(
                        AllocationAlgorithmType.GREEDY,
                        AllocationAlgorithmType.BACKTRACKING,
                        AllocationAlgorithmType.CP_SAT
                ),
                BenchmarkExecutionOrder.forIteration(1)
        );
        assertEquals(
                List.of(
                        AllocationAlgorithmType.BACKTRACKING,
                        AllocationAlgorithmType.CP_SAT,
                        AllocationAlgorithmType.GREEDY
                ),
                BenchmarkExecutionOrder.forIteration(2)
        );
        assertEquals(
                List.of(
                        AllocationAlgorithmType.CP_SAT,
                        AllocationAlgorithmType.GREEDY,
                        AllocationAlgorithmType.BACKTRACKING
                ),
                BenchmarkExecutionOrder.forIteration(3)
        );
    }

    @Test
    void rotationRepeatsDeterministically() {
        assertEquals(
                BenchmarkExecutionOrder.forIteration(1),
                BenchmarkExecutionOrder.forIteration(4)
        );
        assertEquals(
                BenchmarkExecutionOrder.forIteration(2),
                BenchmarkExecutionOrder.forIteration(5)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkExecutionOrder.forIteration(0)
        );
    }

    @Test
    void firstWarmupOrderIsBalancedAcrossSeeds() {
        assertEquals(
                BenchmarkExecutionOrder.forIteration(1),
                BenchmarkExecutionOrder.forWarmup(1, BenchmarkProfile.GREEDY_TRAP, 42)
        );
        assertEquals(
                BenchmarkExecutionOrder.forIteration(2),
                BenchmarkExecutionOrder.forWarmup(1, BenchmarkProfile.GREEDY_TRAP, 43)
        );
        assertEquals(
                BenchmarkExecutionOrder.forIteration(3),
                BenchmarkExecutionOrder.forWarmup(1, BenchmarkProfile.GREEDY_TRAP, 44)
        );
    }
}
