# Allocation Algorithm Experiment Plan

## Purpose

This campaign provides a reproducible experimental basis for comparing GREEDY, BACKTRACKING, and CP_SAT in the
AllocationLibrary thesis evaluation. It measures both aggregate solution quality and the decision made for every
individual allocation request. The campaign uses the existing deterministic benchmark profiles and public
`ResourceAllocator` API; it does not change algorithm behavior.

## Research Questions

1. How does solution quality differ among GREEDY, BACKTRACKING, and CP_SAT?
2. How does execution time grow with the number of resources and requests?
3. In which scenarios does GREEDY produce a lower total priority score?
4. Which requests does GREEDY reject compared with BACKTRACKING and CP_SAT?
5. How often does BACKTRACKING stop because of its time limit?
6. How often does CP_SAT confirm an `OPTIMAL` status?
7. How do the `CONFLICT_HEAVY` and `CAPACITY_HEAVY` profiles affect the algorithms?
8. How do different time limits affect score and request-level decisions?

## Hypotheses

- GREEDY will usually execute fastest but can produce lower scores when an early local choice consumes a resource
  needed by a later request.
- BACKTRACKING can match CP_SAT on small scenarios, but its `stoppedByLimit` frequency will increase with problem
  size and conflict density.
- CP_SAT will confirm `OPTIMAL` more often than BACKTRACKING completes an exhaustive search on medium and larger
  scenarios under comparable practical limits.
- `CONFLICT_HEAVY` will increase rejection counts and expose time-limit sensitivity.
- `CAPACITY_HEAVY` will expose differences caused by scarce high-capacity resources even when many resources have
  the correct type.
- Increasing time limits should improve or preserve the best score; it may change individual accepted and rejected
  requests even when the aggregate score is unchanged.

## Variables

Controlled variables:

- source commit and project version;
- Java version and Java VM;
- physical machine, operating system, and power mode;
- benchmark generator implementation and scenario fingerprint format;
- execution-order rotation;
- algorithm configuration except for the explicitly varied time limits;
- warmup policy and measured repetition count within an experiment;
- background workload and debugger state.

Independent variables:

- algorithm: GREEDY, BACKTRACKING, or CP_SAT;
- benchmark profile;
- deterministic seed;
- resource and request count in SCALE experiments;
- resource type count in SCALE experiments;
- BACKTRACKING and CP-SAT time limits.

Dependent metrics:

- `totalPriorityScore`;
- accepted and rejected request counts;
- `measuredExecutionTimeMs` and `algorithmExecutionTimeMs`;
- `exploredStates`;
- `stoppedByLimit`;
- CP-SAT `algorithmStatus` and `objectiveValue`;
- per-request `ACCEPTED`, `REJECTED`, or `UNKNOWN` outcome;
- assigned resource IDs and names;
- rejection reason.

## Profiles And Seeds

The campaign uses the existing profiles without changing their generators:

- `GREEDY_TRAP` validates the known score difference of 10 versus 19.
- `BALANCED_SMALL` provides a small general allocation problem.
- `BALANCED_MEDIUM` includes more resources, requirements, and time intervals.
- `CONFLICT_HEAVY` emphasizes overlapping requests and resource scarcity.
- `CAPACITY_HEAVY` emphasizes capacity mismatches.
- `SCALE` varies resource, request, and type counts.

Core-profile experiments use seeds `42,43,44,45,46`. SCALE and time-limit experiments use seeds `42,43,44`.
The smoke experiment uses seed `42`.

## Presets

### Smoke

| Experiment | Profiles | Seeds | Warmups | Measured runs | Backtracking | CP-SAT |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| `greedy-trap-validation` | GREEDY_TRAP | 42 | 3 | 6 | 500 ms | 1 s |

### Standard

| Experiment | Profiles | Seeds | Warmups | Runs | Backtracking | CP-SAT | SCALE |
| --- | --- | --- | ---: | ---: | ---: | ---: | --- |
| `core-profiles` | BALANCED_SMALL, BALANCED_MEDIUM, CONFLICT_HEAVY, CAPACITY_HEAVY | 42-46 | 3 | 9 | 2000 ms | 2 s | n/a |
| `scale-10` | SCALE | 42-44 | 3 | 6 | 1000 ms | 2 s | 10/10/3 |
| `scale-20` | SCALE | 42-44 | 3 | 6 | 1000 ms | 2 s | 20/20/3 |
| `scale-30` | SCALE | 42-44 | 3 | 6 | 1000 ms | 2 s | 30/30/4 |
| `scale-40` | SCALE | 42-44 | 3 | 6 | 1000 ms | 2 s | 40/40/5 |

The SCALE column is `resources/requests/resource types`.

### Extended

Extended contains the complete standard preset plus five `CONFLICT_HEAVY` sensitivity experiments. Each uses
seeds `42,43,44`, 3 warmups, and 6 measured runs.

| Experiment | Backtracking limit | CP-SAT limit |
| --- | ---: | ---: |
| `limits-100ms` | 100 ms | 0.1 s |
| `limits-500ms` | 500 ms | 0.5 s |
| `limits-1000ms` | 1000 ms | 1 s |
| `limits-2000ms` | 2000 ms | 2 s |
| `limits-5000ms` | 5000 ms | 5 s |

Measured run counts are divisible by three so every algorithm occupies every execution-order position equally over
each complete rotation cycle. Warmups are excluded from exported measured results.

## Reproducibility Rules

Final timing measurements must be run:

- on the same physical computer;
- with the same Java version and VM;
- without a debugger;
- without parallel CPU-, disk-, or memory-intensive processes;
- with a stable operating-system power mode;
- with measured run counts divisible by three;
- from a clean Git working tree;
- with the current HEAD recorded as `sourceCommit`;
- with one output directory per final campaign.

The seed alone is not sufficient proof of an identical scenario. Compare `scenarioFingerprint`, `sourceCommit`,
profile, seed, SCALE dimensions, and time limits. Archive the complete campaign directory, including the manifest,
combined CSV files, and every experiment subdirectory. Do not commit machine-dependent `benchmark-results/` data
to Git.

## Request Outcome Interpretation

`request-outcomes.csv` permits direct comparison of the same original request across algorithms and repetitions.
An `ACCEPTED` row records assigned resources. A `REJECTED` row records the algorithm's reason. `UNKNOWN` means that
the algorithm result was structurally inconsistent or incomplete; final campaigns reject any experiment containing
an `UNKNOWN` outcome.

Aggregate score ties do not imply identical decisions. Two algorithms can accept different request sets with the
same score, so thesis analysis should compare request IDs, assigned resources, and rejection reasons alongside
summary metrics.

## Runtime Interpretation

Use medians and distributions over measured repetitions rather than one execution. Timing results from different
computers, Java versions, power modes, or background workloads are not directly comparable. `measuredExecutionTimeMs`
is the campaign's primary wall-clock metric around the public allocator call. `algorithmExecutionTimeMs` is the
coarser algorithm-reported statistic and can be zero for very fast executions.

Time limits make some results censored observations: a stopped run reports the best solution found before the
limit, not necessarily the algorithm's unrestricted result. Score and request-level decisions should therefore be
interpreted together with `stoppedByLimit` and CP-SAT `algorithmStatus`.

## Archiving And Review

Before using a campaign in the thesis:

1. Confirm `campaignStatus` is `COMPLETED`.
2. Confirm every experiment status is `COMPLETED`.
3. Retain `campaign-manifest.json` and all five files from each experiment.
4. Retain the three combined campaign CSV files.
5. Verify source commit, Java version, machine details, fingerprints, and row counts.
6. Record the physical-machine specification and power mode separately with the archived results.

The campaign scripts prevent accidental reuse of an existing output root unless overwrite is explicitly enabled.
For final experiments, a new output root is preferable to overwriting an earlier campaign.
