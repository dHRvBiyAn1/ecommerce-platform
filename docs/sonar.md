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

The `sonar-analysis` Maven profile selects the frontend analysis-only POM at
`frontend/ecommerce-app/pom.xml`, which explicitly declares `sonar.sources=src`.
The pinned scanner ignores compile-source roots on `pom` projects, so the earlier
root build-helper registration did not include the frontend. Explicit frontend
scope replaces that ineffective registration. Java modules use their native
Maven source/test roots and each imports its own JaCoCo XML. The root POM and
GitHub Actions configuration retain their scanner-default scope. Normal builds
do not select the frontend analysis module.
Generated/dependency/output directories and frontend test/E2E sources are
excluded from production-source analysis; Java tests keep Maven classification.
Security rules are not globally disabled.

All GitHub Actions references are pinned to verified full commit SHAs. Check
publishing and artifact-read permissions are job-scoped. npm installs use
`--ignore-scripts`; browser setup invokes the lockfile-installed Playwright CLI
directly, with no on-demand package download through `npx`.

## Reviewed PR #11 findings

Automatic analysis reported seven highest-priority bug/security findings.
Dispositions below are source- and test-backed; remote resolution still requires
a subsequent CI analysis.

| Rule / source | Disposition | Proof |
| --- | --- | --- |
| `javabugs:S2259`, payment transition lookup | Required journal reads fail explicitly if a save result omits its transition, before acknowledgement/outbox completion. Optional lookup paths remain nullable. | Two durability tests reproduced null dereferences before the guard; afterward both fail closed and safely replay from persisted state. |
| `java:S4502`, order/payment/product/notification/coupon chains | Reviewed, method-scoped suppression for stateless Bearer-only APIs. | Each MVC test rejects a valid JWT supplied only by cookie and a pre-authenticated session, accepts the same Bearer header, and proves no HTTP Basic filter exists. Payment webhooks use provider signatures, not browser authentication. |
| `plsql:DeleteOrUpdateWithoutWhereCheck`, coupon V3 | Reviewed, one-rule/one-immutable-migration exception: deliberately normalize every coupon before the normalized unique index. | PostgreSQL upgrade/concurrency tests cover the normalization. The applied Flyway script and checksum are unchanged; other SQL rules are not suppressed. |

CSRF exceptions do not apply to auth-service's cookie-based refresh flow. If a
reviewed API gains cookie, session, or Basic authentication, reassess CSRF before
retaining the suppression; transport tests must continue protecting that rule.

Lower-priority findings remain subject to the next coverage-aware scan. Passing
local tests or these notes do not claim a successful remote quality gate.
Historical captures retain original outcomes; new Sonar evidence must show a
successful `sonar` job, setup validation, and quality-gate step.

## First CI scan follow-up

PR run `37148517503` successfully validated the new credential/analysis mode and
uploaded analysis. The original seven priority findings cleared; Java new-code
coverage was 81.5% (passing the 80% gate), while remaining reliability/security
conditions failed on workflow supply-chain controls and the cache reference.

The follow-up pins action SHAs, scopes permissions, uses locked local package
execution, and explicitly publishes the immutable cached token through an
`AtomicReference` while retaining synchronized refresh/coalescing. All 65 common
tests pass, including deterministic concurrent exchange and expiry checks.
Clean frontend installation with lifecycle scripts ignored passes typecheck,
lint, 30 tests, coverage, Playwright, and production build. Remote acceptance
still requires reanalysis of this follow-up, including frontend source/coverage.

## Source-scope correction and branch diagnostics

Commit `618985b3` passed PR CI run `37196512112` and its Sonar quality gate:
Java new-code coverage was 81.5%, with A ratings and no new bugs or vulnerabilities.
However, scanner logs and the public component API still showed no frontend
files. That gate does not establish frontend analysis or LCOV import.

`scripts/test-sonar-maven-scope.sh` exercises the real pinned scanner in local
simulation mode against a loopback address, without credentials or a source
upload. It builds fresh analysis bytecode and verifies actual frontend source
selection, LCOV location, Java test classification, and module-local XML paths.
It also verifies that normal builds exclude the frontend analysis module.
CI runs this proof before the authenticated cloud analysis.

The separate push run `37196509610` uploaded branch analysis but failed while
waiting for the quality gate with an authorization/project error; PR analysis
succeeded with the same credential. A failure-only diagnostic now queries the
scanner's background task and, if processing succeeded, its analysis-specific
quality gate. It reports processing/access failures without printing credentials
or changing the failed scanner outcome. Remote source/coverage acceptance and
the cause of branch failure remain pending the next scan.

## Local checks

```bash
bash scripts/test-sonar-integration.sh
bash scripts/verify-release-evidence.test.sh
# Requires Java 17; installs reactor artifacts but does not upload analysis:
bash scripts/test-sonar-maven-scope.sh
```

Cloud analysis uploads source to the configured service and needs an authorized
environment token. Java/frontend reports must exist before scanning.
