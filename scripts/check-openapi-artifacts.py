#!/usr/bin/env python3
"""Check that packaged services contain generated server code, never test clients."""
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
services = ("auth", "product", "inventory", "order", "payment", "notification", "cart", "coupon")
for service in services:
    jars = list((root / "services" / f"{service}-service" / "target").glob("*-SNAPSHOT.jar"))
    if len(jars) != 1:
        raise SystemExit(f"{service}: expected one packaged Spring Boot jar, found {len(jars)}")
    with ZipFile(jars[0]) as artifact:
        names = artifact.namelist()
    if not any("/generated/api/" in name and name.endswith(".class") for name in names):
        raise SystemExit(f"{service}: generated server interfaces missing from runtime artifact")
    if any("/generated/testclient/" in name for name in names):
        raise SystemExit(f"{service}: generated test client leaked into runtime artifact")
    if any("jackson-databind-nullable" in name for name in names):
        raise SystemExit(f"{service}: test-only nullable dependency leaked into runtime artifact")
    print(f"PASS {service}: generated server present; test client and nullable dependency absent")
