# Release hardening evidence — Wave 6 partial

**State:** Partial; Task 27 specification and quality reviews are approved, but
its focused fix-round commit ID is not recorded. Task 28 final verification has
not started. This is not release-complete evidence.

The successful Wave 5 CI run is commit `29789440`, run
<https://github.com/dHRvBiyAn1/ecommerce-platform/actions/runs/37071472807>.
The commit-safe capture at `release-hardening-evidence/ci-run-37071472807.json`
records the backend/frontend gating steps and all nine image builds as
successful. It also records the Sonar job and SonarCloud step as failed; overall
workflow success does not mean Sonar analysis was accepted. Treat Sonar as a
release blocker until a later captured run shows the Sonar job and step passing.

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
queries/fragments. `sonar-external` is accepted only when its
captured job and SonarCloud step both succeeded. `BLOCKED` is recorded truthfully
but fails release completeness.

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
| 27 | PARTIAL | `task:27` |
| 28 | PENDING | `task:28` |

Task 27 review approvals are recorded in its structured task record. It remains
`PARTIAL` until Task 28 records the focused fix commit and final evidence.

## Gate records

| Gate | Status | Evidence |
| --- | --- | --- |
| ci-run | SUCCESS | `ci:ci-run-37071472807.json` |
| backend-tests | PASS | `ci:ci-run-37071472807.json` |
| backend-coverage | PASS | `ci:ci-run-37071472807.json` |
| frontend-tests | PASS | `ci:ci-run-37071472807.json` |
| frontend-coverage | PASS | `ci:ci-run-37071472807.json` |
| testcontainers | PASS | `ci:ci-run-37071472807.json` |
| smoke | NOT_RUN | `NOT_RUN` |
| reviewers | PARTIAL | `tasks:all` |
| sonar-external | BLOCKED | `ci:ci-run-37071472807.json` |
| no-compose | DECLARED | `task:26` |
| image:api-gateway | SUCCESS | `ci:ci-run-37071472807.json` |
| image:auth-service | SUCCESS | `ci:ci-run-37071472807.json` |
| image:product-service | SUCCESS | `ci:ci-run-37071472807.json` |
| image:inventory-service | SUCCESS | `ci:ci-run-37071472807.json` |
| image:order-service | SUCCESS | `ci:ci-run-37071472807.json` |
| image:payment-service | SUCCESS | `ci:ci-run-37071472807.json` |
| image:notification-service | SUCCESS | `ci:ci-run-37071472807.json` |
| image:cart-service | SUCCESS | `ci:ci-run-37071472807.json` |
| image:coupon-service | SUCCESS | `ci:ci-run-37071472807.json` |

The overall reviewer gate remains `PARTIAL` because Task 28's final review and
evidence are not complete, even though Task 27's specification and quality
round-one reviews are approved.

## Task 27 checks and reconciliation

| Check | Result |
|---|---|
| `bash scripts/verify-release-evidence.test.sh` | Passed: self-contained synthetic complete evidence accepted; README citations, mocked smoke source, failed Sonar, task/run identity mismatches, missing job steps, and incomplete transcripts rejected. |
| `bash scripts/smoke-release.test.sh` | Passed: bounded GET probes, Bearer header via stdin (not curl argv), redacted token/URL output, CR/LF rejection, and no Docker invocation. |
| `node scripts/test-postman-idempotency.js` | Passed: per-operation order/payment/refund keys (including E2E keys) remain stable on retry and rotate only after explicit reset. |
| `bash scripts/verify-release-evidence.sh docs/release-hardening-evidence.md` | Expected to fail until Task 28 fills all task records and release gates. |
| Configured live health/OpenAPI smoke | Not run; no deployment URL or bearer value was supplied. The smoke interface now accepts `SMOKE_BEARER_TOKEN` for both protected gateway endpoints. |
| Sonar external action | BLOCKED; do not interpret the non-blocking CI result as accepted analysis. |

Gateway health and the default OpenAPI URL (`GET /v3/api-docs/swagger-config`)
both require a nonblank Authorization header. `SMOKE_BEARER_TOKEN` now supplies
the Bearer header to both GET probes, rejects CR/LF, and is not printed or
persisted. Task 28 still needs to run the configured live smoke and record its
actual result; this evidence remains `NOT_RUN` until then.
