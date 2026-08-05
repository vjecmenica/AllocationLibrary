package allocation.benchmark;

import allocation.service.AllocationAlgorithmType;

import java.time.Instant;

final class BenchmarkTestData {

    static final String FINGERPRINT = "0".repeat(64);

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
                FINGERPRINT,
                repetition,
                algorithm,
                algorithm.ordinal() + 1,
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

    static BenchmarkResult resultWithIdentity(
            String benchmarkRunId,
            String scenarioFingerprint,
            int resourceCount,
            int requestCount,
            BenchmarkProfile profile,
            long seed,
            int repetition,
            AllocationAlgorithmType algorithm
    ) {
        return new BenchmarkResult(
                benchmarkRunId,
                Instant.parse("2026-09-01T08:00:00Z"),
                profile,
                seed,
                scenarioFingerprint,
                repetition,
                algorithm,
                algorithm.ordinal() + 1,
                resourceCount,
                requestCount,
                500,
                1.0,
                10,
                1,
                requestCount - 1,
                1.0,
                1,
                1,
                false,
                algorithm == AllocationAlgorithmType.CP_SAT ? "OPTIMAL" : null,
                0
        );
    }
}
