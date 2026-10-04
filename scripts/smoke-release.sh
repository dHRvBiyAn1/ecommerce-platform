#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 2; }

valid_url() {
  local name=$1 url=$2
  [[ $url =~ ^https?://(\[[0-9A-Fa-f:.]+\]|[A-Za-z0-9.-]+)(:[0-9]{1,5})?([/?#][^[:space:]]*)?$ ]] ||
    fail "$name must be an absolute http(s) URL"
}

[[ -n ${SMOKE_BASE_URL:-} ]] || fail 'SMOKE_BASE_URL is required'
valid_url SMOKE_BASE_URL "$SMOKE_BASE_URL"

base=${SMOKE_BASE_URL%/}
health_url=${SMOKE_HEALTH_URL:-$base/actuator/health}
openapi_url=${SMOKE_OPENAPI_URL:-$base/v3/api-docs/swagger-config}
valid_url SMOKE_HEALTH_URL "$health_url"
valid_url SMOKE_OPENAPI_URL "$openapi_url"

bearer_token=${SMOKE_BEARER_TOKEN:-}
if [[ $bearer_token == *$'\r'* || $bearer_token == *$'\n'* ]]; then
  fail 'SMOKE_BEARER_TOKEN must not contain CR or LF'
fi

curl_get() {
  if [[ -n $bearer_token ]]; then
    printf 'Authorization: Bearer %s\n' "$bearer_token" |
      curl --fail --silent --connect-timeout 5 --max-time 15 \
        --request GET --output /dev/null --write-out '%{http_code}' --header @- "$1" 2>/dev/null
  else
    curl --fail --silent --connect-timeout 5 --max-time 15 \
      --request GET --output /dev/null --write-out '%{http_code}' "$1" 2>/dev/null
  fi
}

probe() {
  local name=$1 url=$2 status curl_status=0
  printf 'GET %s\n' "$name"
  status=$(curl_get "$url") || curl_status=$?
  [[ $status =~ ^[0-9]{3}$ ]] || status=000
  printf 'HTTP %s %s\n' "$name" "$status"
  if ((curl_status != 0)) || [[ ! $status =~ ^2[0-9]{2}$ ]]; then
    printf 'ERROR: %s probe failed\n' "$name" >&2
    return 1
  fi
}

probe health "$health_url"
probe OpenAPI "$openapi_url"
printf 'PASS: release health and OpenAPI probes succeeded\n'
