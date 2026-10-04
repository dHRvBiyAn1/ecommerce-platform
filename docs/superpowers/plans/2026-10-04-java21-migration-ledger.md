# Java 21 migration execution ledger

Branch: `codex/java21-migration`, based on clean `main` at `51199d8b`.

The controller performed Git operations and serialized Maven verification.
Three Luna workers at a time edited disjoint files in the shared checkout.
Configuration/documentation workers used medium reasoning; security/concurrency
workers and final review used high reasoning. Workers and reviewers used
`gpt-6-luna` with self-contained briefs and no inherited conversation.

| Task | Owner / exclusive scope | Status and commit | Verification/review |
| --- | --- | --- | --- |
| Foundation | Controller: root POM, saved plan | Done — `281fd3ed` | Clean Java 21 compile across 12 modules |
| A | Luna: 11 Dockerfiles, CI, Docker/Sonar regression checks | Done — `c0129c02`; final CI/scanner assertions `67655f56` | All image builds/runtime Java checks; Docker/Sonar regressions; fresh Luna review |
| B | Luna: JWT converter, service tokens, tests | Done — `3e10ef99` | Scope/token tests, refresh deduplication/failure recovery; fresh Luna review |
| C | Luna: layered cache, cart image selection, notification samples, tests | Done — `202bf83b`; final regression `8086210b` | Cache deduplication/failure recovery, first/empty image behavior, existing-item snapshot regression; fresh Luna review |
| D | Luna: service/central YAML, Compose, environment example, threading script | Done — `4e264621` | Script and actual Compose precedence checks; fresh Luna review |
| E | Luna: platform scheduling configs/tests; gateway/product/cart HTTP tests | Done — `2e686a0f` | Both thread modes, scheduler capacities/shutdown, context isolation; fresh Luna re-review after fixture corrections |
| F | Luna: current setup/Sonar/service docs, migration guide | Done — `43232733` | All 12 module dispositions and rollback/preview boundaries; fresh Luna re-review |
| CI integration | Controller: new regression invocation | Done — `6f1d6cdf`, `67655f56` | Both threading and Docker assertions invoked in CI |
| Integration fixtures | Original C/E owners: four existing test setups | Done — `8086210b` | All explicit Mongo suites and payment signature tests pass; final Luna review |
| Final verification | Controller | Done with recorded runtime limits | 565 backend tests, unchanged coverage, actual Sonar scanner dump, shell regressions, 11 images, isolated smoke, on/off load/JFR |
| Whole branch review | Fresh Luna, high reasoning | Approved; follow-up fixes approved | No blocking code/config defects; evidence/coverage limits recorded |

Wave 1 checks passed before Wave 2 dispatch. Wave 2 checks passed before final
integration verification. Four fixture issues discovered by final verification
were routed to original owners and corrected without production behavior changes.
The cart covered-line baseline was preserved by adding an existing-item regression,
not by lowering thresholds. Final clean verification was repeated after those fixes.

The final review initially hit an account usage limit; retry after reset succeeded
using the same Luna model. No reviewer model was substituted.

[Verification report](../../java21-verification.md) records exact checks, measured
results, and runtime limitations: Redis product rehydration after restart;
notification delivery unverified despite a responsive list endpoint; actual tracing
integration unverified; local load/JFR measurements insufficient for rollout approval.
All isolated smoke containers/volumes were cleaned up. No production deployment,
push, or pull request was requested or performed.
