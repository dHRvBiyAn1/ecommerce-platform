#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

dockerfile = Path("frontend/ecommerce-app/Dockerfile").read_text()
compose = Path("docker-compose.yml").read_text().split("  frontend:\n", 1)[1].split("\n  # ----------", 1)[0]

assert "ARG VITE_STRIPE_PUBLISHABLE_KEY=" in dockerfile
assert 'VITE_STRIPE_PUBLISHABLE_KEY="$VITE_STRIPE_PUBLISHABLE_KEY" npm run build' in dockerfile
assert "VITE_STRIPE_PUBLISHABLE_KEY: ${VITE_STRIPE_PUBLISHABLE_KEY:-}" in compose
print("PASS: frontend Stripe publishable key reaches the Vite production build")
PY
