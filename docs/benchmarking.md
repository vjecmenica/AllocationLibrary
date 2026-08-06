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

Each generated scenario also has a lowercase 64-character SHA-256 `scenarioFingerprint`. The canonical fingerprint
includes resource and request order, all model fields, ordered time windows and requirements, and capacity maps with
sorted keys. Resource and request order intentionally remains significant because it can affect Greedy behavior.
Matching seeds are not sufficient evidence of an identical experiment unless the fingerprints and source commits
also match.

## Runs And Limits

Warmup runs exercise every algorithm before measurement and are never written to result files. Measured runs are
written individually. Each algorithm receives an independent copy of the same logical scenario for each run.

Algorithm execution order rotates deterministically to reduce fixed-order timing bias:

1. GREEDY, BACKTRACKING, CP_SAT
2. BACKTRACKING, CP_SAT, GREEDY
3. CP_SAT, GREEDY, BACKTRACKING

The cycle repeats for later iterations. Warmups use the same cycle with a deterministic initial offset derived from
the profile and seed, so scenarios with one warmup do not all favor the same first algorithm. Raw CSV rows remain
in the canonical GREEDY, BACKTRACKING, CP_SAT order. `executionOrderPosition` records where an algorithm actually
ran in that measured iteration.

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
| Overwrite existing output | `false` |

The time limits bound individual Backtracking and CP-SAT executions. A result stopped by a limit remains useful as
the best solution found by that execution, but it must not be interpreted as a proven optimum.

## Running The Benchmark

From the repository root, package the required module and run the CLI:

```bash
mvn -pl allocation-core -am package
mvn -pl allocation-core exec:java \
  -Dbenchmark.sourceCommit=<commit-sha> \
  -Dexec.args="--profile GREEDY_TRAP --seed 42 --warmups 1 --runs 3 --output benchmark-results/greedy-trap"
```

For complete experimental campaigns, use `scripts/run-benchmark-campaign.ps1` on Windows or
`scripts/run-benchmark-campaign.sh` on Unix-like systems. Both read the same versioned campaign plan and support
`smoke`, `standard`, and `extended` presets. The PowerShell implementation is the primary supported runner:

```powershell
./scripts/run-benchmark-campaign.ps1 -Preset smoke
./scripts/run-benchmark-campaign.ps1 -Preset standard -DryRun
```

The Bash runner uses Python 3 from the operating system only for JSON manifest processing and CSV validation; it
does not add a Java project dependency:

```bash
bash scripts/run-benchmark-campaign.sh --preset smoke
bash scripts/run-benchmark-campaign.sh --preset extended --dry-run
```

By default, campaign output is written below `benchmark-results/campaigns/<short-source-commit>`. Each experiment
has its own directory. Use a new output root for final runs. `-Overwrite` or `--overwrite` forwards the benchmark
CLI overwrite flag without deleting unrelated files.

Campaign options are:

| PowerShell | Bash | Meaning |
| --- | --- | --- |
| `-SourceCommit` | `--source-commit` | Commit to verify and record; defaults to HEAD |
| `-OutputRoot` | `--output-root` | Campaign root directory |
| `-Preset` | `--preset` | `smoke`, `standard`, or `extended` |
| `-Overwrite` | `--overwrite` | Allow replacement of known benchmark outputs |
| `-SkipTests` | `--skip-tests` | Skip the initial `mvn test` step |
| `-AllowDirtyWorkingTree` | `--allow-dirty-working-tree` | Permit an intentionally dirty local tree |
| `-DryRun` | `--dry-run` | Validate and print the plan without running Maven or writing results |

A successful campaign adds `campaign-manifest.json`, `campaign-raw-results.csv`, `campaign-summary.csv`, and
`campaign-request-outcomes.csv` at its root. The combined CSV files prepend `experimentId` and `resultDirectory`
to the unchanged columns from each experiment. Scenario snapshots remain in their experiment directories and are
referenced by the manifest rather than concatenated.

Without `SkipTests`, a campaign runs `mvn -B -ntp test` exactly once. Both modes then package the CLI with
`mvn -B -ntp -pl allocation-core -am package -DskipTests`, so the package phase never repeats tests. Output paths
may contain spaces and shell metacharacters; both runners quote the value passed through Maven `exec.args` and the
manifest stores a platform-appropriate, copy-paste-safe Maven command.

Before a final campaign, complete the
[experiment environment template](experiment-environment-template.md) and run the `smoke` preset. The manifest is
written with every planned experiment in `PENDING` state before execution begins, then updated through `RUNNING`,
`COMPLETED`, or `FAILED`. `benchmarkExitCode` records the Maven benchmark process, while `validationExitCode`
records the independent artifact validation step. This lifecycle is represented by campaign manifest
`schemaVersion: 2`; the five per-experiment benchmark schema versions are unchanged.

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
| `--overwrite` | Replace the five benchmark files if any already exist |
| `--help` | CLI usage |

Unknown arguments, missing values, and invalid configuration values produce a non-zero exit status and print the
usage text. Duplicate profiles and seeds are rejected rather than silently deduplicated.

By default, the runner refuses to overwrite any benchmark output if one of the five target files already exists.
Select a new output directory for each final experiment. To intentionally replace all five files, use:

```bash
mvn -pl allocation-core exec:java \
  -Dbenchmark.sourceCommit=<commit-sha> \
  -Dexec.args="--profile GREEDY_TRAP --seed 42 --runs 3 --output benchmark-results/greedy-trap --overwrite"
```

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

The output directory is created automatically and contains five UTF-8 files. Local `benchmark-results/` output is
ignored by Git. Raw and summary formats remain at `schemaVersion: 2`. Request outcomes and scenario snapshots begin
at version 1, while metadata uses version 3 because it lists all five artifacts.

### `raw-results.csv`

One row represents one algorithm in one measured repetition. Rows are stable by configured profile order, seed
order, repetition, then GREEDY, BACKTRACKING, and CP_SAT.

Columns:

```text
schemaVersion,benchmarkRunId,generatedAt,profile,seed,scenarioFingerprint,repetition,algorithm,executionOrderPosition,resourceCount,requestCount,backtrackingTimeLimitMs,cpSatTimeLimitSeconds,totalPriorityScore,allocatedRequests,rejectedRequests,measuredExecutionTimeMs,algorithmExecutionTimeMs,exploredStates,stoppedByLimit,algorithmStatus,objectiveValue
```

### `summary-results.csv`

One row represents one `profile + seed + algorithm` combination. Times and averages retain six decimal places in
CSV output; aggregation uses unrounded values.

Columns:

```text
schemaVersion,benchmarkRunId,generatedAt,profile,seed,scenarioFingerprint,algorithm,resourceCount,requestCount,measuredRuns,averageMeasuredExecutionTimeMs,medianMeasuredExecutionTimeMs,minimumMeasuredExecutionTimeMs,maximumMeasuredExecutionTimeMs,averageTotalPriorityScore,bestTotalPriorityScore,worstTotalPriorityScore,averageAllocatedRequests,stoppedByLimitRuns,optimalCpSatRuns
```

`optimalCpSatRuns` is counted only for CP-SAT rows. Other algorithms have no CP-SAT solver status and report zero.

### `request-outcomes.csv`

One row represents one original allocation request for one algorithm in one measured repetition. Rows follow the
configured profile and seed order, repetition, canonical algorithm order, and original scenario request order.
Accepted rows contain assigned resource IDs and names separated by semicolons. Rejected rows contain the existing
algorithm rejection reason. `UNKNOWN` is used instead of silently choosing a result when an algorithm result is
missing or internally inconsistent.

Columns:

```text
schemaVersion,benchmarkRunId,generatedAt,profile,seed,scenarioFingerprint,repetition,algorithm,executionOrderPosition,requestId,requestName,requestPriority,requestStart,requestEnd,outcome,assignedResourceIds,assignedResourceNames,rejectionReason
```

This file can be substantially larger than the aggregate files because every request is retained for every
algorithm and measured repetition. For example, PowerShell can filter one experimental slice with:

```powershell
Import-Csv benchmark-results/request-outcomes.csv |
  Where-Object {
    $_.profile -eq 'CONFLICT_HEAVY' -and
    $_.seed -eq '42' -and
    $_.algorithm -eq 'CP_SAT' -and
    $_.outcome -eq 'REJECTED'
  }
```

### `scenario-snapshots.json`

The version 1 document retains one complete logical input for each unique
`profile + seed + scenarioFingerprint` combination. Resources and requests keep their original order, as do
availability windows and requirements. Capacity map keys are sorted for stable JSON output. A scenario is not
duplicated for each algorithm or repetition.

### `metadata.json`

The versioned metadata document contains:

- benchmark run ID and UTC generation time;
- Maven project version and source commit;
- Java version, vendor, and VM name;
- operating system name, version, and architecture;
- available processor count;
- complete benchmark configuration;
- selected profiles and seeds;
- stable algorithm list;
- paths to the raw, summary, request outcome, scenario snapshot, and metadata files.

All result artifacts share one `benchmarkRunId`, allowing one experimental invocation to be identified
consistently. Request outcome rows and scenario snapshots also retain the same `scenarioFingerprint` as the raw and
summary records.
`sourceCommit` is resolved from `benchmark.sourceCommit`, then `BENCHMARK_GIT_COMMIT`, then `GITHUB_SHA`, with
`UNKNOWN` as the fallback. Maven supplies `projectVersion`; its fallback is also `UNKNOWN`.

## Running From IntelliJ IDEA

Create an **Application** run configuration with:

- Main class: `allocation.benchmark.BenchmarkMain`
- Module classpath: `allocation-core`
- JRE: Java 17
- Working directory: `$PROJECT_DIR$`
- Program arguments: for example,
  `--profile GREEDY_TRAP --seed 42 --warmups 1 --runs 3 --output benchmark-results/intellij`
- VM options: `-Dbenchmark.sourceCommit=<commit-sha> -Dbenchmark.projectVersion=1.0-SNAPSHOT`

Maven supplies `benchmark.projectVersion` automatically through the `allocation-core` plugin configuration. A
direct IntelliJ Application run does not, so the VM option must be provided manually when provenance is required.
Use **Run**, not **Debug**, for timing measurements. The complete campaign should be launched from PowerShell or a
terminal with one of the campaign scripts; one `BenchmarkMain` configuration represents only one experiment.
