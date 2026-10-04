#!/usr/bin/env bash
set -euo pipefail

# Exercise the shipped Nginx configuration with an isolated, replaceable gateway.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${1:-$(docker compose --project-directory "$ROOT" images -q frontend)}"
[[ -n "$IMAGE" ]] || { echo "Build the frontend image before running this check." >&2; exit 1; }
WORK="$(mktemp -d)"
PREFIX="ecommerce-proxy-test-$$"
cleanup() {
  docker rm -f "$PREFIX-frontend" "$PREFIX-gateway" "$PREFIX-hold" >/dev/null 2>&1 || true
  docker network rm "$PREFIX" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

python3 - "$ROOT" "$WORK" <<'PY'
from pathlib import Path
import sys
root, work = map(Path, sys.argv[1:])
source = (root / "frontend/ecommerce-app/Dockerfile").read_text()
config = source.split("COPY <<'EOF' /etc/nginx/conf.d/default.conf\n", 1)[1].split("\nEOF", 1)[0]
(work / "frontend.conf").write_text(config)
PY
cat > "$WORK/gateway.conf" <<'EOF'
server {
    listen 8080;
    location / { return 200 "$request_method $request_uri\n"; }
}
EOF

docker network create "$PREFIX" >/dev/null
start_gateway() {
  docker run -d --name "$PREFIX-gateway" --network "$PREFIX" --network-alias api-gateway \
    -v "$WORK/gateway.conf:/etc/nginx/conf.d/default.conf:ro" "$IMAGE" >/dev/null
}
start_gateway
docker run -d --name "$PREFIX-frontend" --network "$PREFIX" \
  -v "$WORK/frontend.conf:/etc/nginx/conf.d/default.conf:ro" "$IMAGE" >/dev/null

expect_get() {
  local path="$1" expected="$2" actual
  for attempt in {1..20}; do
    actual="$(docker exec "$PREFIX-frontend" wget -T 2 -qO- "http://127.0.0.1:8080$path" 2>/dev/null || true)"
    [[ "$actual" == "$expected" ]] && return 0
    sleep 1
  done
  echo "Expected '$expected', received '$actual' for $path" >&2
  return 1
}

expect_get /health ok
expect_get '/api/v1/products?page=0&size=8' 'GET /api/v1/products?page=0&size=8'
expect_get /api/v1/categories 'GET /api/v1/categories'
actual="$(docker exec "$PREFIX-frontend" wget -T 2 -qO- --post-data='grant_type=refresh_token' \
  http://127.0.0.1:8080/api/auth/token)"
[[ "$actual" == 'POST /api/auth/token' ]]

# Occupy the former address so the replacement must receive a different one.
OLD_IP="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$PREFIX-gateway")"
docker rm -f "$PREFIX-gateway" >/dev/null
docker run -d --name "$PREFIX-hold" --network "$PREFIX" --ip "$OLD_IP" \
  --entrypoint sh "$IMAGE" -c 'sleep 60' >/dev/null
start_gateway
NEW_IP="$(docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$PREFIX-gateway")"
[[ "$NEW_IP" != "$OLD_IP" ]]
echo "Gateway address changed: $OLD_IP -> $NEW_IP"
expect_get '/api/v1/products?page=0&size=8' 'GET /api/v1/products?page=0&size=8'
echo "PASS: health, API paths/query strings, POST, and gateway address replacement"
