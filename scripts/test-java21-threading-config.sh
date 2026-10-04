#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 - "$repo_root" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
services = (
    "api-gateway", "auth-service", "cart-service", "config-server",
    "coupon-service", "discovery-server", "inventory-service",
    "notification-service", "order-service", "payment-service", "product-service",
)
expected = "${SPRING_THREADS_VIRTUAL_ENABLED:false}"
errors = []

def require(condition, message):
    if not condition:
        errors.append(message)

for service in services:
    path = root / "services" / service / "src/main/resources/application.yml"
    text = path.read_text() if path.exists() else ""
    require(bool(text), f"{path.relative_to(root)} is missing")
    require(re.search(r"(?m)^\s+threads:\s*$", text) is not None, f"{service}: missing spring.threads")
    require(re.search(r"(?m)^\s+virtual:\s*$", text) is not None, f"{service}: missing spring.threads.virtual")
    require(re.search(r"(?m)^\s+enabled:\s*" + re.escape(expected) + r"\s*$", text) is not None,
            f"{service}: virtual threads must default to false from SPRING_THREADS_VIRTUAL_ENABLED")
    require(re.search(r"(?m)^\s+keep-alive:\s*true\s*$", text) is not None,
            f"{service}: spring.main.keep-alive must be true")
    for profile in path.parent.glob("application-*.yml"):
        require("spring.threads.virtual.enabled" not in profile.read_text(),
                f"{profile.relative_to(root)} unexpectedly overrides the threading toggle")

central = (root / "config-repo/application.yml").read_text()
require(re.search(r"(?m)^      enabled:\s*" + re.escape(expected) + r"\s*$", central) is not None,
        "config-repo/application.yml: central virtual-thread default is missing or differs")
require(re.search(r"(?m)^    keep-alive:\s*true\s*$", central) is not None,
        "config-repo/application.yml: central spring.main.keep-alive must be true")

for path in sorted((root / "config-repo").glob("*.yml")):
    if path.name == "application.yml":
        continue
    require("spring.threads.virtual.enabled" not in path.read_text(),
            f"{path.relative_to(root)} unexpectedly overrides the threading toggle")

compose = (root / "docker-compose.yml").read_text()
example = (root / ".env.example").read_text()
require("SPRING_THREADS_VIRTUAL_ENABLED=false" in example,
        ".env.example: global virtual-thread default must be false")
require("# PRODUCT_SERVICE_VIRTUAL_THREADS=true" in example and "# CART_SERVICE_VIRTUAL_THREADS=true" in example,
        ".env.example: commented product and cart service override examples are required")
for service in services:
    env_name = service.replace("-", "_").upper() + "_VIRTUAL_THREADS"
    require(re.search(rf"(?m)^  {re.escape(service)}:\s*$", compose) is not None,
            f"docker-compose.yml: service {service} missing")
    service_block = re.search(rf"(?ms)^  {re.escape(service)}:\s*\n(.*?)(?=^  [\w-]+:|\Z)", compose)
    block = service_block.group(1) if service_block else ""
    expected_compose = f"SPRING_THREADS_VIRTUAL_ENABLED: ${{{env_name}:-${{SPRING_THREADS_VIRTUAL_ENABLED:-false}}}}"
    require(expected_compose in block,
            f"docker-compose.yml: {service} must explicitly set {env_name} with global false-default fallback")

def effective(global_value, service_value):
    return service_value if service_value is not None else (global_value if global_value is not None else "false")

require(effective(None, None) == "false", "simulated Compose default must disable virtual threads")
require(effective("true", None) == "true", "simulated global toggle must enable virtual threads")
require(effective("true", "false") == "false", "simulated service override must take precedence")
require(effective("false", "true") == "true", "simulated service override must enable virtual threads")

if errors:
    print("Java 21 threading configuration checks failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    sys.exit(1)
print("Java 21 threading configuration checks passed for all 11 services.")
PY
