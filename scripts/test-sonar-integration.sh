#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
python3 - "$root" <<'PY'
from pathlib import Path
import io
import json
import os
import re
import sys
import textwrap
import urllib.request
from unittest.mock import patch
import urllib.error
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
pom = ET.parse(root / "pom.xml").getroot()
props = pom.find("{*}properties")
assert props.findtext("{*}sonar.projectKey") == "dHRvBiyAn1_ecommerce-platform", "wrong or missing Sonar project"
assert props.findtext("{*}sonar.organization") == "dhrvbiyan1", "wrong or missing Sonar organization"
assert props.findtext("{*}sonar.maven.scanAll") == "true", "frontend/configuration sources must be included"
assert props.findtext("{*}java.version") == "17", "application Java target must remain 17"
assert props.findtext("{*}sonar.javascript.lcov.reportPaths").endswith("/coverage/sonar-lcov.info"), "missing frontend coverage import"
assert props.findtext("{*}sonar.coverage.jacoco.xmlReportPaths") == "${project.basedir}/target/site/jacoco/jacoco.xml", "each module must import its own verified coverage"
plugins = pom.findall("{*}build/{*}pluginManagement/{*}plugins/{*}plugin")
scanner = next(p for p in plugins if p.findtext("{*}artifactId") == "sonar-maven-plugin")
assert scanner.findtext("{*}version") == "${sonar-maven-plugin.version}"
assert props.findtext("{*}sonar-maven-plugin.version") == "5.8.0.7211", "scanner version must be pinned"
assert props.findtext("{*}sonar.issue.ignore.multicriteria") == "couponNormalization", "unexpected broad issue exclusions"
assert props.findtext("{*}sonar.issue.ignore.multicriteria.couponNormalization.ruleKey") == "plsql:DeleteOrUpdateWithoutWhereCheck"
assert props.findtext("{*}sonar.issue.ignore.multicriteria.couponNormalization.resourceKey") == "**/src/main/resources/db/migration/V3__coupon_lifecycle_locking.sql", "only the intentional immutable migration is reviewed"
profile = next(p for p in pom.findall("{*}profiles/{*}profile") if p.findtext("{*}id") == "sonar-analysis")
assert profile.findtext("{*}modules/{*}module") == "frontend/ecommerce-app", "frontend analysis module must be selected"
assert "frontend/ecommerce-app" not in [p.text for p in pom.findall("{*}modules/{*}module")], "frontend analysis must be profile-only"
frontend = ET.parse(root / "frontend/ecommerce-app/pom.xml").getroot()
assert frontend.findtext("{*}properties/{*}sonar.sources") == "src", "frontend scope must be explicit for pom packaging"
workflow = (root / ".github/workflows/ci.yml").read_text()
assert "\n  sonar:\n" in workflow, "missing analysis job"
sonar = workflow.split("\n  sonar:\n", 1)[1]
assert "needs: [backend, frontend]" in sonar, "analysis must consume both successful verification jobs"
assert "github.event.pull_request.head.repo.full_name == github.repository" in sonar, "fork code must not receive Sonar credentials"
assert "continue-on-error" not in sonar, "quality failures must be reported"
assert "sonar.branch.name" not in sonar, "branch/PR identity must be autodetected"
assert "-Dsonar.token" not in sonar, "token must stay in the environment"
assert 'get("authentication/validate")' in sonar and "https://sonarcloud.io/api/" in sonar and "sonar.autoscan.enabled" in sonar, "authentication and duplicate-analysis setup must be checked"
assert "backend-jacoco-xml" in sonar and "frontend-sonar-lcov" in sonar, "verified coverage artifacts must be downloaded"
assert "bash scripts/test-sonar-maven-scope.sh" in sonar, "actual scanner scope and fresh bytecode must be verified"
assert "-Psonar-analysis" in sonar, "analysis profile must actually execute"
assert "sonar.qualitygate.wait=true" in sonar and 'sonar.java.jdkHome="$JAVA_HOME"' in sonar
assert "SF:frontend/ecommerce-app/src/" in workflow, "LCOV paths must be rooted to the repository"
assert "name: frontend-sonar-lcov" in workflow and "path: frontend/ecommerce-app/coverage/sonar-lcov.info" in workflow
assert all(re.fullmatch(r"[0-9a-f]{40}", reference) for reference in re.findall(r"uses: [^\s@]+@([^\s#]+)", workflow)), "actions must be commit-pinned"
global_permissions = workflow.split("permissions:\n", 1)[1].split("\nconcurrency:", 1)[0]
assert "checks: write" not in global_permissions and "actions: read" not in global_permissions, "permissions must be job-scoped"
assert "npm ci --ignore-scripts" in workflow and "npx playwright" not in workflow, "install and browser setup must use locked packages without automatic lifecycle scripts"
for name in ("backend", "frontend"):
    job = re.split(r"\n  [A-Za-z0-9_-]+:\n", workflow.split(f"\n  {name}:\n", 1)[1], maxsplit=1)[0]
    condition = next(line for line in job.splitlines() if line.startswith("    if:"))
    assert "outputs.backend" in condition and "outputs.frontend" in condition, "both coverage producers must run on either code change"

# Execute the actual CI setup block with synthetic credentials and HTTP responses.
block = sonar.split("        run: |\n", 1)[1].split("      - name:", 1)[0]
lines = textwrap.dedent(block).strip().splitlines()
assert lines[0] == "python3 - <<'PY'" and lines[-1] == "PY"
program = compile("\n".join(lines[1:-1]), "<CI Sonar setup>", "exec")
for token, valid, automatic, http_error, expected in (
    ("", True, False, False, "Configure a current SONAR_TOKEN"),
    ("synthetic-sonar-credential", False, False, False, "SONAR_TOKEN is invalid"),
    ("synthetic-sonar-credential", True, True, False, "Disable Automatic Analysis"),
    ("synthetic-sonar-credential", True, None, False, "Unable to confirm"),
    ("synthetic-sonar-credential", True, False, True, "HTTP 403"),
    ("synthetic-sonar-credential", True, False, False, None),
):
    def response(request, timeout):
        assert request.full_url.startswith("https://sonarcloud.io/api/")
        assert request.get_header("Authorization") == "Bearer " + token
        if http_error:
            raise urllib.error.HTTPError(request.full_url, 403, "Forbidden", {}, None)
        data = {"valid": valid} if "authentication/validate" in request.full_url else {
            "settings": [] if automatic is None else [
                {"key": "sonar.autoscan.enabled", "value": str(automatic).lower()}]}
        return io.BytesIO(json.dumps(data).encode())
    with patch.dict(os.environ, {"SONAR_TOKEN": token}), patch("urllib.request.urlopen", side_effect=response):
        try:
            exec(program, {})
        except SystemExit as error:
            assert expected is not None and expected in str(error)
            assert "synthetic-sonar-credential" not in str(error), "credential leaked in setup diagnostics"
        else:
            assert expected is None, "invalid setup was accepted"
print("PASS: coverage-aware Sonar CI contract, trusted events, project identity, and application Java target")
print("PASS: CI setup rejects missing/invalid credentials, duplicate analysis and HTTP errors without leaking tokens")

# Run the actual failure diagnostic; only its filesystem and HTTP boundaries are mocked.
marker = "      - name: Diagnose failed Sonar analysis\n"
assert marker in sonar, "failed branch scans need background-task diagnostics"
diagnostic = sonar.split(marker, 1)[1]
assert "failure() && steps.analysis.outcome == 'failure'" in diagnostic
lines = textwrap.dedent(diagnostic.split("        run: |\n", 1)[1]).strip().splitlines()
assert lines[0] == "python3 - <<'PY'" and lines[-1] == "PY"
program = compile("\n".join(lines[1:-1]), "<CI Sonar failure diagnostic>", "exec")
for report, task, gate, failure, expected in (
    (None, {}, {}, None, "No scanner task report"),
    ("ceTaskId=../../other", {}, {}, None, "Invalid scanner task identifier"),
    ("ceTaskId=task-123", {}, {}, "http", "HTTP 403"),
    ("ceTaskId=task-123", {}, {}, "network", "request unavailable"),
    ("ceTaskId=task-123", {}, {}, "json", "invalid response"),
    ("ceTaskId=task-123", {"status": "FAILED", "errorMessage": "Branch denied: synthetic-sonar-credential\n::error::private"}, {}, None, "[REDACTED]"),
    ("ceTaskId=task-123", {"status": "PENDING"}, {}, None, "background task status: PENDING"),
    ("ceTaskId=task-123", {"status": "SUCCESS", "analysisId": "analysis-123"}, {"status": "OK"}, None, "quality gate status: OK"),
    ("ceTaskId=task-123", {"status": "SUCCESS", "analysisId": "analysis-123"}, {"status": "ERROR"}, None, "quality gate status: ERROR"),
    ("ceTaskId=task-123", {"status": "SUCCESS", "analysisId": "analysis-123"}, {}, "gate-http", "quality gate returned HTTP 403"),
):
    def response(request, timeout):
        if failure == "http" or (failure == "gate-http" and "qualitygates/" in request.full_url):
            raise urllib.error.HTTPError(request.full_url, 403, "synthetic-sonar-credential", {}, None)
        if failure == "network":
            raise urllib.error.URLError("synthetic-sonar-credential")
        if failure == "json":
            return io.BytesIO(b"{")
        payload = {"task": task} if "/ce/task?id=task-123" in request.full_url else {"projectStatus": gate}
        return io.BytesIO(json.dumps(payload).encode())
    output = io.StringIO()
    with patch.dict(os.environ, {"SONAR_TOKEN": "synthetic-sonar-credential"}), \
            patch.object(Path, "is_file", return_value=report is not None), \
            patch.object(Path, "read_text", return_value=report or ""), \
            patch("urllib.request.urlopen", side_effect=response), patch("sys.stdout", output):
        try:
            exec(program, {})
        except SystemExit as error:
            assert error.code in (None, 0), "diagnostic must preserve the existing scanner failure"
    assert expected in output.getvalue(), f"diagnostic lost {expected}"
    assert "synthetic-sonar-credential" not in output.getvalue(), "credential leaked in failure diagnostic"
    assert "\n::error::private" not in output.getvalue(), "remote error injected a workflow command"
print("PASS: scanner failure diagnostics distinguish processing, gate and access errors without leaking credentials")

# Exercise the CI LCOV conversion, including invalid inputs and preserved counters.
preparation = workflow.split("      - name: Prepare repository-rooted Sonar coverage\n", 1)[1]
block = preparation.split("        run: |\n", 1)[1].split("      - name:", 1)[0]
lines = textwrap.dedent(block).strip().splitlines()
program = compile("\n".join(lines[1:-1]), "<CI LCOV preparation>", "exec")
original = "TN:\nSF:src/App.tsx\nDA:3,7\nLF:1\nLH:1\nend_of_record\n"
for source, expected in ((original, original.replace("SF:src/", "SF:frontend/ecommerce-app/src/")),
                         ("", None), ("SF:../outside.ts\n", None)):
    with patch.object(Path, "read_text", return_value=source), patch.object(Path, "write_text") as write:
        try:
            exec(program, {})
        except SystemExit:
            assert expected is None
            assert not write.called
        else:
            assert write.call_args.args == (expected,), "LCOV counters or source identity changed"
print("PASS: CI LCOV conversion preserves counters and rejects missing or non-frontend sources")
PY
