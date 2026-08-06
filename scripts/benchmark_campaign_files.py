#!/usr/bin/env python3
"""Standard-library support for reproducible benchmark campaign runners."""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import platform
import re
import shlex
import statistics
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


EXPECTED_FILES = (
    "raw-results.csv",
    "summary-results.csv",
    "request-outcomes.csv",
    "scenario-snapshots.json",
    "metadata.json",
)
EXPECTED_ALGORITHMS = ("GREEDY", "BACKTRACKING", "CP_SAT")
EXPECTED_PRESET_IDS = {
    "smoke": ["greedy-trap-validation"],
    "standard": ["core-profiles", "scale-10", "scale-20", "scale-30", "scale-40"],
    "extended": [
        "core-profiles",
        "scale-10",
        "scale-20",
        "scale-30",
        "scale-40",
        "limits-100ms",
        "limits-500ms",
        "limits-1000ms",
        "limits-2000ms",
        "limits-5000ms",
    ],
}
COMBINED_FILES = {
    "rawResults": "campaign-raw-results.csv",
    "summaryResults": "campaign-summary.csv",
    "requestOutcomes": "campaign-request-outcomes.csv",
}
FLOAT_TOLERANCE = 2.0e-6


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def comma_values(value: Any, label: str, experiment_id: str) -> list[str]:
    require(isinstance(value, str) and value.strip(), f"Experiment {experiment_id} has a blank {label} argument.")
    values = [item.strip() for item in value.split(",")]
    require(all(values), f"Experiment {experiment_id} has a blank {label} value.")
    duplicates = [item for item, count in Counter(values).items() if count > 1]
    if duplicates:
        raise ValueError(f"Experiment {experiment_id} has duplicate {label} value: {duplicates[0]}")
    return values


def validate_experiment(experiment: dict[str, Any]) -> None:
    experiment_id = experiment.get("experimentId")
    require(isinstance(experiment_id, str) and experiment_id.strip(), "Campaign plan contains a blank experiment ID.")
    experiment_id = experiment_id.strip()
    profiles = comma_values(experiment.get("profiles"), "profile", experiment_id)
    seeds = comma_values(experiment.get("seeds"), "seed", experiment_id)
    for seed in seeds:
        try:
            int(seed)
        except ValueError as error:
            raise ValueError(f"Experiment {experiment_id} has invalid seed value: {seed}") from error

    warmups = experiment.get("warmups")
    runs = experiment.get("measuredRuns")
    backtracking_limit = experiment.get("backtrackingTimeLimitMs")
    cp_sat_limit = experiment.get("cpSatTimeLimitSeconds")
    require(isinstance(warmups, int) and not isinstance(warmups, bool) and warmups >= 0,
            f"Experiment {experiment_id} has invalid warmups: {warmups}")
    require(isinstance(runs, int) and not isinstance(runs, bool) and runs > 0,
            f"Experiment {experiment_id} has invalid measuredRuns: {runs}")
    require(runs % 3 == 0, f"Experiment {experiment_id} measuredRuns must be divisible by three: {runs}")
    require(isinstance(backtracking_limit, int) and not isinstance(backtracking_limit, bool) and backtracking_limit > 0,
            f"Experiment {experiment_id} has invalid Backtracking limit: {backtracking_limit}")
    require(isinstance(cp_sat_limit, (int, float)) and not isinstance(cp_sat_limit, bool)
            and math.isfinite(float(cp_sat_limit)) and float(cp_sat_limit) > 0,
            f"Experiment {experiment_id} has invalid CP-SAT limit: {cp_sat_limit}")

    scale_fields = ("scaleResources", "scaleRequests", "scaleResourceTypes")
    scale_values = [experiment.get(field) for field in scale_fields]
    has_any_scale = any(value is not None for value in scale_values)
    has_all_scale = all(value is not None for value in scale_values)
    is_scale = profiles == ["SCALE"]
    require(has_any_scale == has_all_scale, f"Experiment {experiment_id} has an incomplete SCALE parameter trio.")
    require(not has_any_scale or is_scale, f"Experiment {experiment_id} defines SCALE values for a non-SCALE profile.")
    require(not is_scale or has_all_scale, f"Experiment {experiment_id} is SCALE but has no complete SCALE parameter trio.")
    if has_all_scale:
        require(all(isinstance(value, int) and not isinstance(value, bool) and value > 0 for value in scale_values),
                f"Experiment {experiment_id} has non-positive SCALE values: {scale_values}")
        require(scale_values[2] <= scale_values[0],
                f"Experiment {experiment_id} has more resource types than resources: {scale_values[2]}")


def validate_plan(plan: dict[str, Any]) -> None:
    require(isinstance(plan, dict), "Campaign plan must be a JSON object.")
    require(plan.get("schemaVersion") == 1, "Unsupported campaign plan schema version.")
    experiments = plan.get("experiments")
    require(isinstance(experiments, list) and experiments, "Campaign plan must contain experiments.")
    canonical_definitions: set[str] = set()
    experiment_ids: set[str] = set()
    for experiment in experiments:
        require(isinstance(experiment, dict), "Campaign plan contains an invalid experiment definition.")
        canonical = json.dumps(experiment, sort_keys=True, separators=(",", ":"))
        require(canonical not in canonical_definitions,
                f"Duplicate experiment definition: {experiment.get('experimentId', '<blank>')}")
        canonical_definitions.add(canonical)
        validate_experiment(experiment)
        experiment_id = experiment["experimentId"].strip()
        require(experiment_id not in experiment_ids, f"Duplicate experiment ID: {experiment_id}")
        experiment_ids.add(experiment_id)

    presets = plan.get("presets")
    require(isinstance(presets, dict), "Campaign plan must contain presets.")
    for preset, expected_ids in EXPECTED_PRESET_IDS.items():
        ids = presets.get(preset)
        require(ids == expected_ids, f"Unexpected experiment IDs for preset: {preset}")
        require(len(ids) == len(set(ids)), f"Preset {preset} contains a duplicate experiment ID.")
        for experiment_id in ids:
            require(experiment_id in experiment_ids, f"Unknown experiment ID: {experiment_id}")


def load_plan(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        plan = json.load(handle)
    validate_plan(plan)
    return plan


def selected_experiments(plan: dict[str, Any], preset: str) -> list[dict[str, Any]]:
    validate_plan(plan)
    require(preset in EXPECTED_PRESET_IDS, f"Unknown campaign preset: {preset}")
    by_id = {item["experimentId"]: item for item in plan["experiments"]}
    return [by_id[experiment_id] for experiment_id in plan["presets"][preset]]


def validate_output_directories(experiments: Iterable[dict[str, Any]], output_root: Path) -> None:
    seen: set[str] = set()
    for experiment in experiments:
        output = str((output_root / experiment["experimentId"]).resolve()).casefold()
        require(output not in seen,
                f"Experiment {experiment['experimentId']} has a duplicate output directory: {output}")
        seen.add(output)


def benchmark_arguments(experiment: dict[str, Any], output_dir: Path, overwrite: bool) -> list[str]:
    arguments = [
        "--profile", str(experiment["profiles"]),
        "--seed", str(experiment["seeds"]),
        "--warmups", str(experiment["warmups"]),
        "--runs", str(experiment["measuredRuns"]),
        "--backtracking-limit-ms", str(experiment["backtrackingTimeLimitMs"]),
        "--cp-sat-limit-seconds", format(float(experiment["cpSatTimeLimitSeconds"]), ".15g"),
        "--output", str(output_dir),
    ]
    if experiment.get("scaleResources") is not None:
        arguments.extend([
            "--resources", str(experiment["scaleResources"]),
            "--requests", str(experiment["scaleRequests"]),
            "--resource-types", str(experiment["scaleResourceTypes"]),
        ])
    if overwrite:
        arguments.append("--overwrite")
    return arguments


def quote_exec_argument(value: str) -> str:
    """Quote one token for the exec-maven-plugin command-line parser."""
    if value and re.fullmatch(r"[A-Za-z0-9_./,:=+@\\-]+", value):
        return value
    if "'" not in value:
        return f"'{value}'"
    escaped = re.sub(r'(\\*)"', lambda match: match.group(1) * 2 + '\\"', value)
    escaped = re.sub(r"(\\+)$", lambda match: match.group(1) * 2, escaped)
    return f'"{escaped}"'


def exec_arguments_text(arguments: Iterable[str]) -> str:
    return " ".join(quote_exec_argument(str(argument)) for argument in arguments)


def maven_command(experiment: dict[str, Any], output_dir: Path, commit: str, overwrite: bool) -> str:
    command = [
        "mvn", "-pl", "allocation-core", "exec:java",
        f"-Dbenchmark.sourceCommit={commit}",
        f"-Dexec.args={exec_arguments_text(benchmark_arguments(experiment, output_dir, overwrite))}",
    ]
    return shlex.join(command)


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        return list(csv.DictReader(handle))


def csv_header(path: Path) -> list[str]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        return next(csv.reader(handle))


def read_json(path: Path) -> Any:
    with path.open(encoding="utf-8-sig") as handle:
        return json.load(handle)


def require_schema(rows: list[dict[str, str]], version: str, label: str) -> None:
    require(rows and {row.get("schemaVersion") for row in rows} == {version},
            f"{label} has an unexpected schemaVersion.")


def result_key(row: dict[str, str]) -> tuple[str, str, str, str]:
    return row["profile"], row["seed"], row["repetition"], row["algorithm"]


def summary_key(row: dict[str, str]) -> tuple[str, str, str]:
    return row["profile"], row["seed"], row["algorithm"]


def as_float(value: str, label: str) -> float:
    try:
        result = float(value)
    except ValueError as error:
        raise ValueError(f"Invalid decimal value for {label}: {value}") from error
    require(math.isfinite(result), f"Non-finite decimal value for {label}: {value}")
    return result


def close(actual: str, expected: float, label: str) -> None:
    require(math.isclose(as_float(actual, label), expected, rel_tol=0.0, abs_tol=FLOAT_TOLERANCE),
            f"Summary aggregate mismatch for {label}: expected {expected}, found {actual}")


def validate_smoke(raw: list[dict[str, str]], outcomes: list[dict[str, str]]) -> None:
    expected_scores = {"GREEDY": 10, "BACKTRACKING": 19, "CP_SAT": 19}
    for algorithm, score in expected_scores.items():
        rows = [row for row in raw if row["algorithm"] == algorithm]
        require(len(rows) == 6, f"Smoke result is missing runs for {algorithm}.")
        require(all(int(row["totalPriorityScore"]) == score for row in rows),
                f"Unexpected smoke score for {algorithm}.")
    expected = (
        ("GREEDY", "REQ_SMALL", "ACCEPTED"),
        ("GREEDY", "REQ_BIG", "REJECTED"),
        ("BACKTRACKING", "REQ_SMALL", "ACCEPTED"),
        ("BACKTRACKING", "REQ_BIG", "ACCEPTED"),
        ("CP_SAT", "REQ_SMALL", "ACCEPTED"),
        ("CP_SAT", "REQ_BIG", "ACCEPTED"),
    )
    for algorithm, request_id, outcome in expected:
        matches = [row for row in outcomes if row["algorithm"] == algorithm
                   and row["requestId"] == request_id and row["outcome"] == outcome]
        require(len(matches) == 6, f"Unexpected smoke outcome for {algorithm}/{request_id}/{outcome}.")


def validate_raw(experiment: dict[str, Any], raw: list[dict[str, str]]) -> None:
    experiment_id = experiment["experimentId"]
    profiles = comma_values(experiment["profiles"], "profile", experiment_id)
    seeds = comma_values(experiment["seeds"], "seed", experiment_id)
    repetitions = [str(value) for value in range(1, int(experiment["measuredRuns"]) + 1)]
    expected_keys = {
        (profile, seed, repetition, algorithm)
        for profile in profiles for seed in seeds for repetition in repetitions for algorithm in EXPECTED_ALGORITHMS
    }
    actual_keys = [result_key(row) for row in raw]
    require(len(actual_keys) == len(set(actual_keys)), f"Duplicate raw result tuple for {experiment_id}.")
    require(set(actual_keys) == expected_keys, f"Raw profile, seed, repetition, or algorithm set mismatch for {experiment_id}.")
    require({row["profile"] for row in raw} == set(profiles), f"Raw profile set mismatch for {experiment_id}.")
    require({row["seed"] for row in raw} == set(seeds), f"Raw seed set mismatch for {experiment_id}.")
    require(all(int(row["backtrackingTimeLimitMs"]) == int(experiment["backtrackingTimeLimitMs"]) for row in raw),
            f"Raw Backtracking limit mismatch for {experiment_id}.")
    require(all(math.isclose(as_float(row["cpSatTimeLimitSeconds"], "cpSatTimeLimitSeconds"),
                             float(experiment["cpSatTimeLimitSeconds"]), rel_tol=0.0, abs_tol=FLOAT_TOLERANCE)
                for row in raw), f"Raw CP-SAT limit mismatch for {experiment_id}.")

    fingerprints: dict[tuple[str, str], set[str]] = defaultdict(set)
    for row in raw:
        fingerprints[(row["profile"], row["seed"])].add(row["scenarioFingerprint"])
    for key, values in fingerprints.items():
        require(len(values) == 1, f"Multiple scenario fingerprints found for {key}.")

    if profiles == ["SCALE"]:
        require(all(int(row["resourceCount"]) == int(experiment["scaleResources"]) for row in raw),
                f"SCALE resourceCount mismatch for {experiment_id}.")
        require(all(int(row["requestCount"]) == int(experiment["scaleRequests"]) for row in raw),
                f"SCALE requestCount mismatch for {experiment_id}.")

    position_counts: dict[tuple[str, str, str], Counter[int]] = defaultdict(Counter)
    for row in raw:
        position = int(row["executionOrderPosition"])
        require(position in (1, 2, 3), f"Invalid executionOrderPosition for {result_key(row)}: {position}")
        position_counts[(row["profile"], row["seed"], row["algorithm"])][position] += 1
    expected_per_position = int(experiment["measuredRuns"]) // 3
    for key, counts in position_counts.items():
        require(counts == Counter({1: expected_per_position, 2: expected_per_position, 3: expected_per_position}),
                f"Unbalanced executionOrderPosition for {key}: {dict(counts)}")


def validate_outcomes(raw: list[dict[str, str]], outcomes: list[dict[str, str]]) -> None:
    raw_by_key = {result_key(row): row for row in raw}
    grouped: dict[tuple[str, str, str, str], list[dict[str, str]]] = defaultdict(list)
    for outcome in outcomes:
        key = result_key(outcome)
        require(key in raw_by_key, f"Request outcome has no matching raw result: {key}")
        raw_row = raw_by_key[key]
        require(outcome["benchmarkRunId"] == raw_row["benchmarkRunId"], f"Outcome benchmarkRunId mismatch for {key}.")
        require(outcome["scenarioFingerprint"] == raw_row["scenarioFingerprint"], f"Outcome fingerprint mismatch for {key}.")
        require(outcome["executionOrderPosition"] == raw_row["executionOrderPosition"],
                f"Outcome executionOrderPosition mismatch for {key}.")
        grouped[key].append(outcome)
    require(set(grouped) == set(raw_by_key), "One or more raw results have no request outcomes.")
    for key, raw_row in raw_by_key.items():
        group = grouped[key]
        accepted = sum(row["outcome"] == "ACCEPTED" for row in group)
        rejected = sum(row["outcome"] == "REJECTED" for row in group)
        unknown = sum(row["outcome"] == "UNKNOWN" for row in group)
        require(accepted == int(raw_row["allocatedRequests"]), f"Accepted count mismatch for {key}.")
        require(rejected == int(raw_row["rejectedRequests"]), f"Rejected count mismatch for {key}.")
        require(accepted + rejected + unknown == int(raw_row["requestCount"]), f"Outcome count mismatch for {key}.")
        require(unknown == 0, f"UNKNOWN request outcomes found for {key}.")


def validate_summary(experiment: dict[str, Any], raw: list[dict[str, str]], summary: list[dict[str, str]]) -> None:
    profiles = comma_values(experiment["profiles"], "profile", experiment["experimentId"])
    seeds = comma_values(experiment["seeds"], "seed", experiment["experimentId"])
    expected_keys = {(profile, seed, algorithm) for profile in profiles for seed in seeds for algorithm in EXPECTED_ALGORITHMS}
    actual_keys = [summary_key(row) for row in summary]
    require(len(actual_keys) == len(set(actual_keys)), f"Duplicate summary tuple for {experiment['experimentId']}.")
    require(set(actual_keys) == expected_keys, f"Summary profile, seed, or algorithm set mismatch for {experiment['experimentId']}.")
    raw_groups: dict[tuple[str, str, str], list[dict[str, str]]] = defaultdict(list)
    for row in raw:
        raw_groups[summary_key(row)].append(row)
    for row in summary:
        key = summary_key(row)
        group = raw_groups[key]
        times = [as_float(item["measuredExecutionTimeMs"], "measuredExecutionTimeMs") for item in group]
        scores = [int(item["totalPriorityScore"]) for item in group]
        allocated = [int(item["allocatedRequests"]) for item in group]
        require(int(row["measuredRuns"]) == len(group) == int(experiment["measuredRuns"]),
                f"Summary measuredRuns mismatch for {key}.")
        require(row["scenarioFingerprint"] == group[0]["scenarioFingerprint"], f"Summary fingerprint mismatch for {key}.")
        require(int(row["resourceCount"]) == int(group[0]["resourceCount"]), f"Summary resourceCount mismatch for {key}.")
        require(int(row["requestCount"]) == int(group[0]["requestCount"]), f"Summary requestCount mismatch for {key}.")
        close(row["averageMeasuredExecutionTimeMs"], statistics.fmean(times), f"{key}/averageMeasuredExecutionTimeMs")
        close(row["medianMeasuredExecutionTimeMs"], statistics.median(times), f"{key}/medianMeasuredExecutionTimeMs")
        close(row["minimumMeasuredExecutionTimeMs"], min(times), f"{key}/minimumMeasuredExecutionTimeMs")
        close(row["maximumMeasuredExecutionTimeMs"], max(times), f"{key}/maximumMeasuredExecutionTimeMs")
        close(row["averageTotalPriorityScore"], statistics.fmean(scores), f"{key}/averageTotalPriorityScore")
        require(int(row["bestTotalPriorityScore"]) == max(scores), f"Summary best score mismatch for {key}.")
        require(int(row["worstTotalPriorityScore"]) == min(scores), f"Summary worst score mismatch for {key}.")
        close(row["averageAllocatedRequests"], statistics.fmean(allocated), f"{key}/averageAllocatedRequests")
        require(int(row["stoppedByLimitRuns"]) == sum(item["stoppedByLimit"].lower() == "true" for item in group),
                f"Summary stoppedByLimitRuns mismatch for {key}.")
        require(int(row["optimalCpSatRuns"]) == sum(item["algorithmStatus"] == "OPTIMAL" for item in group),
                f"Summary optimalCpSatRuns mismatch for {key}.")


def resolved_metadata_path(value: str, output_dir: Path) -> Path:
    path = Path(value)
    if not path.is_absolute():
        path = output_dir / path.name
    return path.resolve()


def validate_metadata(experiment: dict[str, Any], output_dir: Path, commit: str,
                      metadata: dict[str, Any], paths: dict[str, Path]) -> None:
    experiment_id = experiment["experimentId"]
    profiles = comma_values(experiment["profiles"], "profile", experiment_id)
    seeds = [int(value) for value in comma_values(experiment["seeds"], "seed", experiment_id)]
    require(metadata.get("schemaVersion") == 3, "metadata.json has an unexpected schemaVersion.")
    require(metadata.get("sourceCommit") == commit, f"Metadata sourceCommit mismatch for {experiment_id}.")
    require(metadata.get("profiles") == profiles, f"Metadata profile order mismatch for {experiment_id}.")
    require(metadata.get("seeds") == seeds, f"Metadata seed order mismatch for {experiment_id}.")
    require(metadata.get("algorithms") == list(EXPECTED_ALGORITHMS), f"Metadata algorithms mismatch for {experiment_id}.")
    configuration = metadata.get("configuration")
    require(isinstance(configuration, dict), f"Metadata configuration is missing for {experiment_id}.")
    expected_configuration = {
        "warmupRuns": int(experiment["warmups"]),
        "measuredRuns": int(experiment["measuredRuns"]),
        "backtrackingTimeLimitMs": int(experiment["backtrackingTimeLimitMs"]),
        "scaleResourceCount": int(experiment.get("scaleResources") or 20),
        "scaleRequestCount": int(experiment.get("scaleRequests") or 20),
        "scaleResourceTypeCount": int(experiment.get("scaleResourceTypes") or 3),
    }
    for key, expected in expected_configuration.items():
        require(configuration.get(key) == expected, f"Metadata {key} mismatch for {experiment_id}.")
    require(math.isclose(float(configuration.get("cpSatTimeLimitSeconds")), float(experiment["cpSatTimeLimitSeconds"]),
                         rel_tol=0.0, abs_tol=FLOAT_TOLERANCE), f"Metadata CP-SAT limit mismatch for {experiment_id}.")
    require(configuration.get("overwrite") is bool(experiment.get("_overwrite", False)),
            f"Metadata overwrite mismatch for {experiment_id}.")
    require(Path(configuration.get("outputDirectory", "")).resolve() == output_dir.resolve(),
            f"Metadata output directory mismatch for {experiment_id}.")
    metadata_files = metadata.get("files")
    require(isinstance(metadata_files, dict), f"Metadata files are missing for {experiment_id}.")
    key_by_name = {
        "raw-results.csv": "rawResults", "summary-results.csv": "summaryResults",
        "request-outcomes.csv": "requestOutcomes", "scenario-snapshots.json": "scenarioSnapshots",
        "metadata.json": "metadata",
    }
    for file_name, metadata_key in key_by_name.items():
        require(resolved_metadata_path(str(metadata_files.get(metadata_key, "")), output_dir) == paths[file_name],
                f"Metadata path mismatch for {metadata_key} in {experiment_id}.")


def validate_output(experiment: dict[str, Any], output_dir: Path, commit: str,
                    overwrite: bool | None = None) -> dict[str, Any]:
    candidate = dict(experiment)
    candidate["_overwrite"] = bool(overwrite)
    paths = {name: (output_dir / name).resolve() for name in EXPECTED_FILES}
    for path in paths.values():
        require(path.is_file(), f"Missing benchmark output file: {path}")
    raw = read_csv(paths["raw-results.csv"])
    summary = read_csv(paths["summary-results.csv"])
    outcomes = read_csv(paths["request-outcomes.csv"])
    snapshots = read_json(paths["scenario-snapshots.json"])
    metadata = read_json(paths["metadata.json"])
    require_schema(raw, "2", "raw-results.csv")
    require_schema(summary, "2", "summary-results.csv")
    require_schema(outcomes, "1", "request-outcomes.csv")
    require(isinstance(snapshots, dict) and snapshots.get("schemaVersion") == 1,
            "scenario-snapshots.json has an unexpected schemaVersion.")
    validate_raw(candidate, raw)
    validate_outcomes(raw, outcomes)
    validate_summary(candidate, raw, summary)
    validate_metadata(candidate, output_dir, commit, metadata, paths)
    run_ids = {*(row["benchmarkRunId"] for row in raw), *(row["benchmarkRunId"] for row in summary),
               *(row["benchmarkRunId"] for row in outcomes), str(metadata.get("benchmarkRunId"))}
    require(len(run_ids) == 1, f"Benchmark run IDs do not match for {experiment['experimentId']}.")
    snapshot_rows = snapshots.get("scenarios")
    require(isinstance(snapshot_rows, list), "scenario-snapshots.json scenarios must be an array.")
    snapshot_keys = {(str(row["profile"]), str(row["seed"]), str(row["scenarioFingerprint"])) for row in snapshot_rows}
    raw_keys = {(row["profile"], row["seed"], row["scenarioFingerprint"]) for row in raw}
    require(snapshot_keys == raw_keys, f"Scenario snapshots do not match raw scenarios for {experiment['experimentId']}.")
    if experiment["experimentId"] == "greedy-trap-validation":
        require(len(raw) == 18, "Smoke raw row count must be 18.")
        require(len(summary) == 3, "Smoke summary row count must be 3.")
        require(len(outcomes) == 36, "Smoke request outcome row count must be 36.")
        require(len(snapshot_rows) == 1, "Smoke scenario snapshot count must be 1.")
        require(len({row["scenarioFingerprint"] for row in raw}) == 1, "Smoke must have one fingerprint.")
        validate_smoke(raw, outcomes)
    return {
        "benchmarkRunId": str(metadata["benchmarkRunId"]),
        "rawRowCount": len(raw), "summaryRowCount": len(summary),
        "requestOutcomeRowCount": len(outcomes), "scenarioSnapshotCount": len(snapshot_rows),
        "fingerprints": list(dict.fromkeys(row["scenarioFingerprint"] for row in raw)),
        "files": {
            "rawResults": str(paths["raw-results.csv"]), "summaryResults": str(paths["summary-results.csv"]),
            "requestOutcomes": str(paths["request-outcomes.csv"]),
            "scenarioSnapshots": str(paths["scenario-snapshots.json"]), "metadata": str(paths["metadata.json"]),
        },
    }


def experiment_record(experiment: dict[str, Any], output_dir: Path, commit: str,
                      overwrite: bool) -> dict[str, Any]:
    return {
        "experimentId": experiment["experimentId"], "profileArgument": experiment["profiles"],
        "seedArgument": experiment["seeds"], "warmups": experiment["warmups"],
        "measuredRuns": experiment["measuredRuns"],
        "backtrackingTimeLimitMs": experiment["backtrackingTimeLimitMs"],
        "cpSatTimeLimitSeconds": experiment["cpSatTimeLimitSeconds"],
        "scaleResources": experiment.get("scaleResources"), "scaleRequests": experiment.get("scaleRequests"),
        "scaleResourceTypes": experiment.get("scaleResourceTypes"), "outputDirectory": str(output_dir.resolve()),
        "mavenCommand": maven_command(experiment, output_dir.resolve(), commit, overwrite),
        "benchmarkExitCode": None, "validationExitCode": None, "status": "PENDING", "errorMessage": None,
        "benchmarkRunId": None, "rawRowCount": 0, "summaryRowCount": 0,
        "requestOutcomeRowCount": 0, "scenarioSnapshotCount": 0, "fingerprints": [], "files": None,
    }


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def write_json_atomic(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, indent=2)
        handle.write("\n")
    os.replace(temporary, path)


def create_manifest(experiments: list[dict[str, Any]], source_commit: str, preset: str,
                    output_root: Path, repository_root: Path, working_tree_clean: bool,
                    java_version: str, maven_version: str, operating_system: str,
                    overwrite: bool) -> dict[str, Any]:
    validate_output_directories(experiments, output_root)
    return {
        "schemaVersion": 2, "sourceCommit": source_commit, "sourceCommitShort": source_commit[:12],
        "preset": preset, "startedAt": utc_now(), "completedAt": None,
        "javaVersion": java_version, "mavenVersion": maven_version, "operatingSystem": operating_system,
        "workingTreeClean": working_tree_clean, "repositoryRoot": str(repository_root.resolve()),
        "campaignStatus": "RUNNING", "errorMessage": None,
        "experiments": [experiment_record(item, output_root / item["experimentId"], source_commit, overwrite)
                        for item in experiments],
        "combinedFiles": None,
    }


def update_manifest_experiment(manifest: dict[str, Any], experiment_id: str, status: str,
                               benchmark_exit_code: int | None = None,
                               validation_exit_code: int | None = None,
                               error_message: str | None = None,
                               validation: dict[str, Any] | None = None) -> None:
    require(status in {"PENDING", "RUNNING", "COMPLETED", "FAILED"}, f"Invalid experiment status: {status}")
    matches = [record for record in manifest["experiments"] if record["experimentId"] == experiment_id]
    require(len(matches) == 1, f"Manifest experiment not found: {experiment_id}")
    record = matches[0]
    record["status"] = status
    if benchmark_exit_code is not None:
        record["benchmarkExitCode"] = benchmark_exit_code
    if validation_exit_code is not None:
        record["validationExitCode"] = validation_exit_code
    record["errorMessage"] = error_message
    if validation:
        record.update(validation)
    if status == "FAILED":
        manifest["campaignStatus"] = "FAILED"
        manifest["errorMessage"] = error_message
        manifest["completedAt"] = utc_now()


def combine_csv(records: list[dict[str, Any]], source_key: str, output_path: Path) -> int:
    completed = [record for record in records if record["status"] == "COMPLETED"]
    require(completed, "No completed experiments are available for combined campaign CSV output.")
    expected_header: list[str] | None = None
    row_count = 0
    with output_path.open("w", newline="", encoding="utf-8") as output:
        writer: csv.DictWriter[str] | None = None
        for record in completed:
            source_path = Path(record["files"][source_key])
            header = csv_header(source_path)
            if expected_header is None:
                expected_header = header
                writer = csv.DictWriter(output, fieldnames=["experimentId", "resultDirectory", *header], lineterminator="\n")
                writer.writeheader()
            require(header == expected_header, f"Combined CSV header mismatch: {source_path}")
            for source_row in read_csv(source_path):
                writer.writerow({"experimentId": record["experimentId"],
                                 "resultDirectory": record["outputDirectory"], **source_row})
                row_count += 1
    require(row_count == sum(int(record[{"rawResults": "rawRowCount", "summaryResults": "summaryRowCount",
                                                "requestOutcomes": "requestOutcomeRowCount"}[source_key]])
                             for record in completed), f"Combined CSV row count mismatch for {source_key}.")
    return row_count


def finalize_manifest(manifest: dict[str, Any], manifest_path: Path) -> None:
    try:
        require(all(record["status"] == "COMPLETED" for record in manifest["experiments"]),
                "Campaign cannot be completed while experiments are unfinished.")
        combined = {}
        for source_key, file_name in COMBINED_FILES.items():
            path = (manifest_path.parent / file_name).resolve()
            combine_csv(manifest["experiments"], source_key, path)
            combined[source_key] = str(path)
        manifest["combinedFiles"] = combined
        manifest["campaignStatus"] = "COMPLETED"
        manifest["errorMessage"] = None
        manifest["completedAt"] = utc_now()
        write_json_atomic(manifest_path, manifest)
    except Exception as error:
        manifest["campaignStatus"] = "FAILED"
        manifest["errorMessage"] = f"Campaign finalization failed: {error}"
        manifest["completedAt"] = utc_now()
        write_json_atomic(manifest_path, manifest)
        raise


def command_plan(args: argparse.Namespace) -> None:
    experiments = selected_experiments(load_plan(args.plan), args.preset)
    if args.output_root:
        validate_output_directories(experiments, args.output_root)
    for experiment in experiments:
        values = [experiment["experimentId"], experiment["profiles"], experiment["seeds"],
                  experiment["warmups"], experiment["measuredRuns"], experiment["backtrackingTimeLimitMs"],
                  experiment["cpSatTimeLimitSeconds"], experiment.get("scaleResources"),
                  experiment.get("scaleRequests"), experiment.get("scaleResourceTypes")]
        print("|".join("" if value is None else str(value) for value in values))


def command_validate(args: argparse.Namespace) -> None:
    experiments = {item["experimentId"]: item for item in selected_experiments(load_plan(args.plan), args.preset)}
    require(args.experiment_id in experiments, f"Experiment is not part of preset: {args.experiment_id}")
    validation = validate_output(experiments[args.experiment_id], args.output_dir, args.source_commit, args.overwrite)
    print(json.dumps(validation, indent=2))


def selected_experiment_from_args(args: argparse.Namespace) -> dict[str, Any]:
    experiments = {item["experimentId"]: item for item in selected_experiments(load_plan(args.plan), args.preset)}
    require(args.experiment_id in experiments, f"Experiment is not part of preset: {args.experiment_id}")
    return experiments[args.experiment_id]


def command_exec_args(args: argparse.Namespace) -> None:
    experiment = selected_experiment_from_args(args)
    print(exec_arguments_text(benchmark_arguments(experiment, args.output_dir, args.overwrite)))


def command_maven_command(args: argparse.Namespace) -> None:
    experiment = selected_experiment_from_args(args)
    print(maven_command(experiment, args.output_dir, args.source_commit, args.overwrite))


def command_manifest_init(args: argparse.Namespace) -> None:
    experiments = selected_experiments(load_plan(args.plan), args.preset)
    manifest = create_manifest(experiments, args.source_commit, args.preset, args.output_root,
                               args.repository_root, args.working_tree_clean == "true",
                               args.java_version, args.maven_version,
                               args.operating_system or platform.platform(), args.overwrite)
    write_json_atomic(args.manifest, manifest)


def command_manifest_update(args: argparse.Namespace) -> None:
    manifest = read_json(args.manifest)
    validation = read_json(args.validation_file) if args.validation_file else None
    update_manifest_experiment(manifest, args.experiment_id, args.status, args.benchmark_exit_code,
                               args.validation_exit_code, args.error_message, validation)
    write_json_atomic(args.manifest, manifest)


def command_manifest_fail(args: argparse.Namespace) -> None:
    manifest = read_json(args.manifest)
    manifest["campaignStatus"] = "FAILED"
    manifest["errorMessage"] = args.error_message
    manifest["completedAt"] = utc_now()
    write_json_atomic(args.manifest, manifest)


def command_finalize(args: argparse.Namespace) -> None:
    manifest = read_json(args.manifest)
    finalize_manifest(manifest, args.manifest)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    plan_parser = subparsers.add_parser("plan")
    plan_parser.add_argument("--plan", type=Path, required=True)
    plan_parser.add_argument("--preset", choices=EXPECTED_PRESET_IDS, required=True)
    plan_parser.add_argument("--output-root", type=Path)
    plan_parser.set_defaults(handler=command_plan)
    validate_parser = subparsers.add_parser("validate")
    validate_parser.add_argument("--plan", type=Path, required=True)
    validate_parser.add_argument("--preset", choices=EXPECTED_PRESET_IDS, required=True)
    validate_parser.add_argument("--experiment-id", required=True)
    validate_parser.add_argument("--output-dir", type=Path, required=True)
    validate_parser.add_argument("--source-commit", required=True)
    validate_parser.add_argument("--overwrite", action="store_true")
    validate_parser.set_defaults(handler=command_validate)
    for name, handler in (("exec-args", command_exec_args), ("maven-command", command_maven_command)):
        command_parser = subparsers.add_parser(name)
        command_parser.add_argument("--plan", type=Path, required=True)
        command_parser.add_argument("--preset", choices=EXPECTED_PRESET_IDS, required=True)
        command_parser.add_argument("--experiment-id", required=True)
        command_parser.add_argument("--output-dir", type=Path, required=True)
        command_parser.add_argument("--overwrite", action="store_true")
        if name == "maven-command":
            command_parser.add_argument("--source-commit", required=True)
        command_parser.set_defaults(handler=handler)
    init_parser = subparsers.add_parser("manifest-init")
    init_parser.add_argument("--plan", type=Path, required=True)
    init_parser.add_argument("--preset", choices=EXPECTED_PRESET_IDS, required=True)
    init_parser.add_argument("--source-commit", required=True)
    init_parser.add_argument("--output-root", type=Path, required=True)
    init_parser.add_argument("--repository-root", type=Path, required=True)
    init_parser.add_argument("--working-tree-clean", choices=("true", "false"), required=True)
    init_parser.add_argument("--java-version", required=True)
    init_parser.add_argument("--maven-version", required=True)
    init_parser.add_argument("--operating-system")
    init_parser.add_argument("--overwrite", action="store_true")
    init_parser.add_argument("--manifest", type=Path, required=True)
    init_parser.set_defaults(handler=command_manifest_init)
    update_parser = subparsers.add_parser("manifest-update")
    update_parser.add_argument("--manifest", type=Path, required=True)
    update_parser.add_argument("--experiment-id", required=True)
    update_parser.add_argument("--status", choices=("PENDING", "RUNNING", "COMPLETED", "FAILED"), required=True)
    update_parser.add_argument("--benchmark-exit-code", type=int)
    update_parser.add_argument("--validation-exit-code", type=int)
    update_parser.add_argument("--error-message")
    update_parser.add_argument("--validation-file", type=Path)
    update_parser.set_defaults(handler=command_manifest_update)
    fail_parser = subparsers.add_parser("manifest-fail")
    fail_parser.add_argument("--manifest", type=Path, required=True)
    fail_parser.add_argument("--error-message", required=True)
    fail_parser.set_defaults(handler=command_manifest_fail)
    finalize_parser = subparsers.add_parser("finalize")
    finalize_parser.add_argument("--manifest", type=Path, required=True)
    finalize_parser.set_defaults(handler=command_finalize)
    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        args.handler(args)
        return 0
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
