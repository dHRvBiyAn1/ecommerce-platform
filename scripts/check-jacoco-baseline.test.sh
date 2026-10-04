#!/usr/bin/env bash
set -euo pipefail

# Catches rounded comparisons, missing class-level LINE/BRANCH checks, and
# critical-class lookup failures hidden by the module ratchet.
root=$(cd "$(dirname "$0")/.." && pwd)
fixture="$root/scripts/fixtures/jacoco/critical-classes.xml"
baseline="$root/scripts/fixtures/jacoco/baseline.json"
checker="$root/scripts/check-jacoco-baseline.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

prepare() {
    mkdir -p "$work/services/fixture/target/site/jacoco"
    cp "$fixture" "$work/services/fixture/target/site/jacoco/jacoco.xml"
    cp "$baseline" "$work/baseline.json"
}

run_checker() {
    (cd "$work" && bash "$checker" "$work/baseline.json")
}

expect_class_failure() {
    local class=$1 counter=$2
    if run_checker >"$work/output" 2>&1; then
        printf 'expected critical class %s %s failure\n' "$class" "$counter" >&2
        exit 1
    fi
    if ! python3 - "$work/output" "$class" "$counter" <<'PY'
import sys
output = open(sys.argv[1]).read()
expected = f"critical class {sys.argv[2]} {sys.argv[3]}:"
if expected not in output:
    raise SystemExit(f"failure did not independently prove {expected}\n{output}")
PY
    then
        exit 1
    fi
}

expect_inventory_failure() {
    if run_checker >"$work/output" 2>&1; then
        printf '%s\n' 'expected required critical-class inventory validation to fail' >&2
        exit 1
    fi
    if ! python3 - "$work/output" <<'PY'
import sys
if "criticalClasses must match the required 11 classes with line and branch thresholds of 80%" not in open(sys.argv[1]).read():
    raise SystemExit("required inventory failure was not reported")
PY
    then
        exit 1
    fi
}

prepare
run_checker

prepare
python3 - "$work/services/fixture/target/site/jacoco/jacoco.xml" <<'PY'
import sys
path = sys.argv[1]
text = open(path).read()
open(path, "w").write(text.replace('type="LINE" missed="2" covered="8"', 'type="LINE" missed="2" covered="7"', 1))
PY
if run_checker; then
    printf '%s\n' 'expected covered-count decrease to fail' >&2
    exit 1
fi

prepare
python3 - "$work/services/fixture/target/site/jacoco/jacoco.xml" <<'PY'
import sys
path = sys.argv[1]
text = open(path).read()
open(path, "w").write(text.replace('type="BRANCH" missed="2" covered="8"', 'type="BRANCH" missed="3" covered="8"', 1))
PY
if run_checker; then
    printf '%s\n' 'expected missed-count increase to fail' >&2
    exit 1
fi

prepare
python3 - "$work/services/fixture/target/site/jacoco/jacoco.xml" "$work/baseline.json" <<'PY'
import json
import sys
xml_path, baseline_path = sys.argv[1:]
text = open(xml_path).read()
text = text.replace('type="LINE" missed="2" covered="8"', 'type="LINE" missed="3" covered="7"', 1)
open(xml_path, "w").write(text)
baseline = json.load(open(baseline_path))
baseline["modules"]["services/fixture"]["line"] = {"covered": 87, "missed": 23}
open(baseline_path, "w").write(json.dumps(baseline))
PY
expect_class_failure com.project.product_service.service.impl.ProductServiceImpl LINE

prepare
xml="$work/services/fixture/target/site/jacoco/jacoco.xml"
python3 - "$xml" "$work/baseline.json" <<'PY'
import json
import sys
from xml.etree import ElementTree as ET
xml_path, baseline_path = sys.argv[1:]
root = ET.parse(xml_path).getroot()
counter = root.find(".//class[@name='com/project/order/service/impl/OutboxEventRelay']/counter[@type='BRANCH']")
counter.set("covered", "7")
counter.set("missed", "3")
ET.ElementTree(root).write(xml_path)
baseline = json.load(open(baseline_path))
baseline["modules"]["services/fixture"]["branch"] = {"covered": 87, "missed": 23}
open(baseline_path, "w").write(json.dumps(baseline))
PY
expect_class_failure com.project.order.service.impl.OutboxEventRelay BRANCH

prepare
python3 - "$work/services/fixture/target/site/jacoco/jacoco.xml" <<'PY'
import sys
from xml.etree import ElementTree as ET
path = sys.argv[1]
root = ET.parse(path).getroot()
package = root.find("package[@name='com/project/payment/controller']")
root.remove(package)
ET.ElementTree(root).write(path)
PY
if run_checker >"$work/output" 2>&1 || ! python3 - "$work/output" <<'PY'
import sys
if "critical class com.project.payment.controller.PaymentController: missing from JaCoCo reports" not in open(sys.argv[1]).read():
    raise SystemExit("missing-class failure was not reported")
PY
then
    printf '%s\n' 'expected missing critical class to fail' >&2
    exit 1
fi

prepare
python3 - "$work/services/fixture/target/site/jacoco/jacoco.xml" "$work/baseline.json" <<'PY'
import json
import sys
from xml.etree import ElementTree as ET
xml_path, baseline_path = sys.argv[1:]
root = ET.parse(xml_path).getroot()
cls = root.find(".//class[@name='com/project/authservice/service/ClientCredentialsService']")
cls.remove(cls.find("counter[@type='BRANCH']"))
ET.ElementTree(root).write(xml_path)
baseline = json.load(open(baseline_path))
baseline["modules"]["services/fixture"]["branch"] = {"covered": 80, "missed": 20}
open(baseline_path, "w").write(json.dumps(baseline))
PY
if run_checker >"$work/output" 2>&1 || ! python3 - "$work/output" <<'PY'
import sys
if "critical class com.project.authservice.service.ClientCredentialsService: missing BRANCH counter" not in open(sys.argv[1]).read():
    raise SystemExit("missing-counter failure was not reported")
PY
then
    printf '%s\n' 'expected missing critical counter to fail' >&2
    exit 1
fi

prepare
python3 - "$work/baseline.json" <<'PY'
import json
import sys
path = sys.argv[1]
baseline = json.load(open(path))
del baseline["criticalClasses"]["com.project.product_service.service.impl.ProductServiceImpl"]
open(path, "w").write(json.dumps(baseline))
PY
expect_inventory_failure

prepare
python3 - "$work/baseline.json" <<'PY'
import json
import sys
path = sys.argv[1]
baseline = json.load(open(path))
baseline["criticalClasses"]["com.project.product_service.service.impl.ProductServiceImpl"]["line"] = 79
open(path, "w").write(json.dumps(baseline))
PY
expect_inventory_failure

prepare
python3 - "$work/baseline.json" <<'PY'
import json
import sys
path = sys.argv[1]
baseline = json.load(open(path))
baseline["criticalClasses"]["com.project.product_service.service.impl.ProductServiceImpl"]["branch"] = 79
open(path, "w").write(json.dumps(baseline))
PY
expect_inventory_failure

printf '%s\n' 'check-jacoco-baseline fixtures passed'
