#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
venv_path="$(mktemp -d "$repo_root/.openapi-venv.XXXXXX")"
report_dir="${OPENAPI_REPORT_DIR:-${TMPDIR:-/tmp}/ecommerce-openapi-execution}"
trap 'rm -rf -- "$venv_path"' EXIT
mkdir -p "$report_dir"
report_dir="$(cd "$report_dir" && pwd)"

python3 -m venv "$venv_path"
"$venv_path/bin/python" -m pip install --disable-pip-version-check -q -r "$repo_root/scripts/requirements-openapi.txt"
"$venv_path/bin/python" "$repo_root/scripts/bundle-openapi.py" --check
"$venv_path/bin/python" "$repo_root/scripts/test-openapi-bundler.py"

cd "$repo_root"
raw_report="$report_dir/spectral-openapi.raw.log"
stderr_report="$report_dir/spectral-openapi.stderr.log"
json_report="$report_dir/spectral-openapi.json"
spectral_status=0
npx --yes @stoplight/spectral-cli@6.17.0 lint swagger.yaml 'services/*/src/main/openapi/swagger.yaml' \
  -f json --fail-severity warn > "$raw_report" 2> "$stderr_report" || spectral_status=$?
normalizer_status=0
python3 "$repo_root/scripts/normalize-spectral-json.py" "$raw_report" "$json_report" || normalizer_status=$?
echo "Spectral JSON diagnostics: $report_dir/spectral-openapi.json"
echo "Raw Spectral output: $raw_report"
echo "Spectral stderr: $stderr_report"
bash "$repo_root/scripts/resolve-openapi-check-status.sh" "$spectral_status" "$normalizer_status"
