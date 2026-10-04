#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir "$tmp/bin"
export CURL_LOG="$tmp/curl.log" DOCKER_LOG="$tmp/docker.log"

cat >"$tmp/bin/curl" <<'CURL'
#!/usr/bin/env bash
method=unknown connect=unknown max=unknown auth=none argv_token=absent url_path=unknown
while (($#)); do
  if [[ -n ${EXPECTED_BEARER_TOKEN:-} && $1 == *"$EXPECTED_BEARER_TOKEN"* ]]; then
    argv_token=present
  fi
  case $1 in
    --request) method=$2; shift ;;
    --connect-timeout) connect=$2; shift ;;
    --max-time) max=$2; shift ;;
    --header)
      case ${2-} in
        @-)
          IFS= read -r header || true
          if [[ $header == "Authorization: Bearer ${EXPECTED_BEARER_TOKEN:-}" && -n ${EXPECTED_BEARER_TOKEN:-} ]]; then
            auth=match
          else
            auth=other
          fi
          ;;
        "Authorization: Bearer ${EXPECTED_BEARER_TOKEN:-}")
          [[ -n ${EXPECTED_BEARER_TOKEN:-} ]] && auth=match || auth=other
          ;;
        Authorization:*) auth=other ;;
      esac
      shift
      ;;
    http://*|https://*)
      authority=${1#*://}
      if [[ $authority == */* ]]; then url_path=/${authority#*/}; else url_path=/; fi
      url_path=${url_path%%\?*}
      ;;
  esac
  shift
done
printf 'method=%s connect-timeout=%s max-time=%s auth=%s argv-token=%s url-path=%s\n' \
  "$method" "$connect" "$max" "$auth" "$argv_token" "$url_path" >>"$CURL_LOG"
printf '%s' "${FAKE_CURL_STATUS:-200}"
exit "${FAKE_CURL_EXIT:-0}"
CURL
cat >"$tmp/bin/docker" <<'DOCKER'
#!/bin/sh
printf '%s\n' "$*" >>"$DOCKER_LOG"
exit 99
DOCKER
chmod +x "$tmp/bin/curl" "$tmp/bin/docker"
export PATH="$tmp/bin:$PATH"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
run() { env -u SMOKE_BASE_URL -u SMOKE_HEALTH_URL -u SMOKE_OPENAPI_URL -u SMOKE_BEARER_TOKEN "$@"; }

set +e
run bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1
status=$?
set -e
[[ $status == 2 ]] || fail "missing SMOKE_BASE_URL returned $status, expected 2"
[[ "$(<"$tmp/out")" == *'SMOKE_BASE_URL is required'* ]] || fail 'missing base URL error was unclear'
[[ ! -s "$CURL_LOG" ]] || fail 'missing base URL made a request'

if SMOKE_BASE_URL='ftp://example.invalid' bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1; then
  fail 'invalid SMOKE_BASE_URL was accepted'
fi
[[ ! -s "$CURL_LOG" ]] || fail 'invalid base URL made a request'

if SMOKE_BASE_URL='https:///missing-host' bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1; then
  fail 'base URL without a host was accepted'
fi
query_marker="sensitive-fixture-$$"
if SMOKE_BASE_URL='https://gateway.example.test' SMOKE_HEALTH_URL="http://bad host/ready?$query_marker" \
  bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1; then
  fail 'invalid health override was accepted'
fi
[[ ! -s "$CURL_LOG" ]] || fail 'invalid endpoint URL made a request'
[[ "$(<"$tmp/out")" != *"$query_marker"* ]] || fail 'invalid URL query appeared in output'
if SMOKE_BASE_URL='https://gateway.example.test' SMOKE_OPENAPI_URL='http://bad host/docs' \
  bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1; then
  fail 'invalid OpenAPI override was accepted'
fi
[[ ! -s "$CURL_LOG" ]] || fail 'invalid OpenAPI URL made a request'
if grep -Eiq 'docker[[:space:]]+compose[[:space:]]+(up|down|restart|stop|kill)|rm[[:space:]]+-rf' \
  "$root/scripts/smoke-release.sh"; then
  fail 'smoke script contains destructive lifecycle or data-deletion commands'
fi

: >"$CURL_LOG"
SMOKE_BASE_URL='https://gateway.example.test/root/' \
  bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1 || fail 'default probes failed'
[[ $(wc -l <"$CURL_LOG" | tr -d ' ') == 2 ]] || fail 'expected exactly two default probes'
[[ $(grep -c 'url-path=/root/actuator/health' "$CURL_LOG") == 1 ]] || fail 'default health URL was wrong'
[[ $(grep -c 'url-path=/root/v3/api-docs/swagger-config' "$CURL_LOG") == 1 ]] || fail 'default OpenAPI URL was wrong'
[[ "$(<"$tmp/out")" == *'HTTP health 200'* ]] || fail 'successful health status was not recorded'
[[ "$(<"$tmp/out")" == *'HTTP OpenAPI 200'* ]] || fail 'successful OpenAPI status was not recorded'
[[ "$(<"$tmp/out")" != *'gateway.example.test'* ]] || fail 'configured URL appeared in smoke output'

: >"$CURL_LOG"
SMOKE_BASE_URL='https://gateway.example.test/root/' \
  SMOKE_HEALTH_URL='http://health.example.test/ready' \
  SMOKE_OPENAPI_URL='https://gateway.example.test/v3/api-docs/swagger-config' \
  bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1 || fail 'successful probes failed'
[[ $(wc -l <"$CURL_LOG" | tr -d ' ') == 2 ]] || fail 'expected exactly two probes'
[[ $(grep -c 'method=GET' "$CURL_LOG") == 2 ]] || fail 'probe method was not GET'
[[ $(grep -c 'connect-timeout=5' "$CURL_LOG") == 2 ]] || fail 'connection timeout is not bounded'
[[ $(grep -c 'max-time=15' "$CURL_LOG") == 2 ]] || fail 'total timeout is not bounded'
[[ $(grep -c 'auth=none' "$CURL_LOG") == 2 ]] || fail 'unauthenticated probes unexpectedly sent Authorization'
[[ $(grep -c 'url-path=/ready' "$CURL_LOG") == 1 ]] || fail 'health override was not used'
[[ $(grep -c 'url-path=/v3/api-docs/swagger-config' "$CURL_LOG") == 1 ]] || fail 'OpenAPI override was not used'
for output_line in 'GET health' 'HTTP health 200' 'GET OpenAPI' 'HTTP OpenAPI 200' 'PASS: release health and OpenAPI probes succeeded'; do
  [[ $(grep -Fc "$output_line" "$tmp/out") == 1 ]] || fail "missing concise smoke output: $output_line"
done

fixture_token="fixture-$$-${RANDOM}"
: >"$CURL_LOG"
EXPECTED_BEARER_TOKEN="$fixture_token" SMOKE_BEARER_TOKEN="$fixture_token" \
  SMOKE_BASE_URL='https://gateway.example.test' bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1 ||
  fail 'authenticated probes failed'
[[ $(grep -c 'auth=match' "$CURL_LOG") == 2 ]] || fail 'bearer header was not sent to both probes'
[[ $(grep -c 'argv-token=absent' "$CURL_LOG") == 2 ]] || fail 'bearer token appeared in curl argv'
[[ "$(<"$tmp/out")" != *"$fixture_token"* ]] || fail 'bearer token appeared in smoke output'
[[ "$(<"$CURL_LOG")" != *"$fixture_token"* ]] || fail 'bearer token appeared in curl log'

query_marker="successful-query-fixture-$$"
: >"$CURL_LOG"
SMOKE_BASE_URL="https://gateway.example.test/root?$query_marker" \
  bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1 || fail 'configured query probes failed'
[[ "$(<"$tmp/out")" != *"$query_marker"* ]] || fail 'configured URL query appeared in smoke output'
[[ "$(<"$CURL_LOG")" != *"$query_marker"* ]] || fail 'configured URL query appeared in curl log'

for invalid_token in $'fixture\rvalue' $'fixture\nvalue'; do
  : >"$CURL_LOG"
  if SMOKE_BEARER_TOKEN="$invalid_token" SMOKE_BASE_URL='https://gateway.example.test' \
    bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1; then
    fail 'CR/LF bearer token was accepted'
  fi
  [[ ! -s "$CURL_LOG" ]] || fail 'CR/LF bearer token made a request'
  [[ "$(<"$tmp/out")" != *"$invalid_token"* ]] || fail 'invalid bearer token appeared in smoke output'
done

for curl_exit in 22 6 7; do
  : >"$CURL_LOG"
  if FAKE_CURL_EXIT="$curl_exit" SMOKE_BASE_URL='https://gateway.example.test' \
    bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1; then
    fail "curl exit $curl_exit was accepted"
  fi
  [[ "$(<"$tmp/out")" == *'probe failed'* ]] || fail "curl exit $curl_exit lacked a clear failure"
done
query_marker="request-query-fixture-$$"
: >"$CURL_LOG"
if FAKE_CURL_EXIT=7 SMOKE_BASE_URL="https://gateway.example.test/root?$query_marker" \
  bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1; then
  fail 'request failure was accepted'
fi
[[ "$(<"$tmp/out")" != *"$query_marker"* ]] || fail 'request URL query appeared in output'
[[ "$(<"$CURL_LOG")" != *"$query_marker"* ]] || fail 'request URL query appeared in curl log'
for status_code in 302 503; do
  if FAKE_CURL_STATUS="$status_code" SMOKE_BASE_URL='https://gateway.example.test' \
    bash "$root/scripts/smoke-release.sh" >"$tmp/out" 2>&1; then
    fail "HTTP status $status_code was accepted"
  fi
  [[ "$(<"$tmp/out")" == *"HTTP health $status_code"* ]] || fail "HTTP status $status_code lacked a clear failure"
done

[[ ! -s "$DOCKER_LOG" ]] || fail 'smoke invoked Docker/Compose'
[[ ! -s "$CURL_LOG" || $(grep -Ec 'method=(GET|HEAD)' "$CURL_LOG") == $(wc -l <"$CURL_LOG" | tr -d ' ') ]] || fail 'probe used a method other than GET/HEAD'
printf 'PASS: smoke configuration, bounded read-only probes, failures, and no Docker invocation\n'
