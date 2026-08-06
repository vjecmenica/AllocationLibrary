from __future__ import annotations

import copy
import csv
import importlib.util
import json
import shlex
import statistics
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("benchmark_campaign_files.py")
SPEC = importlib.util.spec_from_file_location("benchmark_campaign_files", MODULE_PATH)
campaign = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(campaign)


RAW_HEADER = [
    "schemaVersion", "benchmarkRunId", "generatedAt", "profile", "seed", "scenarioFingerprint",
    "repetition", "algorithm", "executionOrderPosition", "resourceCount", "requestCount",
    "backtrackingTimeLimitMs", "cpSatTimeLimitSeconds", "totalPriorityScore", "allocatedRequests",
    "rejectedRequests", "measuredExecutionTimeMs", "algorithmExecutionTimeMs", "exploredStates",
    "stoppedByLimit", "algorithmStatus", "objectiveValue",
]
SUMMARY_HEADER = [
    "schemaVersion", "benchmarkRunId", "generatedAt", "profile", "seed", "scenarioFingerprint",
    "algorithm", "resourceCount", "requestCount", "measuredRuns", "averageMeasuredExecutionTimeMs",
    "medianMeasuredExecutionTimeMs", "minimumMeasuredExecutionTimeMs", "maximumMeasuredExecutionTimeMs",
    "averageTotalPriorityScore", "bestTotalPriorityScore", "worstTotalPriorityScore",
    "averageAllocatedRequests", "stoppedByLimitRuns", "optimalCpSatRuns",
]
OUTCOME_HEADER = [
    "schemaVersion", "benchmarkRunId", "generatedAt", "profile", "seed", "scenarioFingerprint",
    "repetition", "algorithm", "executionOrderPosition", "requestId", "requestName", "requestPriority",
    "requestStart", "requestEnd", "outcome", "assignedResourceIds", "assignedResourceNames",
    "rejectionReason",
]


def experiment() -> dict:
    return {
        "experimentId": "fixture", "profiles": "BALANCED_SMALL", "seeds": "42", "warmups": 1,
        "measuredRuns": 3, "backtrackingTimeLimitMs": 500, "cpSatTimeLimitSeconds": 1.0,
        "scaleResources": None, "scaleRequests": None, "scaleResourceTypes": None,
    }


def plan_with(*experiments: dict) -> dict:
    plan = json.loads(MODULE_PATH.with_name("benchmark-campaign-plan.json").read_text(encoding="utf-8"))
    if experiments:
        plan["experiments"] = list(experiments)
        plan["presets"] = {"smoke": [experiments[0]["experimentId"]], "standard": [], "extended": []}
    return plan


def write_csv(path: Path, header: list[str], rows: list[dict[str, object]]) -> None:
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=header, lineterminator="\r\n")
        writer.writeheader()
        writer.writerows(rows)


def fixture_rows() -> tuple[list[dict[str, object]], list[dict[str, object]], list[dict[str, object]]]:
    run_id = "run-fixture"
    fingerprint = "a" * 64
    generated = "2026-01-01T00:00:00Z"
    positions = {
        1: {"GREEDY": 1, "BACKTRACKING": 2, "CP_SAT": 3},
        2: {"BACKTRACKING": 1, "CP_SAT": 2, "GREEDY": 3},
        3: {"CP_SAT": 1, "GREEDY": 2, "BACKTRACKING": 3},
    }
    scores = {"GREEDY": 10, "BACKTRACKING": 19, "CP_SAT": 19}
    raw = []
    outcomes = []
    for repetition in range(1, 4):
        for algorithm_index, algorithm in enumerate(campaign.EXPECTED_ALGORITHMS):
            raw.append({
                "schemaVersion": 2, "benchmarkRunId": run_id, "generatedAt": generated,
                "profile": "BALANCED_SMALL", "seed": 42, "scenarioFingerprint": fingerprint,
                "repetition": repetition, "algorithm": algorithm,
                "executionOrderPosition": positions[repetition][algorithm], "resourceCount": 2,
                "requestCount": 1, "backtrackingTimeLimitMs": 500, "cpSatTimeLimitSeconds": "1.000000",
                "totalPriorityScore": scores[algorithm], "allocatedRequests": 1, "rejectedRequests": 0,
                "measuredExecutionTimeMs": f"{repetition + algorithm_index / 10:.6f}",
                "algorithmExecutionTimeMs": repetition, "exploredStates": 0, "stoppedByLimit": "false",
                "algorithmStatus": "OPTIMAL" if algorithm == "CP_SAT" else "", "objectiveValue": "19.000000",
            })
            outcomes.append({
                "schemaVersion": 1, "benchmarkRunId": run_id, "generatedAt": generated,
                "profile": "BALANCED_SMALL", "seed": 42, "scenarioFingerprint": fingerprint,
                "repetition": repetition, "algorithm": algorithm,
                "executionOrderPosition": positions[repetition][algorithm], "requestId": "REQ-1",
                "requestName": "Request", "requestPriority": 10, "requestStart": "2026-01-01T10:00",
                "requestEnd": "2026-01-01T11:00", "outcome": "ACCEPTED", "assignedResourceIds": "R-1",
                "assignedResourceNames": "Room", "rejectionReason": "",
            })
    summary = []
    for algorithm in campaign.EXPECTED_ALGORITHMS:
        group = [row for row in raw if row["algorithm"] == algorithm]
        times = [float(row["measuredExecutionTimeMs"]) for row in group]
        score_values = [int(row["totalPriorityScore"]) for row in group]
        summary.append({
            "schemaVersion": 2, "benchmarkRunId": run_id, "generatedAt": generated,
            "profile": "BALANCED_SMALL", "seed": 42, "scenarioFingerprint": fingerprint,
            "algorithm": algorithm, "resourceCount": 2, "requestCount": 1, "measuredRuns": 3,
            "averageMeasuredExecutionTimeMs": f"{statistics.fmean(times):.6f}",
            "medianMeasuredExecutionTimeMs": f"{statistics.median(times):.6f}",
            "minimumMeasuredExecutionTimeMs": f"{min(times):.6f}",
            "maximumMeasuredExecutionTimeMs": f"{max(times):.6f}",
            "averageTotalPriorityScore": f"{statistics.fmean(score_values):.6f}",
            "bestTotalPriorityScore": max(score_values), "worstTotalPriorityScore": min(score_values),
            "averageAllocatedRequests": "1.000000", "stoppedByLimitRuns": 0,
            "optimalCpSatRuns": 3 if algorithm == "CP_SAT" else 0,
        })
    return raw, summary, outcomes


def write_fixture(output: Path, *, commit: str = "abc1234") -> dict:
    output.mkdir(parents=True)
    raw, summary, outcomes = fixture_rows()
    write_csv(output / "raw-results.csv", RAW_HEADER, raw)
    write_csv(output / "summary-results.csv", SUMMARY_HEADER, summary)
    write_csv(output / "request-outcomes.csv", OUTCOME_HEADER, outcomes)
    (output / "scenario-snapshots.json").write_text(json.dumps({
        "schemaVersion": 1,
        "scenarios": [{"profile": "BALANCED_SMALL", "seed": 42, "scenarioFingerprint": "a" * 64,
                       "resources": [], "requests": []}],
    }), encoding="utf-8")
    files = {"rawResults": output / "raw-results.csv", "summaryResults": output / "summary-results.csv",
             "requestOutcomes": output / "request-outcomes.csv", "scenarioSnapshots": output / "scenario-snapshots.json",
             "metadata": output / "metadata.json"}
    metadata = {
        "schemaVersion": 3, "benchmarkRunId": "run-fixture", "sourceCommit": commit,
        "configuration": {"warmupRuns": 1, "measuredRuns": 3, "backtrackingTimeLimitMs": 500,
                          "cpSatTimeLimitSeconds": 1.0, "outputDirectory": str(output.resolve()),
                          "scaleResourceCount": 20, "scaleRequestCount": 20, "scaleResourceTypeCount": 3,
                          "overwrite": False},
        "profiles": ["BALANCED_SMALL"], "seeds": [42], "algorithms": list(campaign.EXPECTED_ALGORITHMS),
        "files": {key: str(path.resolve()) for key, path in files.items()},
    }
    files["metadata"].write_text(json.dumps(metadata), encoding="utf-8")
    return metadata


class CampaignPlanTest(unittest.TestCase):
    def test_loads_all_presets(self) -> None:
        plan = campaign.load_plan(MODULE_PATH.with_name("benchmark-campaign-plan.json"))
        for preset, expected in campaign.EXPECTED_PRESET_IDS.items():
            self.assertEqual(expected, [item["experimentId"] for item in campaign.selected_experiments(plan, preset)])

    def test_rejects_duplicate_experiment_id(self) -> None:
        plan = campaign.load_plan(MODULE_PATH.with_name("benchmark-campaign-plan.json"))
        plan["experiments"].append(copy.deepcopy(plan["experiments"][0]))
        with self.assertRaisesRegex(ValueError, "Duplicate experiment definition|Duplicate experiment ID"):
            campaign.validate_plan(plan)

    def test_rejects_measured_runs_not_divisible_by_three(self) -> None:
        item = experiment(); item["measuredRuns"] = 4
        with self.assertRaisesRegex(ValueError, "divisible by three"):
            campaign.validate_experiment(item)

    def test_rejects_incomplete_scale_trio(self) -> None:
        item = experiment(); item.update(profiles="SCALE", scaleResources=10)
        with self.assertRaisesRegex(ValueError, "incomplete SCALE"):
            campaign.validate_experiment(item)

    def test_rejects_blank_id_and_duplicate_profile_or_seed(self) -> None:
        item = experiment(); item["experimentId"] = " "
        with self.assertRaisesRegex(ValueError, "blank experiment ID"):
            campaign.validate_experiment(item)
        item = experiment(); item["profiles"] = "BALANCED_SMALL,BALANCED_SMALL"
        with self.assertRaisesRegex(ValueError, "duplicate profile"):
            campaign.validate_experiment(item)
        item = experiment(); item["seeds"] = "42,42"
        with self.assertRaisesRegex(ValueError, "duplicate seed"):
            campaign.validate_experiment(item)

    def test_rejects_non_finite_limit_and_scale_values_on_non_scale_profile(self) -> None:
        item = experiment(); item["cpSatTimeLimitSeconds"] = float("inf")
        with self.assertRaisesRegex(ValueError, "CP-SAT limit"):
            campaign.validate_experiment(item)
        item = experiment(); item.update(scaleResources=10, scaleRequests=10, scaleResourceTypes=3)
        with self.assertRaisesRegex(ValueError, "non-SCALE"):
            campaign.validate_experiment(item)

    def test_rejects_duplicate_output_directories(self) -> None:
        first = experiment()
        second = copy.deepcopy(first)
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "duplicate output directory"):
                campaign.validate_output_directories([first, second], Path(directory))

    def test_maven_command_quotes_output_with_spaces_and_shell_characters(self) -> None:
        output = Path("campaign output") / "smoke (one) & 'quoted'"
        command = campaign.maven_command(experiment(), output, "abc1234", False)
        tokens = shlex.split(command)
        exec_property = next(token for token in tokens if token.startswith("-Dexec.args="))
        self.assertIn(f'--output "{output}"', exec_property)
        self.assertNotIn("--overwrite", command)
        self.assertIn("-Dbenchmark.sourceCommit=abc1234", tokens)

    def test_overwrite_is_only_added_when_requested(self) -> None:
        self.assertNotIn("--overwrite", campaign.benchmark_arguments(experiment(), Path("out"), False))
        self.assertIn("--overwrite", campaign.benchmark_arguments(experiment(), Path("out"), True))

    def test_every_planned_maven_command_contains_source_commit(self) -> None:
        plan = campaign.load_plan(MODULE_PATH.with_name("benchmark-campaign-plan.json"))
        for item in campaign.selected_experiments(plan, "extended"):
            self.assertIn("-Dbenchmark.sourceCommit=abc1234",
                          campaign.maven_command(item, Path("out") / item["experimentId"], "abc1234", False))


class CampaignOutputValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.output = Path(self.temporary.name) / "campaign output" / "fixture"
        write_fixture(self.output)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def rewrite(self, name: str, header: list[str], mutator) -> None:
        rows = campaign.read_csv(self.output / name)
        mutator(rows)
        write_csv(self.output / name, header, rows)

    def test_valid_small_fixture(self) -> None:
        result = campaign.validate_output(experiment(), self.output, "abc1234", False)
        self.assertEqual(9, result["rawRowCount"])
        self.assertEqual(3, result["summaryRowCount"])
        self.assertEqual(9, result["requestOutcomeRowCount"])

    def test_rejects_missing_expected_output_file(self) -> None:
        (self.output / "metadata.json").unlink()
        with self.assertRaisesRegex(ValueError, "Missing benchmark output file"):
            campaign.validate_output(experiment(), self.output, "abc1234", False)

    def test_rejects_wrong_profile(self) -> None:
        self.rewrite("raw-results.csv", RAW_HEADER, lambda rows: rows[0].update(profile="WRONG"))
        with self.assertRaisesRegex(ValueError, "profile"):
            campaign.validate_output(experiment(), self.output, "abc1234", False)

    def test_rejects_wrong_seed(self) -> None:
        self.rewrite("raw-results.csv", RAW_HEADER, lambda rows: rows[0].update(seed="43"))
        with self.assertRaisesRegex(ValueError, "seed"):
            campaign.validate_output(experiment(), self.output, "abc1234", False)

    def test_rejects_wrong_time_limit(self) -> None:
        self.rewrite("raw-results.csv", RAW_HEADER, lambda rows: rows[0].update(backtrackingTimeLimitMs="501"))
        with self.assertRaisesRegex(ValueError, "Backtracking limit"):
            campaign.validate_output(experiment(), self.output, "abc1234", False)

    def test_rejects_missing_repetition(self) -> None:
        self.rewrite("raw-results.csv", RAW_HEADER, lambda rows: rows.pop())
        with self.assertRaisesRegex(ValueError, "tuple|set mismatch|row count"):
            campaign.validate_output(experiment(), self.output, "abc1234", False)

    def test_rejects_unbalanced_execution_position(self) -> None:
        self.rewrite("raw-results.csv", RAW_HEADER, lambda rows: rows[0].update(executionOrderPosition="2"))
        with self.assertRaisesRegex(ValueError, "executionOrderPosition"):
            campaign.validate_output(experiment(), self.output, "abc1234", False)

    def test_rejects_metadata_mismatch(self) -> None:
        metadata = campaign.read_json(self.output / "metadata.json")
        metadata["profiles"] = ["CONFLICT_HEAVY"]
        (self.output / "metadata.json").write_text(json.dumps(metadata), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Metadata profile"):
            campaign.validate_output(experiment(), self.output, "abc1234", False)

    def test_rejects_outcome_execution_position_mismatch(self) -> None:
        self.rewrite("request-outcomes.csv", OUTCOME_HEADER,
                     lambda rows: rows[0].update(executionOrderPosition="3"))
        with self.assertRaisesRegex(ValueError, "Outcome executionOrderPosition"):
            campaign.validate_output(experiment(), self.output, "abc1234", False)

    def test_rejects_summary_aggregate_mismatch(self) -> None:
        self.rewrite("summary-results.csv", SUMMARY_HEADER,
                     lambda rows: rows[0].update(averageTotalPriorityScore="999.000000"))
        with self.assertRaisesRegex(ValueError, "averageTotalPriorityScore"):
            campaign.validate_output(experiment(), self.output, "abc1234", False)


class CampaignManifestAndCombinedCsvTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name) / "campaign root"
        self.output = self.root / "fixture"
        write_fixture(self.output)
        self.validation = campaign.validate_output(experiment(), self.output, "abc1234", False)
        self.manifest = campaign.create_manifest([experiment()], "abc1234", "smoke", self.root,
                                                 Path(self.temporary.name), True, "Java 17", "Maven 3", "Test OS", False)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_failed_manifest_keeps_exit_codes_and_error(self) -> None:
        campaign.update_manifest_experiment(self.manifest, "fixture", "FAILED", 0, 7, "validation failed")
        self.assertEqual("FAILED", self.manifest["campaignStatus"])
        record = self.manifest["experiments"][0]
        self.assertEqual(0, record["benchmarkExitCode"])
        self.assertEqual(7, record["validationExitCode"])
        self.assertEqual("validation failed", record["errorMessage"])

    def test_completed_manifest_and_combined_csv(self) -> None:
        campaign.update_manifest_experiment(self.manifest, "fixture", "COMPLETED", 0, 0,
                                            validation=self.validation)
        manifest_path = self.root / "campaign-manifest.json"
        campaign.finalize_manifest(self.manifest, manifest_path)
        saved = campaign.read_json(manifest_path)
        self.assertEqual("COMPLETED", saved["campaignStatus"])
        self.assertIsNotNone(saved["completedAt"])
        for key, name in campaign.COMBINED_FILES.items():
            combined = self.root / name
            self.assertTrue(combined.is_file())
            rows = campaign.read_csv(combined)
            self.assertEqual("experimentId", campaign.csv_header(combined)[0])
            self.assertEqual("resultDirectory", campaign.csv_header(combined)[1])
            expected = {"rawResults": 9, "summaryResults": 3, "requestOutcomes": 9}[key]
            self.assertEqual(expected, len(rows))


if __name__ == "__main__":
    unittest.main()
