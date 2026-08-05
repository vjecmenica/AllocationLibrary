#!/usr/bin/env python3
"""Standard-library support for the Bash benchmark campaign runner."""

from __future__ import annotations

import argparse
import csv
import json
import os
import platform
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


EXPECTED_FILES = (
    "raw-results.csv",
    "summary-results.csv",
    "request-outcomes.csv",
    "scenario-snapshots.json",
    "metadata.json",
)
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


def load_plan(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        plan = json.load(handle)
    require(plan.get("schemaVersion") == 1, "Unsupported campaign plan schema version.")
    return plan


def selected_experiments(plan: dict[str, Any], preset: str) -> list[dict[str, Any]]:
    require(preset in EXPECTED_PRESET_IDS, f"Unknown campaign preset: {preset}")
    ids = plan.get("presets", {}).get(preset)
    require(ids == EXPECTED_PRESET_IDS[preset], f"Unexpected experiment IDs for preset: {preset}")
    experiments = {item["experimentId"]: item for item in plan.get("experiments", [])}
    selected = []
    seen_ids: set[str] = set()
    for experiment_id in ids:
        require(experiment_id in experiments, f"Unknown experiment ID: {experiment_id}")
        require(experiment_id not in seen_ids, f"Duplicate experiment ID: {experiment_id}")
        seen_ids.add(experiment_id)
        experiment = experiments[experiment_id]
        require(
            int(experiment["measuredRuns"]) % 3 == 0,
            f"Measured runs must be divisible by three: {experiment_id}",
        )
        selected.append(experiment)
    return selected


def benchmark_arguments(experiment: dict[str, Any], output_dir: Path, overwrite: bool) -> list[str]:
    arguments = [
        "--profile",
        str(experiment["profiles"]),
        "--seed",
        str(experiment["seeds"]),
        "--warmups",
        str(experiment["warmups"]),
        "--runs",
        str(experiment["measuredRuns"]),
        "--backtracking-limit-ms",
        str(experiment["backtrackingTimeLimitMs"]),
        "--cp-sat-limit-seconds",
        str(experiment["cpSatTimeLimitSeconds"]),
        "--output",
        str(output_dir),
    ]
    if experiment.get("scaleResources") is not None:
        arguments.extend(
            [
                "--resources",
                str(experiment["scaleResources"]),
                "--requests",
                str(experiment["scaleRequests"]),
                "--resource-types",
                str(experiment["scaleResourceTypes"]),
            ]
        )
    if overwrite:
        arguments.append("--overwrite")
    return arguments


def maven_command(experiment: dict[str, Any], output_dir: Path, commit: str, overwrite: bool) -> str:
    cli = " ".join(benchmark_arguments(experiment, output_dir, overwrite))
    return (
        "mvn -pl allocation-core exec:java "
        f'"-Dbenchmark.sourceCommit={commit}" "-Dexec.args={cli}"'
    )


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        return list(csv.DictReader(handle))


def read_json(path: Path) -> Any:
    with path.open(encoding="utf-8-sig") as handle:
        return json.load(handle)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def require_schema(rows: list[dict[str, str]], version: str, label: str) -> None:
    require({row.get("schemaVersion") for row in rows} == {version}, f"{label} has an unexpected schemaVersion.")


def result_key(row: dict[str, str]) -> tuple[str, str, str, str]:
    return row["profile"], row["seed"], row["repetition"], row["algorithm"]


def validate_smoke(raw: list[dict[str, str]], outcomes: list[dict[str, str]]) -> None:
    expected_scores = {"GREEDY": "10", "BACKTRACKING": "19", "CP_SAT": "19"}
    for algorithm, score in expected_scores.items():
        rows = [row for row in raw if row["algorithm"] == algorithm]
        require(len(rows) == 6, f"Smoke result is missing runs for {algorithm}.")
        require(all(row["totalPriorityScore"] == score for row in rows), f"Unexpected smoke score for {algorithm}.")

    expected = (
        ("GREEDY", "REQ_SMALL", "ACCEPTED"),
        ("GREEDY", "REQ_BIG", "REJECTED"),
        ("BACKTRACKING", "REQ_SMALL", "ACCEPTED"),
        ("BACKTRACKING", "REQ_BIG", "ACCEPTED"),
        ("CP_SAT", "REQ_SMALL", "ACCEPTED"),
        ("CP_SAT", "REQ_BIG", "ACCEPTED"),
    )
    for algorithm, request_id, outcome in expected:
        matches = [
            row
            for row in outcomes
            if row["algorithm"] == algorithm
            and row["requestId"] == request_id
            and row["outcome"] == outcome
        ]
        require(len(matches) == 6, f"Unexpected smoke outcome for {algorithm}/{request_id}/{outcome}.")


def validate_output(experiment: dict[str, Any], output_dir: Path, commit: str) -> dict[str, Any]:
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
    require(snapshots.get("schemaVersion") == 1, "scenario-snapshots.json has an unexpected schemaVersion.")
    require(metadata.get("schemaVersion") == 3, "metadata.json has an unexpected schemaVersion.")

    profile_count = len(str(experiment["profiles"]).split(","))
    seed_count = len(str(experiment["seeds"]).split(","))
    expected_raw = profile_count * seed_count * int(experiment["measuredRuns"]) * 3
    expected_summary = profile_count * seed_count * 3
    expected_outcomes = sum(int(row["requestCount"]) for row in raw)
    require(len(raw) == expected_raw, f"Unexpected raw row count for {experiment['experimentId']}.")
    require(len(summary) == expected_summary, f"Unexpected summary row count for {experiment['experimentId']}.")
    require(len(outcomes) == expected_outcomes, f"Unexpected outcome row count for {experiment['experimentId']}.")
    require(not any(row["outcome"] == "UNKNOWN" for row in outcomes), f"UNKNOWN outcomes found for {experiment['experimentId']}.")
    require({row["algorithm"] for row in raw} == {"GREEDY", "BACKTRACKING", "CP_SAT"}, "Not all algorithms are present.")

    run_ids = {
        *(row["benchmarkRunId"] for row in raw),
        *(row["benchmarkRunId"] for row in summary),
        *(row["benchmarkRunId"] for row in outcomes),
        str(metadata["benchmarkRunId"]),
    }
    require(len(run_ids) == 1, "Benchmark run IDs do not match.")
    require(metadata.get("sourceCommit") == commit, "Metadata source commit does not match.")

    grouped_outcomes: dict[tuple[str, str, str, str], list[dict[str, str]]] = defaultdict(list)
    for outcome in outcomes:
        grouped_outcomes[result_key(outcome)].append(outcome)
    for raw_row in raw:
        key = result_key(raw_row)
        group = grouped_outcomes.get(key, [])
        accepted = sum(row["outcome"] == "ACCEPTED" for row in group)
        rejected = sum(row["outcome"] == "REJECTED" for row in group)
        unknown = sum(row["outcome"] == "UNKNOWN" for row in group)
        require(accepted == int(raw_row["allocatedRequests"]), f"Accepted count does not match raw result for {key}.")
        require(rejected == int(raw_row["rejectedRequests"]), f"Rejected count does not match raw result for {key}.")
        require(accepted + rejected + unknown == int(raw_row["requestCount"]), f"Outcome count does not match for {key}.")
        require(all(row["scenarioFingerprint"] == raw_row["scenarioFingerprint"] for row in group), f"Outcome fingerprint mismatch for {key}.")

    for summary_row in summary:
        matching_raw = [
            row
            for row in raw
            if row["profile"] == summary_row["profile"]
            and row["seed"] == summary_row["seed"]
            and row["algorithm"] == summary_row["algorithm"]
        ]
        require(len(matching_raw) == int(experiment["measuredRuns"]), "Summary does not match measured raw runs.")
        require(
            all(row["scenarioFingerprint"] == summary_row["scenarioFingerprint"] for row in matching_raw),
            "Summary fingerprint does not match raw results.",
        )

    snapshot_rows = snapshots.get("scenarios", [])
    snapshot_keys = {
        (str(row["profile"]), str(row["seed"]), str(row["scenarioFingerprint"]))
        for row in snapshot_rows
    }
    raw_scenario_keys = {
        (row["profile"], row["seed"], row["scenarioFingerprint"])
        for row in raw
    }
    require(snapshot_keys == raw_scenario_keys, "Scenario snapshots do not match raw scenarios.")

    if experiment["experimentId"] == "greedy-trap-validation":
        require(len(raw) == 18, "Smoke raw row count must be 18.")
        require(len(summary) == 3, "Smoke summary row count must be 3.")
        require(len(outcomes) == 36, "Smoke request outcome row count must be 36.")
        require(len(snapshot_rows) == 1, "Smoke scenario snapshot count must be 1.")
        require(len({row["scenarioFingerprint"] for row in raw}) == 1, "Smoke must have one fingerprint.")
        validate_smoke(raw, outcomes)

    return {
        "benchmarkRunId": str(metadata["benchmarkRunId"]),
        "rawRowCount": len(raw),
        "summaryRowCount": len(summary),
        "requestOutcomeRowCount": len(outcomes),
        "scenarioSnapshotCount": len(snapshot_rows),
        "fingerprints": list(dict.fromkeys(row["scenarioFingerprint"] for row in raw)),
        "files": {
            "rawResults": str(paths["raw-results.csv"]),
            "summaryResults": str(paths["summary-results.csv"]),
            "requestOutcomes": str(paths["request-outcomes.csv"]),
            "scenarioSnapshots": str(paths["scenario-snapshots.json"]),
            "metadata": str(paths["metadata.json"]),
        },
    }


def experiment_record(
    experiment: dict[str, Any],
    output_dir: Path,
    commit: str,
    overwrite: bool,
    status: str,
    exit_code: int | None,
    error_message: str | None,
    validation: dict[str, Any] | None,
) -> dict[str, Any]:
    record = {
        "experimentId": experiment["experimentId"],
        "profileArgument": experiment["profiles"],
        "seedArgument": experiment["seeds"],
        "warmups": experiment["warmups"],
        "measuredRuns": experiment["measuredRuns"],
        "backtrackingTimeLimitMs": experiment["backtrackingTimeLimitMs"],
        "cpSatTimeLimitSeconds": experiment["cpSatTimeLimitSeconds"],
        "scaleResources": experiment.get("scaleResources"),
        "scaleRequests": experiment.get("scaleRequests"),
        "scaleResourceTypes": experiment.get("scaleResourceTypes"),
        "outputDirectory": str(output_dir.resolve()),
        "mavenCommand": maven_command(experiment, output_dir.resolve(), commit, overwrite),
        "exitCode": exit_code,
        "status": status,
        "errorMessage": error_message,
        "benchmarkRunId": None,
        "rawRowCount": 0,
        "summaryRowCount": 0,
        "requestOutcomeRowCount": 0,
        "scenarioSnapshotCount": 0,
        "fingerprints": [],
        "files": None,
    }
    if validation:
        record.update(validation)
    return record


def combine_csv(records: list[dict[str, Any]], source_key: str, output_path: Path) -> None:
    fieldnames: list[str] | None = None
    rows: list[dict[str, str]] = []
    for record in records:
        source_path = Path(record["files"][source_key])
        source_rows = read_csv(source_path)
        if source_rows and fieldnames is None:
            fieldnames = ["experimentId", "resultDirectory", *source_rows[0].keys()]
        for source_row in source_rows:
            rows.append(
                {
                    "experimentId": record["experimentId"],
                    "resultDirectory": record["outputDirectory"],
                    **source_row,
                }
            )
    require(fieldnames is not None, "No rows are available for combined campaign CSV output.")
    with output_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def command_plan(args: argparse.Namespace) -> None:
    plan = load_plan(args.plan)
    experiments = selected_experiments(plan, args.preset)
    for experiment in experiments:
        values = [
            experiment["experimentId"],
            experiment["profiles"],
            experiment["seeds"],
            experiment["warmups"],
            experiment["measuredRuns"],
            experiment["backtrackingTimeLimitMs"],
            experiment["cpSatTimeLimitSeconds"],
            experiment.get("scaleResources"),
            experiment.get("scaleRequests"),
            experiment.get("scaleResourceTypes"),
        ]
        print("|".join("" if value is None else str(value) for value in values))


def command_validate(args: argparse.Namespace) -> None:
    plan = load_plan(args.plan)
    experiments = {item["experimentId"]: item for item in selected_experiments(plan, args.preset)}
    require(args.experiment_id in experiments, f"Experiment is not part of preset: {args.experiment_id}")
    validation = validate_output(experiments[args.experiment_id], args.output_dir, args.source_commit)
    print(json.dumps(validation, indent=2))


def command_finalize(args: argparse.Namespace) -> None:
    plan = load_plan(args.plan)
    experiments = selected_experiments(plan, args.preset)
    completed_ids = set(args.completed_id)
    records = []
    for experiment in experiments:
        experiment_id = experiment["experimentId"]
        output_dir = (args.output_root / experiment_id).resolve()
        if experiment_id in completed_ids:
            validation = validate_output(experiment, output_dir, args.source_commit)
            records.append(experiment_record(experiment, output_dir, args.source_commit, args.overwrite, "COMPLETED", 0, None, validation))
        elif experiment_id == args.failed_id:
            records.append(experiment_record(experiment, output_dir, args.source_commit, args.overwrite, "FAILED", args.exit_code, args.error_message, None))
        else:
            records.append(experiment_record(experiment, output_dir, args.source_commit, args.overwrite, "PENDING", None, None, None))

    campaign_status = "FAILED" if args.failed_id else "COMPLETED"
    manifest = {
        "schemaVersion": 1,
        "sourceCommit": args.source_commit,
        "sourceCommitShort": args.source_commit[:12],
        "preset": args.preset,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "javaVersion": args.java_version,
        "mavenVersion": args.maven_version,
        "operatingSystem": args.operating_system or platform.platform(),
        "workingTreeClean": args.working_tree_clean == "true",
        "repositoryRoot": str(args.repository_root.resolve()),
        "campaignStatus": campaign_status,
        "experiments": records,
        "combinedFiles": None,
    }

    args.output_root.mkdir(parents=True, exist_ok=True)
    if campaign_status == "COMPLETED":
        require(completed_ids == {item["experimentId"] for item in experiments}, "Completed campaign is missing experiments.")
        raw_path = (args.output_root / "campaign-raw-results.csv").resolve()
        summary_path = (args.output_root / "campaign-summary.csv").resolve()
        outcomes_path = (args.output_root / "campaign-request-outcomes.csv").resolve()
        combine_csv(records, "rawResults", raw_path)
        combine_csv(records, "summaryResults", summary_path)
        combine_csv(records, "requestOutcomes", outcomes_path)
        manifest["combinedFiles"] = {
            "rawResults": str(raw_path),
            "summaryResults": str(summary_path),
            "requestOutcomes": str(outcomes_path),
        }

    manifest_path = args.output_root / "campaign-manifest.json"
    with manifest_path.open("w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2)
        handle.write("\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    plan_parser = subparsers.add_parser("plan")
    plan_parser.add_argument("--plan", type=Path, required=True)
    plan_parser.add_argument("--preset", choices=EXPECTED_PRESET_IDS, required=True)
    plan_parser.set_defaults(handler=command_plan)

    validate_parser = subparsers.add_parser("validate")
    validate_parser.add_argument("--plan", type=Path, required=True)
    validate_parser.add_argument("--preset", choices=EXPECTED_PRESET_IDS, required=True)
    validate_parser.add_argument("--experiment-id", required=True)
    validate_parser.add_argument("--output-dir", type=Path, required=True)
    validate_parser.add_argument("--source-commit", required=True)
    validate_parser.set_defaults(handler=command_validate)

    finalize_parser = subparsers.add_parser("finalize")
    finalize_parser.add_argument("--plan", type=Path, required=True)
    finalize_parser.add_argument("--preset", choices=EXPECTED_PRESET_IDS, required=True)
    finalize_parser.add_argument("--source-commit", required=True)
    finalize_parser.add_argument("--output-root", type=Path, required=True)
    finalize_parser.add_argument("--repository-root", type=Path, required=True)
    finalize_parser.add_argument("--working-tree-clean", choices=("true", "false"), required=True)
    finalize_parser.add_argument("--java-version", required=True)
    finalize_parser.add_argument("--maven-version", required=True)
    finalize_parser.add_argument("--operating-system")
    finalize_parser.add_argument("--overwrite", action="store_true")
    finalize_parser.add_argument("--completed-id", action="append", default=[])
    finalize_parser.add_argument("--failed-id")
    finalize_parser.add_argument("--exit-code", type=int)
    finalize_parser.add_argument("--error-message")
    finalize_parser.set_defaults(handler=command_finalize)
    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        args.handler(args)
        return 0
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
