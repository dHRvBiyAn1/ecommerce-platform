#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 - "$ROOT" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(sys.argv[1])
pom = ET.parse(root / "pom.xml").getroot()
modules = {
    module.text.strip().removeprefix("services/")
    for module in pom.findall("{*}modules/{*}module")
}
expected_modules = {
    "common", "config-server", "discovery-server", "api-gateway", "auth-service",
    "product-service", "inventory-service", "order-service", "payment-service",
    "notification-service", "cart-service", "coupon-service",
}
expected = {
    "auth-service": "Path=/api/auth/**, /api/users/**, /api/user/**, /api/admin/**, /oauth2/**, /login/oauth2/**, /.well-known/**",
    "product-service": "Path=/api/v1/products/**, /api/v1/categories/**",
    "inventory-service": "Path=/api/v1/inventory/**",
    "order-service": "Path=/api/v1/orders/**",
    "payment-service": "Path=/api/v1/payments/**",
    "notification-service": "Path=/api/v1/notifications/**",
    "cart-service": "Path=/api/v1/cart/**",
    "coupon-service": "Path=/api/v1/coupons/**",
}

routes = {}
route = None
for line in (root / "config-repo/api-gateway.yml").read_text().splitlines():
    stripped = line.strip()
    if line.startswith("          - id: "):
        route = stripped.removeprefix("- id: ")
        routes[route] = {}
    elif route and line.startswith("            uri: "):
        routes[route]["uri"] = stripped.removeprefix("uri: ")
    elif route and line.startswith("              - Path="):
        routes[route]["predicate"] = stripped.removeprefix("- ")
    elif line and not line.startswith(" "):
        route = None

errors = []
if modules != expected_modules:
    errors.append(
        f"active Maven modules differ: missing={sorted(expected_modules - modules)}, "
        f"unexpected={sorted(modules - expected_modules)}"
    )

actual_ids = set(routes)
for route_id in sorted(actual_ids - expected.keys()):
    errors.append(f"unsupported route id: {route_id} -> {routes[route_id].get('uri', '<missing uri>')}")
for route_id in sorted(expected.keys() - actual_ids):
    errors.append(f"missing implemented route id: {route_id}")
for route_id, predicate in expected.items():
    if route_id not in routes:
        continue
    target = routes[route_id].get("uri")
    if target != f"lb://{route_id}":
        errors.append(f"{route_id} target changed: expected lb://{route_id}, found {target!r}")
    if routes[route_id].get("predicate") != predicate:
        errors.append(f"{route_id} predicate changed: expected {predicate!r}, found {routes[route_id].get('predicate')!r}")
    if route_id not in modules:
        errors.append(f"{route_id} route has no active Maven module")

if errors:
    print("Active service route check failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    sys.exit(1)

print(f"Active service routes verified: {len(expected)} routes, {len(modules)} Maven modules")
PY
