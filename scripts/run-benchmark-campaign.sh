#!/usr/bin/env bash

set -euo pipefail

source_commit=""
output_root=""
preset="standard"
overwrite=false
skip_tests=false
allow_dirty=false
dry_run=false

usage() {
  cat <<'EOF'
Usage: scripts/run-benchmark-campaign.sh [options]
  --source-commit <sha>       Commit to record; defaults to current HEAD
  --output-root <directory>   Campaign output root
  --preset <name>             smoke, standard, or extended (default: standard)
  --overwrite                 Replace the five benchmark files in each experiment
  --skip-tests                Skip the initial mvn test
  --allow-dirty-working-tree  Permit local verification with uncommitted changes
  --dry-run                   Validate and print the campaign plan without running Maven
  --help                      Show this help
EOF
}

while (($#)); do
  case "$1" in
    --source-commit) source_commit="${2:-}"; shift 2 ;;
    --output-root) output_root="${2:-}"; shift 2 ;;
    --preset) preset="${2:-}"; shift 2 ;;
    --overwrite) overwrite=true; shift ;;
    --skip-tests) skip_tests=true; shift ;;
    --allow-dirty-working-tree) allow_dirty=true; shift ;;
    --dry-run) dry_run=true; shift ;;
    --help) usage; exit 0 ;;
    *) echo "Error: Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$preset" in
  smoke|standard|extended) ;;
  *) echo "Error: Unknown preset: $preset" >&2; exit 2 ;;
esac

for command in git mvn python3; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Error: Required command is not available: $command" >&2
    exit 1
  }
done

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$script_dir/.." && pwd)"
plan_path="$script_dir/benchmark-campaign-plan.json"
helper_path="$script_dir/benchmark_campaign_files.py"

[[ -f "$repository_root/pom.xml" ]] || { echo "Error: Root pom.xml was not found." >&2; exit 1; }
[[ -d "$repository_root/allocation-core" ]] || { echo "Error: allocation-core module was not found." >&2; exit 1; }
[[ -f "$plan_path" && -f "$helper_path" ]] || { echo "Error: Campaign support files were not found." >&2; exit 1; }

git_root="$(cd "$(git -C "$repository_root" rev-parse --show-toplevel)" && pwd)"
[[ "$git_root" == "$repository_root" ]] || { echo "Error: Script is not inside the AllocationLibrary repository." >&2; exit 1; }
head_commit="$(git -C "$repository_root" rev-parse HEAD)"
[[ -n "$source_commit" ]] || source_commit="$head_commit"
[[ "$source_commit" =~ ^[0-9a-fA-F]{7,64}$ ]] || { echo "Error: source commit must be a valid Git hash." >&2; exit 1; }
source_commit="$(git -C "$repository_root" rev-parse --verify "${source_commit}^{commit}")"
[[ "$source_commit" == "$head_commit" ]] || { echo "Error: source commit must match current HEAD." >&2; exit 1; }
short_commit="${source_commit:0:12}"

working_tree_clean=true
if [[ -n "$(git -C "$repository_root" status --porcelain --untracked-files=normal)" ]]; then
  working_tree_clean=false
fi
if [[ "$allow_dirty" == false && "$working_tree_clean" == false ]]; then
  echo "Error: Git working tree must be clean. Use --allow-dirty-working-tree only for intentional local verification." >&2
  exit 1
fi

[[ -n "$output_root" ]] || output_root="benchmark-results/campaigns/$short_commit"
if [[ "$output_root" != /* ]]; then
  output_root="$repository_root/$output_root"
fi
if [[ -e "$output_root" && "$overwrite" == false ]]; then
  echo "Error: Campaign output already exists: $output_root. Choose another --output-root or use --overwrite." >&2
  exit 1
fi

plan_output="$(python3 "$helper_path" plan --plan "$plan_path" --preset "$preset")"
experiments=()
while IFS= read -r line; do
  experiments+=("$line")
done <<<"$plan_output"
(( ${#experiments[@]} > 0 )) || { echo "Error: Campaign plan is empty." >&2; exit 1; }
seen_ids="|"
seen_outputs="|"

print_command() {
  local id="$1" profiles="$2" seeds="$3" warmups="$4" runs="$5" bt="$6" cp="$7"
  local resources="$8" requests="$9" resource_types="${10}" experiment_output="${11}"
  local args="--profile $profiles --seed $seeds --warmups $warmups --runs $runs --backtracking-limit-ms $bt --cp-sat-limit-seconds $cp --output $experiment_output"
  if [[ -n "$resources" ]]; then
    args+=" --resources $resources --requests $requests --resource-types $resource_types"
  fi
  [[ "$overwrite" == false ]] || args+=" --overwrite"
  printf 'mvn -pl allocation-core exec:java "-Dbenchmark.sourceCommit=%s" "-Dexec.args=%s"\n' "$source_commit" "$args"
}

for line in "${experiments[@]}"; do
  line="${line%$'\r'}"
  IFS='|' read -r id profiles seeds warmups runs bt cp resources requests resource_types <<<"$line"
  [[ "$seen_ids" != *"|$id|"* ]] || { echo "Error: Duplicate experiment ID: $id" >&2; exit 1; }
  seen_ids+="$id|"
  ((runs % 3 == 0)) || { echo "Error: measured runs must be divisible by three: $id" >&2; exit 1; }
  experiment_output="$output_root/$id"
  [[ "$seen_outputs" != *"|$experiment_output|"* ]] || { echo "Error: Duplicate output directory: $experiment_output" >&2; exit 1; }
  seen_outputs+="$experiment_output|"
done

if [[ "$dry_run" == true ]]; then
  echo "Preset: $preset"
  echo "Source commit: $source_commit"
  echo "Output root: $output_root"
  [[ "$skip_tests" == true ]] || echo "mvn test"
  echo "mvn -pl allocation-core -am package"
  for line in "${experiments[@]}"; do
    line="${line%$'\r'}"
    IFS='|' read -r id profiles seeds warmups runs bt cp resources requests resource_types <<<"$line"
    print_command "$id" "$profiles" "$seeds" "$warmups" "$runs" "$bt" "$cp" "$resources" "$requests" "$resource_types" "$output_root/$id"
  done
  exit 0
fi

cd "$repository_root"
if [[ "$skip_tests" == false ]]; then
  mvn test
fi
mvn -pl allocation-core -am package
mkdir -p "$output_root"

java_version="$(java -version 2>&1 | sed -n '1p' | tr -d '"')"
maven_version="$(mvn --version 2>&1 | sed -n '1p')"
operating_system="$(uname -a)"
completed_ids=()

write_failed_manifest() {
  local failed_id="$1" exit_code="$2" error_message="$3"
  local arguments=(
    finalize --plan "$plan_path" --preset "$preset" --source-commit "$source_commit"
    --output-root "$output_root" --repository-root "$repository_root"
    --java-version "$java_version" --maven-version "$maven_version"
    --operating-system "$operating_system" --failed-id "$failed_id"
    --exit-code "$exit_code" --error-message "$error_message"
  )
  arguments+=(--working-tree-clean "$working_tree_clean")
  [[ "$overwrite" == false ]] || arguments+=(--overwrite)
  for completed_id in "${completed_ids[@]}"; do arguments+=(--completed-id "$completed_id"); done
  python3 "$helper_path" "${arguments[@]}"
}

for line in "${experiments[@]}"; do
  line="${line%$'\r'}"
  IFS='|' read -r id profiles seeds warmups runs bt cp resources requests resource_types <<<"$line"
  experiment_output="$output_root/$id"
  benchmark_args="--profile $profiles --seed $seeds --warmups $warmups --runs $runs --backtracking-limit-ms $bt --cp-sat-limit-seconds $cp --output $experiment_output"
  if [[ -n "$resources" ]]; then
    benchmark_args+=" --resources $resources --requests $requests --resource-types $resource_types"
  fi
  [[ "$overwrite" == false ]] || benchmark_args+=" --overwrite"

  echo
  echo "==> Running experiment $id"
  set +e
  mvn -pl allocation-core exec:java "-Dbenchmark.sourceCommit=$source_commit" "-Dexec.args=$benchmark_args"
  exit_code=$?
  set -e
  if ((exit_code != 0)); then
    write_failed_manifest "$id" "$exit_code" "Benchmark command failed with exit code $exit_code."
    exit "$exit_code"
  fi

  set +e
  validation_output="$(python3 "$helper_path" validate --plan "$plan_path" --preset "$preset" --experiment-id "$id" --output-dir "$experiment_output" --source-commit "$source_commit" 2>&1)"
  validation_exit=$?
  set -e
  if ((validation_exit != 0)); then
    write_failed_manifest "$id" "$validation_exit" "$validation_output"
    echo "$validation_output" >&2
    exit "$validation_exit"
  fi
  completed_ids+=("$id")
done

finalize_args=(
  finalize --plan "$plan_path" --preset "$preset" --source-commit "$source_commit"
  --output-root "$output_root" --repository-root "$repository_root"
  --java-version "$java_version" --maven-version "$maven_version"
  --operating-system "$operating_system"
)
finalize_args+=(--working-tree-clean "$working_tree_clean")
[[ "$overwrite" == false ]] || finalize_args+=(--overwrite)
for completed_id in "${completed_ids[@]}"; do finalize_args+=(--completed-id "$completed_id"); done
python3 "$helper_path" "${finalize_args[@]}"

echo
echo "Campaign completed successfully."
echo "Manifest: $output_root/campaign-manifest.json"
echo "Combined raw results: $output_root/campaign-raw-results.csv"
echo "Combined summary: $output_root/campaign-summary.csv"
echo "Combined request outcomes: $output_root/campaign-request-outcomes.csv"
