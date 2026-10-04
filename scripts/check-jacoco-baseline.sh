#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
    printf 'usage: %s <baseline.json>\n' "$0" >&2
    exit 2
fi

python3 - "$1" <<'PY'
import json
import sys
from pathlib import Path
from xml.etree import ElementTree

baseline_path = Path(sys.argv[1])

try:
    baseline = json.loads(baseline_path.read_text())
except (OSError, json.JSONDecodeError) as error:
    raise SystemExit(f"invalid baseline: {error}")

if set(baseline) != {"version", "modules", "criticalClasses"} or baseline["version"] != 1:
    raise SystemExit("baseline must use the version 1 backend coverage schema")

required_critical_classes = {
    "com.project.product_service.service.impl.ProductServiceImpl": {"line": 80, "branch": 80},
    "com.project.product_service.kafka.ProductSearchConsumer": {"line": 80, "branch": 80},
    "com.project.order.service.impl.OrderServiceImpl": {"line": 80, "branch": 80},
    "com.project.order.service.impl.OutboxEventRelay": {"line": 80, "branch": 80},
    "com.project.payment.service.impl.PaymentServiceImpl": {"line": 80, "branch": 80},
    "com.project.payment.service.impl.PaymentOutboxRelay": {"line": 80, "branch": 80},
    "com.project.payment.controller.PaymentController": {"line": 80, "branch": 80},
    "com.project.gateway.filter.ForwardedIpTrustFilter": {"line": 80, "branch": 80},
    "com.project.gateway.config.ClientHeaderStrippingFilter": {"line": 80, "branch": 80},
    "com.project.authservice.service.AuthService": {"line": 80, "branch": 80},
    "com.project.authservice.service.ClientCredentialsService": {"line": 80, "branch": 80},
}

if baseline["criticalClasses"] != required_critical_classes:
    raise SystemExit("criticalClasses must match the required 11 classes with line and branch thresholds of 80%")

def counters(value, context):
    if set(value) != {"line", "branch"}:
        raise ValueError(f"{context} must contain line and branch")
    result = {}
    for counter_type, counter in value.items():
        if set(counter) != {"covered", "missed"} or not all(isinstance(n, int) and n >= 0 for n in counter.values()):
            raise ValueError(f"{context}.{counter_type} must contain non-negative integer covered and missed counters")
        result[counter_type.upper()] = counter
    return result

try:
    modules = {name: counters(value, f"modules.{name}") for name, value in baseline["modules"].items()}
    critical = baseline["criticalClasses"]
    if not modules or not critical:
        raise ValueError("modules and criticalClasses must not be empty")
    for name, thresholds in critical.items():
        if set(thresholds) != {"line", "branch"} or not all(isinstance(value, int) and 0 <= value <= 100 for value in thresholds.values()):
            raise ValueError(f"criticalClasses.{name} must contain integer line and branch thresholds from 0 to 100")
except (AttributeError, ValueError) as error:
    raise SystemExit(f"invalid baseline: {error}")

classes = {}
failures = []
for module, expected in modules.items():
    report = Path(module) / "target/site/jacoco/jacoco.xml"
    if not report.is_file():
        failures.append(f"{module}: missing {report}")
        continue
    try:
        root = ElementTree.parse(report).getroot()
    except (OSError, ElementTree.ParseError) as error:
        failures.append(f"{module}: unreadable JaCoCo XML: {error}")
        continue
    actual = {"LINE": {"covered": 0, "missed": 0}, "BRANCH": {"covered": 0, "missed": 0}}
    for element in root.findall(".//class"):
        name = element.attrib.get("name", "").replace("/", ".")
        class_counters = {}
        for counter in element.findall("counter"):
            counter_type = counter.attrib.get("type")
            if counter_type in actual:
                try:
                    class_counters[counter_type] = {key: int(counter.attrib[key]) for key in ("covered", "missed")}
                except (KeyError, ValueError):
                    failures.append(f"{module}: malformed {counter_type} counter for {name}")
        for counter_type, counts in class_counters.items():
            actual[counter_type]["covered"] += counts["covered"]
            actual[counter_type]["missed"] += counts["missed"]
        if name in critical:
            if name in classes:
                failures.append(f"critical class {name} appears in multiple module reports")
            else:
                classes[name] = class_counters
    for counter_type, recorded in expected.items():
        measured = actual[counter_type]
        if measured["covered"] < recorded["covered"] or measured["missed"] > recorded["missed"]:
            failures.append(
                f"{module} {counter_type}: covered {measured['covered']} < {recorded['covered']} or missed {measured['missed']} > {recorded['missed']}"
            )

for name, thresholds in critical.items():
    measured = classes.get(name)
    if measured is None:
        failures.append(f"critical class {name}: missing from JaCoCo reports")
        continue
    for counter_type, threshold in (("LINE", thresholds["line"]), ("BRANCH", thresholds["branch"])):
        counts = measured.get(counter_type)
        if counts is None:
            failures.append(f"critical class {name}: missing {counter_type} counter")
            continue
        total = counts["covered"] + counts["missed"]
        if total == 0 or counts["covered"] * 100 < threshold * total:
            failures.append(f"critical class {name} {counter_type}: {counts['covered']}/{total} is below {threshold}%")

if failures:
    raise SystemExit("\n".join(failures))
print("JaCoCo baseline and critical-class checks passed")
PY
