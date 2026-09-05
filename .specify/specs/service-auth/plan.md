# Service Authentication Implementation Plan

**Status:** Already approved; documentation of the existing slice. No brainstorming or approval round.

**Goal:** Enforce scoped machine identity on internal service calls without weakening user ownership checks or public product reads.

**Architecture:** Auth issues allowlisted short-lived RS256 service JWTs. Common code exchanges and caches them for required internal callers and exposes verified service identity/scope to controller authorization. Controllers retain ordinary permission and owner/admin paths separately from scoped machine paths.

**Tech Stack:** Java, Spring Boot/Security method proxies, JWT RS256, Feign, JUnit 5, Mockito, AssertJ, Maven reactor, configuration repository, Compose environment injection.

**Spec:** [spec.md](spec.md). Task ownership and test gates: [tasks.md](tasks.md).

## Constraints

- Preserve all existing uncommitted service-auth work; review it before editing rather than replace it wholesale.
- Work only in the specified worktree and branch. This documentation assignment owns only the four files in this directory and runs no builds, Docker, agents, or commits.
- Parent schedules all tests involving the shared reactor and owns integration and commits. No worker runs competing Maven commands or changes another owner's files.
- Do not change database DDL, indices, mappings, or transaction semantics. Token caching is local expiry-aware reuse, not a business-data cache or distributed workflow lock.
- No real secrets, shared secret fallback, user-token fallback for required internal callers, unrelated cleanup, stack stop/restart, or push.

## Flow and Interfaces

1. Task 3 supplies allowlisted client registration and OAuth JSON issuance; request uses `grant_type=client_credentials`, client credentials, and requested `scope`.
2. Task 2 consumes that wire contract through `ServiceTokenClient.requestToken(ServiceAuthProperties)` returning `ServiceTokenResponse`; `ServiceTokenProvider.getAccessToken()` supplies the bearer string to Feign. Validate and cache only usable responses, with bounded HTTP waits and early expiry refresh.
3. Task 2 owns `ServiceScopes`, JWT scope authority conversion, and `CurrentUser.isService()`/`hasAuthority(String)`. Task 1 consumes these helpers and requires service identity AND the exact scope before bypassing ordinary ownership checks.
4. Task 3 configures order, payment, and cart with their exact scope sets from the spec, client-specific environment injection, and nonblank required-client activation enforced by Task 2.
5. Task 4 independently reviews the integrated security boundaries and evidence; parent performs final verification and any approved local commit.

Keep existing Java interfaces where correct. Any required shared-interface change is coordinated with its owner before consumers edit; do not create competing helpers or silently rename properties. Task 2 and Task 3 agree property names from the existing code before wiring deployment configuration.

## Verification Gates

For each behavioral change: add a regression test, have the parent coordinate its targeted run, record the expected behavioral failure, apply the minimal fix, and rerun to green. Existing edits are not retroactive proof of TDD; missing historical evidence must be identified honestly in `progress.md`.

Parent runs this exact final command from the worktree:

```bash
./mvnw -B -ntp -pl services/common,services/auth-service,services/inventory-service,services/coupon-service,services/order-service,services/payment-service,services/cart-service -am test
```

Parent also performs static Compose validation, with placeholder values supplied for required environment variables and no secret-bearing output retained:

```bash
docker compose config --quiet
```

Static validation must verify client-specific injection as well as syntax. Do not stop, restart, or otherwise mutate the running stack. These commands are recorded here, not executed by the documentation worker.

Before a focused local security commit, parent reviews status, intended diff, recent commit style, independent review findings, and passing test results. Stage only reviewed slice files, exclude secrets and unrelated edits, and do not push. General build/re-index instructions are not executed in this docs-only assignment because of its explicit no-build and ownership constraints.
