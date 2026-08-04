package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;

import java.time.Instant;

final class BenchmarkTestData {

    private BenchmarkTestData() {
    }

    static BenchmarkResult result(
            BenchmarkProfile profile,
            long seed,
            int repetition,
            AllocationAlgorithmType algorithm,
            double measuredTime,
            int score,
            int allocated,
            boolean stoppedByLimit,
            String status
    ) {
        return new BenchmarkResult(
                "run-1",
                Instant.parse("2026-09-01T08:00:00Z"),
                profile,
                seed,
                repetition,
                algorithm,
                8,
                8,
                500,
                1.0,
                score,
                allocated,
                8 - allocated,
                measuredTime,
                (long) measuredTime,
                12,
                stoppedByLimit,
                status,
                algorithm == AllocationAlgorithmType.CP_SAT ? score * 3.0 : 0.0
        );
    }
}
