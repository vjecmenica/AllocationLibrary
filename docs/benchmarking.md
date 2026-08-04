# Reproducible Benchmarking

The allocation benchmark is a standalone command-line tool in `allocation-core`. It evaluates GREEDY,
BACKTRACKING, and CP_SAT through the public `ResourceAllocator` API; it does not start Spring Boot or the Angular
application.

## Profiles

- `GREEDY_TRAP`: two overlapping requests and two rooms where Greedy obtains score 10 while Backtracking and
  CP-SAT can obtain score 19.
- `BALANCED_SMALL`: 8 resources and 8 requests for quick smoke tests and local experiments.
- `BALANCED_MEDIUM`: 30 resources and 30 requests with ROOM, STAFF, and PROJECTOR requirements.
- `CONFLICT_HEAVY`: 12 resources, 24 requests, few time slots, and frequent additional resource requirements.
- `CAPACITY_HEAVY`: ROOM resources with deliberately limited capacities and requests with a wide capacity range.
- `SCALE`: configurable resource count, request count, and resource type count.

Every profile uses a fixed scenario start and `java.util.Random` with an explicit seed. The same profile, seed,
and SCALE configuration produce the same resources, requests, priorities, capacities, and time windows. Runtime
measurements can still vary because they depend on the JVM, operating system, processor, and current machine load.

## Runs And Limits

Warmup runs exercise every algorithm before measurement and are never written to result files. Measured runs are
written individually. Each algorithm receives an independent copy of the same logical scenario for each run.

Default configuration:

| Setting | Default |
| --- | ---: |
| Profile | `BALANCED_SMALL` |
| Seed | `42` |
| Warmup runs | `1` |
| Measured runs | `3` |
| Backtracking time limit | `5000` ms |
| CP-SAT time limit | `5.0` seconds |
| Output directory | `benchmark-results` |
| SCALE resources | `20` |
| SCALE requests | `20` |
| SCALE resource types | `3` |

The time limits bound individual Backtracking and CP-SAT executions. A result stopped by a limit remains useful as
the best solution found by that execution, but it must not be interpreted as a proven optimum.

## Running The Benchmark

From the repository root, package the required module and run the CLI:

```bash
mvn -pl allocation-core -am package
mvn -pl allocation-core exec:java -Dexec.args="--profile BALANCED_SMALL --seed 42 --runs 5"
```

A short Greedy Trap verification run is:

```bash
mvn -pl allocation-core exec:java -Dexec.args="--profile GREEDY_TRAP --seed 42 --warmups 0 --runs 1 --backtracking-limit-ms 500 --cp-sat-limit-seconds 1 --output benchmark-results"
```

Supported arguments:

| Argument | Meaning |
| --- | --- |
| `--profile` | One profile or a comma-separated profile list |
| `--seed` | One seed or a comma-separated seed list |
| `--warmups` | Warmup runs per algorithm |
| `--runs` | Measured runs per algorithm |
| `--backtracking-limit-ms` | Backtracking limit in milliseconds |
| `--cp-sat-limit-seconds` | CP-SAT limit in seconds |
| `--output` | Output directory |
| `--resources` | Resource count for `SCALE` |
| `--requests` | Request count for `SCALE` |
| `--resource-types` | Resource type count for `SCALE` |
| `--help` | CLI usage |

Unknown arguments, missing values, and invalid configuration values produce a non-zero exit status and print the
usage text.

## Metrics

- `totalPriorityScore`: sum of priorities of accepted requests.
- `allocatedRequests` and `rejectedRequests`: accepted and rejected request counts.
- `measuredExecutionTimeMs`: high-resolution wall-clock duration measured around the public allocator call with
  `System.nanoTime()`.
- `algorithmExecutionTimeMs`: duration reported by the algorithm's existing `AllocationStatistics`, stored in
  whole milliseconds.
- `exploredStates`: algorithm-specific search-state count; Greedy and CP-SAT currently report zero, while
  Backtracking reports its explored states.
- `stoppedByLimit`: whether the algorithm did not complete an unrestricted proof/search before its configured
  limit.
- `algorithmStatus`: solver status where available. CP-SAT commonly reports `OPTIMAL`, `FEASIBLE`, or `UNKNOWN`;
  it is empty for algorithms without solver status.
- `objectiveValue`: CP-SAT objective value when available. It includes the model's score and allocation-count
  tie-break component and is not the same as `totalPriorityScore`.

Do not compare measured times from different machines as though they came from one controlled experiment. A single
run is also insufficient for a performance conclusion; use warmups, several measured repetitions, and the summary
distribution.

## Output Files

The output directory is created automatically and contains three UTF-8 files. Local `benchmark-results/` output is
ignored by Git.

### `raw-results.csv`

One row represents one algorithm in one measured repetition. Rows are stable by configured profile order, seed
order, repetition, then GREEDY, BACKTRACKING, and CP_SAT.

Columns:

```text
schemaVersion,benchmarkRunId,generatedAt,profile,seed,repetition,algorithm,resourceCount,requestCount,backtrackingTimeLimitMs,cpSatTimeLimitSeconds,totalPriorityScore,allocatedRequests,rejectedRequests,measuredExecutionTimeMs,algorithmExecutionTimeMs,exploredStates,stoppedByLimit,algorithmStatus,objectiveValue
```

### `summary-results.csv`

One row represents one `profile + seed + algorithm` combination. Times and averages retain six decimal places in
CSV output; aggregation uses unrounded values.

Columns:

```text
schemaVersion,benchmarkRunId,generatedAt,profile,seed,algorithm,measuredRuns,averageMeasuredExecutionTimeMs,medianMeasuredExecutionTimeMs,minimumMeasuredExecutionTimeMs,maximumMeasuredExecutionTimeMs,averageTotalPriorityScore,bestTotalPriorityScore,worstTotalPriorityScore,averageAllocatedRequests,stoppedByLimitRuns,optimalCpSatRuns
```

`optimalCpSatRuns` is counted only for CP-SAT rows. Other algorithms have no CP-SAT solver status and report zero.

### `metadata.json`

The versioned metadata document contains:

- benchmark run ID and UTC generation time;
- Java version;
- operating system name, version, and architecture;
- available processor count;
- complete benchmark configuration;
- selected profiles and seeds;
- stable algorithm list;
- paths to the raw, summary, and metadata files.

The three files share one `benchmarkRunId`, allowing one experimental invocation to be identified consistently.
