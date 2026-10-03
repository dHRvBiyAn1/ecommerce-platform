#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 1 || ! -f $1 ]]; then
  printf 'Usage: bash scripts/verify-release-evidence.sh <evidence.md>\n' >&2
  exit 2
fi

python3 - "$1" <<'PY'
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse
import json
import re
import sys

document = Path(sys.argv[1]).resolve()
artifact_dir = document.parent / "release-hardening-evidence"
lines = document.read_text(encoding="utf-8").splitlines()
errors = []
repository_only = "**Verification scope:** REPOSITORY_ONLY" in lines

def table(section, header):
    marker = f"## {section}"
    sections = [i for i, line in enumerate(lines) if line.strip() == marker]
    if len(sections) != 1:
        errors.append(f"expected exactly one standalone '## {section}' heading")
        return []
    body = lines[sections[0] + 1:]
    end = next((i for i, line in enumerate(body) if line.startswith("## ")), len(body))
    body = body[:end]
    start = next((i for i, line in enumerate(body) if line.strip() == header), None)
    if start is None:
        errors.append(f"missing {header} table in {section}")
        return []
    expected_count = len(header.strip().strip("|").split("|"))
    rows = []
    for line in body[start + 1:]:
        if not line.strip():
            continue
        if not line.strip().startswith("|"):
            break
        cells = [cell.strip().strip("`") for cell in line.strip().strip("|").split("|")]
        if cells == ["---"] * expected_count:
            continue
        if len(cells) == expected_count:
            rows.append(cells)
        else:
            errors.append(f"malformed {section} row: {line}")
    return rows

def load_json_artifact(ref, prefix, filename_pattern):
    if not isinstance(ref, str) or not ref.startswith(prefix + ":"):
        errors.append(f"unsupported evidence reference {ref!r}; expected {prefix}:<artifact>")
        return None
    name = ref[len(prefix) + 1:]
    if not re.fullmatch(filename_pattern, name):
        errors.append(f"unsupported {prefix} artifact reference {name!r}")
        return None
    path = (artifact_dir / name).resolve()
    if path.parent != artifact_dir.resolve() or not path.is_file():
        errors.append(f"missing captured {prefix} evidence artifact: {name}")
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exception:
        errors.append(f"invalid JSON in {name}: {exception}")
        return None
    if not isinstance(data, dict):
        errors.append(f"{name} must contain a JSON object")
        return None
    return data

task_file = artifact_dir / "task-records.json"
task_records = {}
if not task_file.is_file():
    errors.append("missing per-task evidence artifact: task-records.json")
else:
    try:
        task_payload = json.loads(task_file.read_text(encoding="utf-8"))
        if task_payload.get("schema") != "release-task-records-v1":
            errors.append("task-records.json has an unsupported schema")
        for record in task_payload.get("records", []):
            task = record.get("taskId")
            if not isinstance(task, int) or task in task_records:
                errors.append(f"duplicate or invalid taskId in task-records.json: {task!r}")
            else:
                task_records[task] = record
    except (OSError, json.JSONDecodeError, AttributeError) as exception:
        errors.append(f"invalid task-records.json: {exception}")

task_rows = table("Task records", "| Task | Status | Evidence |")
task_statuses = {}
seen_sources = set()
seen_commits = set()
reviews_complete = True
for task_text, status, reference in task_rows:
    if not re.fullmatch(r"(?:[1-9]|1\d|2[0-8])", task_text):
        errors.append(f"unknown task record: {task_text!r}")
        continue
    task = int(task_text)
    if task in task_statuses:
        errors.append(f"duplicate Task {task} record")
    task_statuses[task] = status
    if reference != f"task:{task}":
        errors.append(f"Task {task} must cite its task-specific record as task:{task}")
        continue
    record = task_records.get(task)
    if record is None:
        errors.append(f"missing task-specific supporting record for Task {task}")
        continue
    if record.get("taskId") != task or record.get("status") != status:
        errors.append(f"task record identity/status mismatch for Task {task}")
    allowed_statuses = {"COMPLETE", "PARTIAL", "PENDING"}
    if status not in allowed_statuses:
        errors.append(f"Task {task} has unknown status {status!r}")
    elif status != "COMPLETE":
        errors.append(f"Task {task} is {status}, not COMPLETE")
    source = record.get("sourceArtifact", "")
    if source in seen_sources:
        errors.append(f"task source is reused instead of task-specific: {source}")
    seen_sources.add(source)
    expected_source = f"task-records.json#task-{task}"
    if source != expected_source:
        errors.append(f"Task {task} source must identify its own task record")
    excerpt = record.get("ledgerExcerpt", "")
    if not re.search(rf"\bTask\s+{task}\b", excerpt) or "review" not in excerpt.lower():
        errors.append(f"Task {task} support excerpt must identify its task and review")
    if status == "COMPLETE":
        commit = record.get("commit", "")
        if not re.fullmatch(r"[0-9a-f]{8,40}", commit) or commit in seen_commits:
            errors.append(f"Task {task} needs its own recorded reviewed commit")
        seen_commits.add(commit)
        if commit.lower() not in excerpt.lower():
            errors.append(f"Task {task} support excerpt does not identify commit {commit}")
        if record.get("review") not in {"APPROVED", "APPROVED_WITH_DEFERRED", "APPROVED_WITH_PARKED"}:
            errors.append(f"Task {task} lacks an approved review outcome")
        command, result = record.get("command", ""), record.get("result", "")
        if not command or len(result) < 20 or not re.search(r"\b(PASS|passed|BUILD SUCCESS)\b", result, re.I):
            errors.append(f"Task {task} lacks task-specific verification output")
        if re.search(r"\b(TBD|TODO|unknown|fake|placeholder|pending|N/A)\b", result, re.I):
            errors.append(f"Task {task} verification contains a placeholder")
    else:
        reviews_complete = False

for task in range(1, 29):
    if task not in task_statuses:
        errors.append(f"missing Task {task} record")
if set(task_records) != set(range(1, 29)):
    errors.append("task-records.json must contain exactly Task 1 through Task 28")

gate_rows = table("Gate records", "| Gate | Status | Evidence |")
gate_data = {}
for name, status, reference in gate_rows:
    if name in gate_data:
        errors.append(f"duplicate gate record: {name}")
    gate_data[name] = (status, reference)

required = {
    "ci-run": "SUCCESS", "backend-tests": "PASS", "backend-coverage": "PASS",
    "frontend-tests": "PASS", "frontend-coverage": "PASS", "testcontainers": "PASS",
    "smoke": "PASS", "reviewers": "SIGNED_OFF",
    "no-compose": "DECLARED",
}
images = {"api-gateway", "auth-service", "product-service", "inventory-service", "order-service",
          "payment-service", "notification-service", "cart-service", "coupon-service"}
required.update({f"image:{service}": "SUCCESS" for service in images})
for name in gate_data.keys() - required.keys():
    errors.append(f"unknown gate record: {name}")
for name in required.keys() - gate_data.keys():
    errors.append(f"missing gate evidence: {name}")

ci_cache = {}
def ci_for(reference):
    data = load_json_artifact(reference, "ci", r"ci-run-\d+\.json")
    if data is None:
        return None
    run_id = re.fullmatch(r"ci-run-(\d+)\.json", reference[3:]).group(1)
    if run_id not in ci_cache:
        if (data.get("databaseId") != int(run_id) or data.get("status") != "completed"
                or not re.fullmatch(r"[0-9a-f]{40}", data.get("headSha", ""))
                or data.get("url", "").rstrip("/").endswith(f"/actions/runs/{run_id}") is False
                or data.get("captureCommand", f"gh run view {run_id} --json databaseId,headSha,url,status,conclusion,jobs")
                != f"gh run view {run_id} --json databaseId,headSha,url,status,conclusion,jobs"):
            errors.append(f"CI capture identity/provenance does not match run {run_id}")
        jobs = {job.get("name"): job for job in data.get("jobs", [])}
        if len(jobs) != len(data.get("jobs", [])):
            errors.append(f"CI capture for run {run_id} has duplicate job names")
        expected_jobs = {"detect-changes", "backend", "frontend", "JUnit Test Report"}
        expected_jobs.update(f"images ({service})" for service in {
            "api-gateway", "auth-service", "product-service", "inventory-service", "order-service",
            "payment-service", "notification-service", "cart-service", "coupon-service",
        })
        # Preserve old captures truthfully; Sonar is no longer a release job or gate.
        if set(jobs) - {"sonar"} != expected_jobs:
            errors.append(f"CI capture for run {run_id} does not contain the exact required job set")
        for job_name in expected_jobs:
            if job_name in jobs and jobs[job_name].get("conclusion") != "success":
                errors.append(f"CI job {job_name!r} conclusion is not success")
        ci_cache[run_id] = (data, jobs)
    return ci_cache[run_id]

run_reference = gate_data.get("ci-run", (None, None))[1]
captured_run = ci_for(run_reference) if isinstance(run_reference, str) and run_reference.startswith("ci:") else None

def successful_step(reference, job_name, step_name):
    captured = ci_for(reference)
    if captured is None:
        return
    _, jobs = captured
    job = jobs.get(job_name)
    if job is None or job.get("conclusion") != "success":
        errors.append(f"CI job {job_name!r} is absent or not successful")
        return
    step = next((item for item in job.get("steps", []) if item.get("name") == step_name), None)
    if step is None or step.get("conclusion") != "success":
        errors.append(f"{step_name} step is not successful in CI job {job_name!r}")

def safe_url(value):
    if not isinstance(value, str):
        return False
    try:
        parsed = urlparse(value)
        return (parsed.scheme in {"http", "https"} and parsed.hostname and not parsed.username
                and not parsed.password and not parsed.query and not parsed.fragment)
    except ValueError:
        return False

def validate_smoke(capture):
    if capture.get("schema") != "release-smoke-v1" or capture.get("command") != "bash scripts/smoke-release.sh":
        errors.append("smoke transcript must identify the release smoke command and schema")
    if "stdout" not in capture.get("capturedFrom", "").lower():
        errors.append("smoke transcript must include capture provenance")
    try:
        parsed_time = datetime.fromisoformat(capture.get("capturedAt", "").replace("Z", "+00:00"))
        if parsed_time.tzinfo is None:
            raise ValueError("timestamp has no timezone")
    except (ValueError, TypeError, AttributeError):
        errors.append("smoke transcript must include a timezone-qualified ISO capture timestamp")
    if capture.get("exitStatus") != 0:
        errors.append("smoke transcript must record exit status 0")
    base_value = capture.get("baseUrl", "")
    if not safe_url(base_value):
        errors.append("smoke transcript baseUrl must be configured and omit credentials, query, and fragment")
    base_url = base_value.rstrip("/") if isinstance(base_value, str) else ""
    overrides = capture.get("endpointOverrides", {})
    if not isinstance(overrides, dict) or set(overrides) - {"health", "openapi"}:
        errors.append("smoke endpointOverrides must contain only health/openapi URLs")
        overrides = {}
    probes = capture.get("probes", [])
    if not isinstance(probes, list) or len(probes) != 2 or {p.get("name") for p in probes if isinstance(p, dict)} != {"health", "openapi"}:
        errors.append("live smoke artifact must contain both health and OpenAPI probes")
        return
    transcript = capture.get("transcript", "")
    if re.search(r"Bearer\s+\S+", transcript, re.I):
        errors.append("smoke transcript must not contain bearer credentials")
    default_paths = {"health": "/actuator/health", "openapi": "/v3/api-docs/swagger-config"}
    for probe in probes:
        name, status = probe.get("name", ""), probe.get("httpStatus")
        if probe.get("method") != "GET" or not isinstance(status, int) or not 200 <= status < 300:
            errors.append(f"smoke {name} probe must record GET and a 2xx HTTP status")
        if not safe_url(probe.get("url", "")):
            errors.append(f"smoke {name} configured URL must omit credentials, query, and fragment")
        expected_url = overrides.get(name, base_url + default_paths.get(name, ""))
        if probe.get("url") != expected_url:
            errors.append(f"smoke {name} URL does not match the captured base URL/override")
        if not re.search(rf"GET {re.escape(name)}\b", transcript, re.I):
            errors.append(f"smoke transcript is missing the configured GET {name} request")
        if not re.search(rf"HTTP {re.escape(name)} {status}\b", transcript, re.I):
            errors.append(f"smoke transcript is missing captured {name} HTTP status")
    if "PASS: release health and OpenAPI probes succeeded" not in transcript:
        errors.append("smoke transcript is missing the release smoke success line")

for name, (status, reference) in gate_data.items():
    if name not in required:
        continue
    expected = required[name]
    if status != expected and not (name == "smoke" and status == "DEFERRED" and repository_only):
        errors.append(f"{name} status is {status or 'missing'}, expected {expected}")
    if name == "smoke":
        if status == "DEFERRED":
            if not repository_only:
                errors.append("smoke deferral requires REPOSITORY_ONLY scope")
            capture = load_json_artifact(reference, "smoke-deferral", r"smoke-deferral\.json")
            if capture is not None:
                if (capture.get("schema") != "release-smoke-deferral-v1"
                        or capture.get("scope") != "REPOSITORY_ONLY"
                        or capture.get("decision") != "defer-live-smoke"):
                    errors.append("smoke deferral must record the repository-only decision and schema")
                if capture.get("approvedBy") != "project-owner" or not capture.get("recordedDecision"):
                    errors.append("smoke deferral requires project-owner approval")
                if not isinstance(capture.get("reason"), str) or len(capture["reason"].strip()) < 20:
                    errors.append("smoke deferral must explain why deployment validation is deferred")
                if capture.get("requiresBeforeProductionDeployment") is not True:
                    errors.append("deferred smoke must remain required before production deployment")
        elif status == "PASS":
            if not isinstance(reference, str) or not re.fullmatch(r"smoke:smoke-run-[A-Za-z0-9._-]+\.json", reference):
                errors.append("live smoke evidence must be a captured smoke-run JSON artifact")
            else:
                capture = load_json_artifact(reference, "smoke", r"smoke-run-[A-Za-z0-9._-]+\.json")
                if capture is not None:
                    validate_smoke(capture)
        elif reference != "NOT_RUN":
            errors.append("unrun smoke must use NOT_RUN until a captured transcript exists")
        continue
    if name == "reviewers":
        if reference != "tasks:all" or not reviews_complete or any(task_statuses.get(i) != "COMPLETE" for i in range(1, 29)):
            errors.append("reviewer sign-off requires an approved review outcome for every completed task")
        continue
    if name == "no-compose":
        if reference != "task:26" or not re.search(r"without Docker Compose|no Docker Compose|Docker Compose was not used", task_records.get(26, {}).get("noComposeDeclaration", ""), re.I):
            errors.append("no-compose declaration must be supported by the reviewed Task 26 record")
        continue
    if not isinstance(reference, str) or not reference.startswith("ci:") or reference != run_reference:
        errors.append(f"{name} must cite the same captured CI run artifact as ci-run")
        continue
    if name == "ci-run":
        if not captured_run or captured_run[0].get("conclusion") != "success":
            errors.append("captured GitHub Actions run conclusion is not success")
    elif name == "backend-tests":
        successful_step(reference, "backend", "Verify the whole reactor")
    elif name == "backend-coverage":
        successful_step(reference, "backend", "Test backend coverage checker")
        successful_step(reference, "backend", "Check backend coverage baseline")
    elif name == "frontend-tests":
        successful_step(reference, "frontend", "React quality")
        successful_step(reference, "frontend", "Build frontend distribution")
    elif name == "frontend-coverage":
        successful_step(reference, "frontend", "React quality")
    elif name == "testcontainers":
        successful_step(reference, "backend", "Verify Docker availability for Testcontainers")
        successful_step(reference, "backend", "Verify the whole reactor")
    elif name.startswith("image:"):
        successful_step(reference, f"images ({name.split(':', 1)[1]})", "Build image")

if errors:
    print("Release evidence is incomplete:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    sys.exit(1)
if repository_only and gate_data.get("smoke", (None, None))[0] == "DEFERRED":
    print("Repository evidence complete: all 28 task records and repository gates verified; live deployment smoke deferred.")
else:
    print("Release evidence complete: all 28 task records and evidence-backed release gates are verified.")
PY
