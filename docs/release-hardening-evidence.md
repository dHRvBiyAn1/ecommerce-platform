# Release hardening evidence — repository verification

**Verification scope:** REPOSITORY_ONLY

**State:** All 28 tasks are complete for the repository-only scope. Task 28's
verification and reviewed evidence checkpoint are recorded in commit `c5f0e941`.
The owner deferred live smoke for this GitHub-only personal project. This is
repository verification, not validation of a deployed production environment.
SonarCloud was removed from CI, Maven, and completion requirements by explicit
user instruction.

The latest successful CI run is Wave 6 commit `a4d2833e`, run
<https://github.com/dHRvBiyAn1/ecommerce-platform/actions/runs/37114276766>.
The commit-safe capture at `release-hardening-evidence/ci-run-37114276766.json`
records the backend/frontend gating steps and all nine image builds as
successful. It preserves the historical failed Sonar job and step rather than
rewriting past results. That removed integration is no longer a release gate.

Java 17 remains the backend/runtime target through completion of all waves. CI
Testcontainers run against isolated containers and does not use Docker Compose.
`release-hardening-evidence/task-records.json` contains task-specific reviewed
commit, source identity, command, and verification-result records so the
evidence check does not depend on ignored planning files.

The reconciled payment contract uses `X-Idempotency-Key`; only the first
successful customer create response returns `data.payment` and an optional
`data.clientSecret`. Stripe secrets remain backend-only, while Compose passes
the public `VITE_STRIPE_PUBLISHABLE_KEY` into the frontend build. Stripe
webhooks use `Stripe-Signature`/`STRIPE_WEBHOOK_SECRET`; the internal webhook
uses `X-Webhook-Signature`/`PAYMENT_WEBHOOK_SECRET`. The Postman collection
stores payment fields from `data.payment` and does not retain `clientSecret`.
Its order, payment, and refund requests persist a separate idempotency key and
reuse it until that operation's variable is explicitly cleared.

## Checker format

The verifier uses Bash, Python 3, and Python's standard library only. Keep the
`## Task records` and `## Gate records` tables and exact headers. A task row's
`task:<id>` reference resolves to that task's unique record in
`release-hardening-evidence/task-records.json`, including its commit, review
outcome, source identity, command, and observed result. Gate rows cite only the
captured `ci:` run artifact, a `smoke:` transcript artifact, or an identified
task record; arbitrary existing files, README links, and test/source scripts
are not result evidence. CI gates are checked against the captured run's actual
job and gating-step conclusions. A live smoke PASS requires a captured
configured GET health/OpenAPI transcript with both 2xx responses; the smoke
unit suite is not a live result. The smoke JSON records capture time/provenance,
command and exit status, configured base URL, both probe URLs/statuses, and
explicit endpoint overrides, and captured stdout; recorded probe URLs must
match the base defaults or those overrides. It omits bearer tokens and URL
queries/fragments. New captures do not require a Sonar job; historical captures
may retain one. Removed `sonar-external` gate rows are rejected as obsolete.
For the explicit `REPOSITORY_ONLY` scope, smoke may be `DEFERRED` only when
`smoke-deferral.json` records the project owner's decision, reason, and required
follow-up before production deployment. Default deployed-release verification
still requires actual live smoke PASS evidence.

## Task records

| Task | Status | Evidence |
| --- | --- | --- |
| 1 | COMPLETE | `task:1` |
| 2 | COMPLETE | `task:2` |
| 3 | COMPLETE | `task:3` |
| 4 | COMPLETE | `task:4` |
| 5 | COMPLETE | `task:5` |
| 6 | COMPLETE | `task:6` |
| 7 | COMPLETE | `task:7` |
| 8 | COMPLETE | `task:8` |
| 9 | COMPLETE | `task:9` |
| 10 | COMPLETE | `task:10` |
| 11 | COMPLETE | `task:11` |
| 12 | COMPLETE | `task:12` |
| 13 | COMPLETE | `task:13` |
| 14 | COMPLETE | `task:14` |
| 15 | COMPLETE | `task:15` |
| 16 | COMPLETE | `task:16` |
| 17 | COMPLETE | `task:17` |
| 18 | COMPLETE | `task:18` |
| 19 | COMPLETE | `task:19` |
| 20 | COMPLETE | `task:20` |
| 21 | COMPLETE | `task:21` |
| 22 | COMPLETE | `task:22` |
| 23 | COMPLETE | `task:23` |
| 24 | COMPLETE | `task:24` |
| 25 | COMPLETE | `task:25` |
| 26 | COMPLETE | `task:26` |
| 27 | COMPLETE | `task:27` |
| 28 | COMPLETE | `task:28` |

Task 27's reviewed commit `a4d2833e` is recorded in its supporting task record.
Task 28's direct verification results and coverage counters are captured in
[final-verification.json](release-hardening-evidence/final-verification.json).

## Gate records

| Gate | Status | Evidence |
| --- | --- | --- |
| ci-run | SUCCESS | `ci:ci-run-37114276766.json` |
| backend-tests | PASS | `ci:ci-run-37114276766.json` |
| backend-coverage | PASS | `ci:ci-run-37114276766.json` |
| frontend-tests | PASS | `ci:ci-run-37114276766.json` |
| frontend-coverage | PASS | `ci:ci-run-37114276766.json` |
| testcontainers | PASS | `ci:ci-run-37114276766.json` |
| smoke | DEFERRED | `smoke-deferral:smoke-deferral.json` |
| reviewers | SIGNED_OFF | `tasks:all` |
| no-compose | DECLARED | `task:26` |
| image:api-gateway | SUCCESS | `ci:ci-run-37114276766.json` |
| image:auth-service | SUCCESS | `ci:ci-run-37114276766.json` |
| image:product-service | SUCCESS | `ci:ci-run-37114276766.json` |
| image:inventory-service | SUCCESS | `ci:ci-run-37114276766.json` |
| image:order-service | SUCCESS | `ci:ci-run-37114276766.json` |
| image:payment-service | SUCCESS | `ci:ci-run-37114276766.json` |
| image:notification-service | SUCCESS | `ci:ci-run-37114276766.json` |
| image:cart-service | SUCCESS | `ci:ci-run-37114276766.json` |
| image:coupon-service | SUCCESS | `ci:ci-run-37114276766.json` |

The repository reviewer gate is signed off. Earlier tasks retain their
independent review outcomes. The user
explicitly requested no subagents for Task 28; its final verification and
evidence review were performed directly by the controller, not represented as
an independent subagent sign-off.

## Task 27 checks and reconciliation

| Check | Result |
|---|---|
| `bash scripts/verify-release-evidence.test.sh` | Passed: self-contained synthetic complete evidence accepted; README citations, mocked smoke source, task/run identity mismatches, missing job steps, and incomplete transcripts rejected. Sonar-removal regression also checks captures without the removed job and truthful historical captures. |
| `bash scripts/smoke-release.test.sh` | Passed: bounded GET probes, Bearer header via stdin (not curl argv), redacted token/URL output, CR/LF rejection, and no Docker invocation. |
| `node scripts/test-postman-idempotency.js` | Passed: per-operation order/payment/refund keys (including E2E keys) remain stable on retry and rotate only after explicit reset. |
| `bash scripts/verify-release-evidence.sh docs/release-hardening-evidence.md` | Repository-scope verification requires Task 28's reviewed evidence commit and the explicit owner-approved smoke deferral record. |
| Configured live health/OpenAPI smoke | DEFERRED by owner; no deployment URL or bearer value was supplied. No successful live result is claimed. |
| Sonar integration | Removed from CI, Maven, and release-completion criteria at the user's request; historical captures remain unchanged. |

Gateway health and the default OpenAPI URL (`GET /v3/api-docs/swagger-config`)
both require a nonblank Authorization header. `SMOKE_BEARER_TOKEN` now supplies
the Bearer header to both GET probes, rejects CR/LF, and is not printed or
persisted. Before production deployment, configure a real target and run the
live probes. The current owner-approved deferral is not a substitute for those
actual requests.

## Task 28 direct verification

Verified application revision: `a4d2833e504b8d1d78dc3d7e7d05e4befb9af3e2`.
All commands below were run directly on 2026-10-03, without subagents.
Maven explicitly selected the installed Java 17 runtime to match CI and images:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v17) \
PATH="$(/usr/libexec/java_home -v17)/bin:$PATH" \
./mvnw -B -ntp -fae clean verify
```

| Command | Observed result |
| --- | --- |
| `./mvnw -B -ntp -fae clean verify` | BUILD SUCCESS, all 13 reactor modules; 88 Surefire reports contain 541 tests, 0 failures, 0 errors, 3 skips. |
| `bash scripts/check-jacoco-baseline.sh coverage-baseline/backend-modules.json` | JaCoCo baseline and critical-class checks passed; unchanged exact module ratchets and all 11 critical 80% LINE/BRANCH thresholds. |
| `npm --prefix frontend/ecommerce-app ci --no-audit --no-fund` | Exit 0; 669 packages installed. |
| `npm --prefix frontend/ecommerce-app run typecheck` | Exit 0; TypeScript no-emit check passed. |
| `npm --prefix frontend/ecommerce-app run lint` | Exit 0; ESLint passed. |
| `npm --prefix frontend/ecommerce-app run test:coverage` | 9 test files, 30 tests passed. |
| `node frontend/ecommerce-app/scripts/check-coverage-baseline.mjs` | Coverage baseline passed for 81 modules. |
| `npm --prefix frontend/ecommerce-app run build` | Exit 0; 2,245 modules transformed and production assets generated. |
| `npm --prefix frontend/ecommerce-app exec playwright test` | 1 passed: declined card followed by retry with the same checkout attempt. |
| `bash scripts/test-common-parent-dockerfiles.sh` | All nine atomic parent/common/service build invariants and CI image entries passed. |
| `bash scripts/check-jacoco-baseline.test.sh` | Checker fixtures passed, including expected negative-case rejections. |
| `bash scripts/smoke-release.test.sh` | Read-only probes, bearer stdin delivery/redaction, bounded requests, and failure propagation passed; not live deployment evidence. |
| `bash scripts/verify-release-evidence.test.sh` | Synthetic complete evidence accepted; unsupported, incomplete, mismatched, and false live-smoke records rejected. |
| `bash scripts/test-frontend-stripe-build-arg.sh` | Public Stripe key build-argument wiring passed. |
| `node scripts/test-postman-idempotency.js` | Stable retry keys, per-operation separation, and explicit-reset rotation passed. |
| `make help` | Exit 0; smoke targets listed without service lifecycle actions. |
| Configured live smoke | DEFERRED: owner approved repository-only scope; `SMOKE_BASE_URL` is not configured. |
| Evidence scope | Repository checks verified; deployed-production validation explicitly deferred. |

Every previously completed task's recorded commit was checked against current
Git ancestry. The fresh backend critical counters and command output excerpts
are in `release-hardening-evidence/final-verification.json`; the coverage policy
still uses 80% line gates for named frontend critical modules and baseline-ratcheted
branch counters, without a new blanket frontend percentage requirement.

**No-Compose declaration:** database and Kafka integration suites used their own
isolated Testcontainers. No Docker Compose lifecycle command, live-service
mutation, or data reset was performed for this verification.

## Controller final evidence review and residuals

- Task 27's specification/quality findings are closed: stable Postman retry keys,
  bearer values supplied via stdin, redacted probe output, and claim-specific
  evidence checks. Its reviewed commit is present in current ancestry.
- The recorded CI job and step outcomes match the queried run; successful
  backend/frontend/image jobs do not hide the failed non-blocking Sonar job.
- Unit/isolated E2E smoke fixtures are not relabeled as configured deployment
  probes. No live smoke PASS is claimed; the owner-approved deferral has its own
  structured supporting record.
- Existing non-blocking React `act(...)` warnings and dependency deprecation
  notices were observed; no application/dependency changes were made in FINAL.
- Earlier reviews parked notification query/index scaling and order optimistic
  conflict-loop performance concerns. They remain recorded residuals, not new
  correctness claims or unverified release fixes.
- Repository-only sign-off records the revised scope and preserves mandatory
  live smoke as a pre-production follow-up. Java 21 migration remains a separate
  task after repository hardening waves are complete, as requested.

## Deferred deployment validation

1. Configure an existing deployment's `SMOKE_BASE_URL`, optional endpoint
   overrides, and `SMOKE_BEARER_TOKEN` where required. Run the read-only probe and
   capture both actual 2xx responses without recording credentials.
2. Record the actual live capture and switch verification back to the deployed
   release scope before claiming production deployment validation. The smoke
   deferral must not be relabeled as a successful probe.
