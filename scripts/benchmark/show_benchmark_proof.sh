#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/../.." && pwd)"
python_bin="${PYTHON_BIN:-/Users/shauryarawat/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3}"
checkov_bin="${CHECKOV_BIN:-/private/tmp/codeshield-checkov-venv/bin/checkov}"
dataset="$project_dir/outputs/codeshield-deliverables/CodeShield_Security_Drift_Benchmark_Dataset_Expanded.xlsx"
output_dir="$project_dir/outputs/codeshield-deliverables/benchmark-comparison"

if [[ ! -x "$python_bin" ]]; then
  echo "Python with openpyxl was not found: $python_bin" >&2
  echo "Set PYTHON_BIN to a Python interpreter that has openpyxl installed." >&2
  exit 1
fi

if [[ ! -x "$checkov_bin" ]]; then
  echo "Checkov was not found: $checkov_bin" >&2
  echo "Set CHECKOV_BIN to the installed Checkov executable." >&2
  exit 1
fi

echo "Running the CodeShield and Checkov benchmark..."
"$python_bin" "$project_dir/scripts/benchmark/run_checkov_comparison.py" \
  --dataset "$dataset" \
  --project-root "$project_dir" \
  --checkov "$checkov_bin" \
  --output-dir "$output_dir" \
  --presentation
