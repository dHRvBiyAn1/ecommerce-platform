#!/usr/bin/env python3
"""Check that packaged services contain generated server code, never test clients."""
from pathlib import Path
from zipfile import ZipFile
import re

root = Path(__file__).resolve().parents[1]
legacy_sources = [source for source in (root / "services").glob("*/src/main/java/**/*.java")
                  if "dto" in source.relative_to(root).parts
                  or source.name in {"ProductSummary.java", "OrderSummary.java",
                                     "CouponValidationRequest.java", "CouponValidationResponse.java",
                                     "ServiceTokenResponse.java"}]
if legacy_sources:
    raise SystemExit("Handwritten transport DTOs remain: " + ", ".join(
        str(source.relative_to(root)) for source in legacy_sources))

server_operations = {"auth": 21, "product": 22, "inventory": 12, "order": 7,
                     "payment": 7, "notification": 3, "cart": 7, "coupon": 10}
for service, expected in server_operations.items():
    module = root / "services" / f"{service}-service"
    generated_apis = module / "target/generated-sources/openapi/src/main/java"
    actual = sum(len(re.findall(r"@RequestMapping\s*\(", source.read_text()))
                 for source in generated_apis.rglob("*Api.java"))
    if actual != expected:
        raise SystemExit(f"{service}: expected {expected} generated MVC operations, found {actual}")
    jars = list((root / "services" / f"{service}-service" / "target").glob("*-SNAPSHOT.jar"))
    if len(jars) != 1:
        raise SystemExit(f"{service}: expected one packaged Spring Boot jar, found {len(jars)}")
    with ZipFile(jars[0]) as artifact:
        names = artifact.namelist()
    if not any("/generated/api/" in name and name.endswith(".class") for name in names):
        raise SystemExit(f"{service}: generated server interfaces missing from runtime artifact")
    if any("/dto/" in name for name in names if name.startswith("BOOT-INF/classes/")):
        raise SystemExit(f"{service}: handwritten DTO classes remain in runtime artifact")
    if any("/generated/testclient/" in name for name in names):
        raise SystemExit(f"{service}: generated test client leaked into runtime artifact")
    if any("jackson-databind-nullable" in name for name in names):
        raise SystemExit(f"{service}: test-only nullable dependency leaked into runtime artifact")
    print(f"PASS {service}: {actual} generated MVC operations; server present; test client and nullable dependency absent")
