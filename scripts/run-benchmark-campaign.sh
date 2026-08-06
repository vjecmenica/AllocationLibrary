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
  --overwrite                 Replace existing benchmark outputs
  --skip-tests                Skip the initial Maven test command
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

case "$preset" in smoke|standard|extended) ;; *) echo "Error: Unknown preset: $preset" >&2; exit 2 ;; esac
for command in git mvn java python3; do
  command -v "$command" >/dev/null 2>&1 || { echo "Error: Required command is not available: $command" >&2; exit 1; }
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
[[ -z "$(git -C "$repository_root" status --porcelain --untracked-files=normal)" ]] || working_tree_clean=false
if [[ "$allow_dirty" == false && "$working_tree_clean" == false ]]; then
  echo "Error: Git working tree must be clean. Use --allow-dirty-working-tree only for intentional local verification." >&2
  exit 1
fi

[[ -n "$output_root" ]] || output_root="benchmark-results/campaigns/$short_commit"
[[ "$output_root" == /* ]] || output_root="$repository_root/$output_root"
if [[ -e "$output_root" && "$overwrite" == false ]]; then
  echo "Error: Campaign output already exists: $output_root. Choose another --output-root or use --overwrite." >&2
  exit 1
fi

plan_output="$(python3 "$helper_path" plan --plan "$plan_path" --preset "$preset" --output-root "$output_root")"
experiments=()
while IFS= read -r line; do experiments+=("${line%$'\r'}"); done <<<"$plan_output"
(( ${#experiments[@]} > 0 )) || { echo "Error: Campaign plan is empty." >&2; exit 1; }

helper_overwrite=()
[[ "$overwrite" == false ]] || helper_overwrite=(--overwrite)

exec_args_for() {
  local id="$1" output="$2"
  python3 "$helper_path" exec-args --plan "$plan_path" --preset "$preset" \
    --experiment-id "$id" --output-dir "$output" "${helper_overwrite[@]}"
}

display_command_for() {
  local id="$1" output="$2"
  python3 "$helper_path" maven-command --plan "$plan_path" --preset "$preset" \
    --experiment-id "$id" --output-dir "$output" --source-commit "$source_commit" "${helper_overwrite[@]}"
}

if [[ "$dry_run" == true ]]; then
  echo "Preset: $preset"
  echo "Source commit: $source_commit"
  echo "Output root: $output_root"
  [[ "$skip_tests" == true ]] || echo "mvn -B -ntp test"
  echo "mvn -B -ntp -pl allocation-core -am package -DskipTests"
  for line in "${experiments[@]}"; do
    IFS='|' read -r id _ <<<"$line"
    display_command_for "$id" "$output_root/$id"
  done
  exit 0
fi

cd "$repository_root"
mkdir -p "$output_root"
manifest_path="$output_root/campaign-manifest.json"
java_version="$(java --version 2>&1 | sed -n '1p' | sed $'s/\033\[[0-9;]*m//g' | tr -d '"')"
maven_version="$(mvn --version 2>&1 | sed -n '1p' | sed $'s/\033\[[0-9;]*m//g')"
operating_system="$(uname -a)"

init_args=(manifest-init --plan "$plan_path" --preset "$preset" --source-commit "$source_commit"
  --output-root "$output_root" --repository-root "$repository_root" --working-tree-clean "$working_tree_clean"
  --java-version "$java_version" --maven-version "$maven_version" --operating-system "$operating_system"
  --manifest "$manifest_path")
[[ "$overwrite" == false ]] || init_args+=(--overwrite)
python3 "$helper_path" "${init_args[@]}"

fail_campaign() {
  local message="$1"
  python3 "$helper_path" manifest-fail --manifest "$manifest_path" --error-message "$message" || true
  echo "Error: $message" >&2
}

if [[ "$skip_tests" == false ]]; then
  if ! mvn -B -ntp test; then fail_campaign "Maven tests failed."; exit 1; fi
fi
if ! mvn -B -ntp -pl allocation-core -am package -DskipTests; then
  fail_campaign "Packaging allocation-core failed."
  exit 1
fi

for line in "${experiments[@]}"; do
  IFS='|' read -r id _ <<<"$line"
  experiment_output="$output_root/$id"
  benchmark_args="$(exec_args_for "$id" "$experiment_output")"
  python3 "$helper_path" manifest-update --manifest "$manifest_path" --experiment-id "$id" --status RUNNING
  echo
  echo "==> Running experiment $id"
  set +e
  mvn -pl allocation-core exec:java "-Dbenchmark.sourceCommit=$source_commit" "-Dexec.args=$benchmark_args"
  benchmark_exit=$?
  set -e
  if ((benchmark_exit != 0)); then
    message="Benchmark command failed with exit code $benchmark_exit."
    python3 "$helper_path" manifest-update --manifest "$manifest_path" --experiment-id "$id" --status FAILED \
      --benchmark-exit-code "$benchmark_exit" --error-message "$message"
    echo "Error: $message" >&2
    exit "$benchmark_exit"
  fi

  validation_file="$(mktemp)"
  validation_error="$(mktemp)"
  set +e
  python3 "$helper_path" validate --plan "$plan_path" --preset "$preset" --experiment-id "$id" \
    --output-dir "$experiment_output" --source-commit "$source_commit" "${helper_overwrite[@]}" \
    >"$validation_file" 2>"$validation_error"
  validation_exit=$?
  set -e
  if ((validation_exit != 0)); then
    message="$(cat "$validation_error")"
    python3 "$helper_path" manifest-update --manifest "$manifest_path" --experiment-id "$id" --status FAILED \
      --benchmark-exit-code 0 --validation-exit-code "$validation_exit" --error-message "$message"
    rm -f "$validation_file" "$validation_error"
    echo "$message" >&2
    exit "$validation_exit"
  fi
  python3 "$helper_path" manifest-update --manifest "$manifest_path" --experiment-id "$id" --status COMPLETED \
    --benchmark-exit-code 0 --validation-exit-code 0 --validation-file "$validation_file"
  rm -f "$validation_file" "$validation_error"
done

python3 "$helper_path" finalize --manifest "$manifest_path"
echo
echo "Campaign completed successfully."
echo "Manifest: $manifest_path"
echo "Combined raw results: $output_root/campaign-raw-results.csv"
echo "Combined summary: $output_root/campaign-summary.csv"
echo "Combined request outcomes: $output_root/campaign-request-outcomes.csv"
