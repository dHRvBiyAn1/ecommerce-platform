#!/usr/bin/env bash
set -euo pipefail

spectral_status="${1:?Spectral status is required}"
normalizer_status="${2:?JSON normalizer status is required}"
if [[ ! "$spectral_status" =~ ^[0-9]+$ || ! "$normalizer_status" =~ ^[0-9]+$ ]]; then
  echo "statuses must be numeric exit codes" >&2
  exit 2
fi
if (( spectral_status != 0 )); then
  exit "$spectral_status"
fi
exit "$normalizer_status"
