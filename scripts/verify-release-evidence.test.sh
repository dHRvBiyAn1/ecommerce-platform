#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

python3 - "$tmp" <<'PY'
import json
from pathlib import Path
import shutil
import sys

base = Path(sys.argv[1])
evidence = base / "release-hardening-evidence"
evidence.mkdir()

def ci_capture(sonar="success", remove_backend_coverage=False):
    jobs = []
    def job(name, steps, conclusion="success"):
        jobs.append({"name": name, "conclusion": conclusion,
                     "steps": [{"name": step, "conclusion": result} for step, result in steps]})
    job("detect-changes", [("Run dorny/paths-filter@v3", "success")])
    backend_steps = [
        ("Verify Docker availability for Testcontainers", "success"),
        ("Test backend coverage checker", "success"),
        ("Verify the whole reactor", "success"),
    ]
    if not remove_backend_coverage:
        backend_steps.append(("Check backend coverage baseline", "success"))
    job("backend", backend_steps)
    job("frontend", [("React quality", "success"), ("Build frontend distribution", "success")])
    job("sonar", [("SonarCloud", sonar)], conclusion=sonar)
    for service in ("api-gateway", "auth-service", "product-service", "inventory-service",
                    "order-service", "payment-service", "notification-service", "cart-service", "coupon-service"):
        job(f"images ({service})", [("Build image", "success")])
    job("JUnit Test Report", [])
    return {
        "databaseId": 424242,
        "headSha": "a" * 40,
        "url": "https://github.com/example/ecommerce-platform/actions/runs/424242",
        "status": "completed",
        "conclusion": "success",
        "captureCommand": "gh run view 424242 --json databaseId,headSha,url,status,conclusion,jobs",
        "jobs": jobs,
    }

def task_records():
    records = []
    for task in range(1, 29):
        source = f"task-records.json#task-{task}"
        records.append({
            "taskId": task,
            "status": "COMPLETE",
            "commit": f"{task:08x}",
            "review": "APPROVED",
            "command": f"./mvnw -pl synthetic-task-{task} verify",
            "result": f"PASS: Task {task} synthetic verification completed with zero failures.",
            "sourceArtifact": source,
            "ledgerExcerpt": f"Task {task}: complete (commit {task:08x}, review approved; verification passed).",
        })
    records[25]["noComposeDeclaration"] = "Isolated Testcontainers used; Docker Compose was not used."
    return {"schema": "release-task-records-v1", "records": records,
            "declarations": {"noCompose": "Isolated Testcontainers only; Docker Compose was not used."}}

def smoke_capture():
    return {
        "schema": "release-smoke-v1",
        "capturedFrom": "stdout capture from the configured release smoke invocation",
        "capturedAt": "2026-09-30T12:00:00Z",
        "command": "bash scripts/smoke-release.sh",
        "exitStatus": 0,
        "baseUrl": "https://gateway.synthetic.invalid",
        "endpointOverrides": {},
        "probes": [
            {"name": "health", "method": "GET", "url": "https://gateway.synthetic.invalid/actuator/health", "httpStatus": 200},
            {"name": "openapi", "method": "GET", "url": "https://gateway.synthetic.invalid/v3/api-docs/swagger-config", "httpStatus": 200},
        ],
        "transcript": "GET health\nHTTP health 200\nGET OpenAPI\nHTTP OpenAPI 200\nPASS: release health and OpenAPI probes succeeded",
    }

valid_gates = [
    ("ci-run", "SUCCESS", "ci:ci-run-424242.json"),
    ("backend-tests", "PASS", "ci:ci-run-424242.json"),
    ("backend-coverage", "PASS", "ci:ci-run-424242.json"),
    ("frontend-tests", "PASS", "ci:ci-run-424242.json"),
    ("frontend-coverage", "PASS", "ci:ci-run-424242.json"),
    ("testcontainers", "PASS", "ci:ci-run-424242.json"),
    ("smoke", "PASS", "smoke:smoke-run-fixture.json"),
    ("reviewers", "SIGNED_OFF", "tasks:all"),
    ("sonar-external", "ACCEPTED", "ci:ci-run-424242.json"),
    ("no-compose", "DECLARED", "task:26"),
] + [(f"image:{service}", "SUCCESS", "ci:ci-run-424242.json") for service in (
    "api-gateway", "auth-service", "product-service", "inventory-service", "order-service",
    "payment-service", "notification-service", "cart-service", "coupon-service")]

def markdown(tasks=None, gates=None):
    tasks = tasks if tasks is not None else [(i, "COMPLETE", f"task:{i}") for i in range(1, 29)]
    gates = gates if gates is not None else valid_gates
    lines = ["# Synthetic complete evidence fixture", "", "## Task records", "",
             "| Task | Status | Evidence |", "| --- | --- | --- |"]
    lines.extend(f"| {task} | {status} | {ref} |" for task, status, ref in tasks)
    lines.extend(["", "## Gate records", "", "| Gate | Status | Evidence |", "| --- | --- | --- |"])
    lines.extend(f"| {name} | {status} | {ref} |" for name, status, ref in gates)
    return "\n".join(lines) + "\n"

(evidence / "task-records.json").write_text(json.dumps(task_records(), indent=2) + "\n")
(evidence / "ci-run-424242.json").write_text(json.dumps(ci_capture(), indent=2) + "\n")
(evidence / "smoke-run-fixture.json").write_text(json.dumps(smoke_capture(), indent=2) + "\n")
(base / "complete.md").write_text(markdown())
(base / "README.md").write_text("existing unrelated README fixture\n")

def write(name, text):
    (base / name).write_text(text)

lines = markdown().splitlines()
write("missing-task.md", "\n".join(line for line in lines if not line.startswith("| 28 |")) + "\n")
write("missing-gate.md", "\n".join(line for line in lines if not line.startswith("| backend-coverage |")) + "\n")
write("pending.md", markdown(tasks=[(i, "PENDING" if i == 28 else "COMPLETE", f"task:{i}") for i in range(1, 29)]))
write("unknown.md", markdown(tasks=[(i, "UNKNOWN" if i == 28 else "COMPLETE", f"task:{i}") for i in range(1, 29)]))
write("readme-citations.md", markdown(
    tasks=[(i, "COMPLETE", "file:README.md") for i in range(1, 29)],
    gates=[(name, status, "file:README.md") for name, status, _ in valid_gates]))
write("mock-smoke-source.md", markdown(gates=[
    (name, status, "smoke:scripts/smoke-release.test.sh" if name == "smoke" else ref)
    for name, status, ref in valid_gates]))

def variant(name, document=None, mutate_ci=None, mutate_tasks=None, mutate_smoke=None):
    target = base / name
    target.mkdir()
    proof = target / "release-hardening-evidence"
    shutil.copytree(evidence, proof)
    if mutate_ci:
        data = json.loads((proof / "ci-run-424242.json").read_text())
        mutate_ci(data)
        (proof / "ci-run-424242.json").write_text(json.dumps(data, indent=2) + "\n")
    if mutate_tasks:
        data = json.loads((proof / "task-records.json").read_text())
        mutate_tasks(data)
        (proof / "task-records.json").write_text(json.dumps(data, indent=2) + "\n")
    if mutate_smoke:
        data = json.loads((proof / "smoke-run-fixture.json").read_text())
        mutate_smoke(data)
        (proof / "smoke-run-fixture.json").write_text(json.dumps(data, indent=2) + "\n")
    (target / "evidence.md").write_text(document or markdown())
    return target / "evidence.md"

variant("failed-sonar", mutate_ci=lambda data: [
    job.update(conclusion="failure", **{"steps": [
        step | ({"conclusion": "failure"} if step["name"] == "SonarCloud" else {})
        for step in job["steps"]
    ]}) for job in data["jobs"] if job["name"] == "sonar"
])
variant("blocked-sonar", document=markdown(gates=[
    (name, "BLOCKED" if name == "sonar-external" else status, ref)
    for name, status, ref in valid_gates]))
variant("task-identity", mutate_tasks=lambda data: data["records"][0].update(status="PENDING"))
variant("ci-identity", mutate_ci=lambda data: data.update(databaseId=424243))
variant("fake-task", mutate_tasks=lambda data: data["records"][0].update(result="README overview only"))
variant("missing-ci-step", mutate_ci=lambda data: [
    job.update(steps=[step for step in job["steps"] if step["name"] != "Check backend coverage baseline"])
    for job in data["jobs"] if job["name"] == "backend"
])
variant("failed-image", mutate_ci=lambda data: [
    job.update(conclusion="failure", **{"steps": [
        step | ({"conclusion": "failure"} if step["name"] == "Build image" else {})
        for step in job["steps"]
    ]}) for job in data["jobs"] if job["name"] == "images (api-gateway)"
])
variant("missing-smoke-result", mutate_smoke=lambda data: data.update(probes=data["probes"][:1]))
PY

checker="$root/scripts/verify-release-evidence.sh"
expect_reject() {
  local name=$1 file=$2 expected=$3 output
  if output=$(bash "$checker" "$file" 2>&1); then
    printf 'FAIL: %s fixture was accepted\n' "$name" >&2
    exit 1
  fi
  [[ $output == *"$expected"* ]] || {
    printf 'FAIL: %s rejection lacked %q: %s\n' "$name" "$expected" "$output" >&2
    exit 1
  }
}

bash "$checker" "$tmp/complete.md"

expect_reject() {
  local name=$1 file=$2 expected=$3 output
  if output=$(bash "$checker" "$file" 2>&1); then
    printf 'FAIL: %s fixture was accepted\n' "$name" >&2
    exit 1
  fi
  [[ $output == *"$expected"* ]] || {
    printf 'FAIL: %s rejection lacked %q: %s\n' "$name" "$expected" "$output" >&2
    exit 1
  }
}

expect_reject 'missing task' "$tmp/missing-task.md" 'missing Task 28'
expect_reject 'missing gate' "$tmp/missing-gate.md" 'missing gate evidence: backend-coverage'
expect_reject 'pending task' "$tmp/pending.md" 'Task 28 is PENDING'
expect_reject 'unknown task status' "$tmp/unknown.md" "Task 28 has unknown status 'UNKNOWN'"
expect_reject 'README citations' "$tmp/readme-citations.md" 'must cite its task-specific record'
expect_reject 'mock smoke test source' "$tmp/mock-smoke-source.md" 'live smoke evidence must be a captured smoke-run JSON artifact'
expect_reject 'failed Sonar accepted' "$tmp/failed-sonar/evidence.md" 'SonarCloud job conclusion is failure'
expect_reject 'blocked Sonar' "$tmp/blocked-sonar/evidence.md" 'Sonar external analysis is BLOCKED; this is a release blocker, not accepted analysis'
expect_reject 'task status mismatch' "$tmp/task-identity/evidence.md" 'task record identity/status mismatch for Task 1'
expect_reject 'run id mismatch' "$tmp/ci-identity/evidence.md" 'CI capture identity/provenance does not match run 424242'
expect_reject 'fake task completion' "$tmp/fake-task/evidence.md" 'Task 1 lacks task-specific verification output'
expect_reject 'missing CI step' "$tmp/missing-ci-step/evidence.md" 'Check backend coverage baseline step is not successful'
expect_reject 'failed image job' "$tmp/failed-image/evidence.md" "CI job 'images (api-gateway)' conclusion is not success"
expect_reject 'missing live-smoke result' "$tmp/missing-smoke-result/evidence.md" 'live smoke artifact must contain both health and OpenAPI probes'

printf 'PASS: complete synthetic evidence accepted; unsupported sources, false CI/smoke claims, mismatches, and missing outputs rejected\n'
