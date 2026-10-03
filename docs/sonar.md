# SonarQube Cloud analysis

## CI setup

The GitHub Actions `sonar` job analyzes the existing project
`dHRvBiyAn1_ecommerce-platform` in organization `dhrvbiyan1`, using pinned
SonarScanner for Maven 5.8.0.7211. Analysis follows successful backend and
frontend verification on pushes and trusted same-repository PRs. Fork PRs do not
receive the scanner credential. A failed quality gate fails the analysis job.

- Store a current personal token with Execute Analysis permission in the
  repository's `SONAR_TOKEN` Actions secret. Do not put it in code, shell
  arguments, logs, or chat.
- Turn off Automatic Analysis on the project's
  [Analysis Method page](https://sonarcloud.io/project/analysis_method?id=dHRvBiyAn1_ecommerce-platform).
  GitHub-admin and Sonar project-admin permissions are separate.
- CI validates the token and analysis mode before scanning. Missing/invalid
  credentials and concurrent Automatic Analysis have explicit diagnostics.
- Backend JaCoCo XML and frontend LCOV artifacts come from successful test jobs.
  Frontend `SF:src/...` entries receive the repository prefix in a separate
  `sonar-lcov.info`; coverage counters and the original report are unchanged.
- The analysis job builds fresh bytecode and installs reactor dependencies with
  tests skipped. It does not repeat tests or fabricate reports; Maven supplies
  the Java analyzer's full build/dependency context.
- Both test jobs run when either backend or frontend code changes so the combined
  project has fresh reports. Image jobs still follow backend changes.
- Branch/PR identities are autodetected. Application Java remains 17; the
  scanner's provisioned JRE is separate, with the application's JDK home passed
  explicitly for Java API resolution.

Maven `scanAll` includes frontend/configuration alongside Java modules.
Generated/dependency/output directories and frontend test/E2E sources are
excluded from production-source analysis; Java tests keep Maven classification.
Security rules are not globally disabled.

## Reviewed PR #11 findings

Automatic analysis reported seven highest-priority bug/security findings.
Dispositions below are source- and test-backed; remote resolution still requires
a subsequent CI analysis.

| Rule / source | Disposition | Proof |
| --- | --- | --- |
| `javabugs:S2259`, payment transition lookup | Required journal reads fail explicitly if a save result omits its transition, before acknowledgement/outbox completion. Optional lookup paths remain nullable. | Two durability tests reproduced null dereferences before the guard; afterward both fail closed and safely replay from persisted state. |
| `java:S4502`, order/payment/product/notification/coupon chains | Reviewed, method-scoped suppression for stateless Bearer-only APIs. | Each MVC test rejects a valid JWT supplied only by cookie and a pre-authenticated session, accepts the same Bearer header, and proves no HTTP Basic filter exists. Payment webhooks use provider signatures, not browser authentication. |
| `plsql:DeleteOrUpdateWithoutWhereCheck`, coupon V3 | Reviewed, one-rule/one-immutable-migration exception: deliberately normalize every coupon before the normalized unique index. | PostgreSQL upgrade/concurrency tests cover the normalization. The applied Flyway script and checksum are unchanged; other SQL rules and migration files remain analyzed. |

CSRF exceptions do not apply to auth-service's cookie-based refresh flow. If a
reviewed API gains cookie, session, or Basic authentication, reassess CSRF before
retaining the suppression; transport tests must continue protecting that rule.

Lower-priority findings remain subject to the next coverage-aware scan. Passing
local tests or these notes do not claim a successful remote quality gate.
Historical captures retain original outcomes; new Sonar evidence must show a
successful `sonar` job, setup validation, and quality-gate step.

## Local checks

```bash
bash scripts/test-sonar-integration.sh
bash scripts/verify-release-evidence.test.sh
```

Cloud analysis uploads source to the configured service and needs an authorized
environment token. Java/frontend reports must exist before scanning.
