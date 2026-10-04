# Java 21 Migration with Parallel Luna Subagents

## Goal and architecture

Migrate all 12 backend modules and 11 service images to Java 21, adopt useful stable features, and add opt-in virtual threads. Java 21.0.8 is installed and active; Maven 3.9.12 and existing dependencies are retained unless verification demonstrates incompatibility.

Work on `codex/java21-migration` in the shared checkout. Three concurrent `gpt-6-luna` workers plus the controller occupy the four available slots. Spawn isolated briefs (`fork_turns=none`), medium reasoning for configuration/docs and high for security/concurrency. All reviewers also use Luna. Workers exclusively own assigned files; only the controller runs Git operations and Maven builds/tests. Serialize builds to avoid shared target-directory races. Every task returns changed files, requested checks, and concerns. The controller reviews, verifies and commits each wave before the next.

## Global constraints

- Stable Java 21 features only; no preview flags, new public interfaces, database migrations, production deployment, or unrelated refactoring.
- Preserve REST/OpenAPI, Kafka payloads, persistence models, validation messages, authentication isolation, tracing and failure behavior.
- `SPRING_THREADS_VIRTUAL_ENABLED=false` by default, maps to `spring.threads.virtual.enabled`; keep `spring.main.keep-alive=true`.
- Preserve fixed-delay schedules, bounded Kafka publisher executor and notification heartbeat executor.
- User explicitly authorizes parallel Luna implementation/review and shared-checkout branch work; these override skill defaults for sequential dispatch or worktree consent.

## Foundation — controller

- [ ] Create branch from clean main and initialize ledger.
- [ ] Set parent java.version=21; retain release configuration and remove source/target duplication.
- [ ] Run clean Java 21 compilation before Wave 1.

## Wave 1 — parallel

### Task A: Containers and CI (medium)
Own all service Dockerfiles, .github/workflows/ci.yml, scripts/test-common-parent-dockerfiles.sh and scripts/test-sonar-integration.sh. Use eclipse-temurin:21-jdk-jammy / 21-jre-jammy. Both CI JDK selections use 21. Build all eleven images including config/discovery; retain nine common-consuming parent checks and add version coverage for all eleven Dockerfiles. Update Sonar Java assertion.

### Task B: Shared security and token handling (high)
Own JwtAuthenticationConverter, ServiceTokenProvider and corresponding tests. Pattern switch for string/collection/null/unexpected scope claims, record patterns for ServiceTokenResponse validation, ReentrantLock around token refresh. Preserve expiry, scopes, authority semantics, validation messages and refresh deduplication. Add deterministic virtual-thread contention and failure-release checks.

### Task C: Cache and collections (high)
Own LayeredCache, CartService product-image selection, notification SampleDataInitializer and associated tests. ReentrantLock preserves existing cache-wide serialized loading and failure semantics. getFirst() for guarded cart images and sample-data selection, preserving null/empty handling. Add deterministic concurrent cache loading and failure-release checks.

Controller runs task-specific tests and Docker/Sonar scripts, clean reactor compile, fresh scoped Luna reviews and commits before Wave 2.

## Wave 2 — parallel

### Task D: Runtime configuration (medium)
Own service application YAML, central YAML, Compose, .env.example and scripts/test-java21-threading-config.sh. Support disabled-default opt-in in all eleven services even without Config Server; global and service-specific Compose overrides, keep-alive. Script verifies defaults, overrides, image/runtime/config invariants. Controller alone adds script to CI after checks pass.

### Task E: Scheduling and runtime tests (high)
Own new scheduler configurations/tests in order/payment/notification and new HTTP threading tests in gateway/product/cart. Explicit ThreadPoolTaskScheduler preserves existing configured pool size and fixed-delay platform scheduling. Keep other executors unchanged. Real embedded HTTP proves Thread.isVirtual on/off, request security context isolation, tracing and shutdown. Tests use isolated infrastructure or minimal real Boot web contexts, never production services.

### Task F: Documentation and feature coverage (medium)
Own current root/service README files, docs/sonar.md and docs/java21-migration.md. Document installed JDK, migration, all twelve module dispositions, opt-in configuration/rollout, testing and rollback. Preserve historical release evidence. Explain existing records/switch expressions and excluded preview/entity/event-envelope rewrites.

Controller runs configuration, HTTP and scheduler tests, reviews each task with fresh Luna, commits, then adds verified script to CI.

## Final integration and acceptance

- [ ] Run ./mvnw -B -ntp -fae clean verify and existing coverage baseline.
- [ ] Enable PRODUCT_MONGO_INTEGRATION=true, cart.mongo.integration=true, notification.mongo.integration=true and verify opt-in suites.
- [ ] Run existing Sonar, Dockerfile, routing, environment and release regression checks.
- [ ] Build all eleven images; report runtime Java versions and isolated smoke coverage for authentication/cart/checkout/inventory/coupons/sandbox payments/notifications.
- [ ] Compare product/cart virtual threads off/on with identical workloads; record latency, throughput, errors, memory, connection-pool pressure and JFR pinning. No readiness claim from compilation alone.
- [ ] Fresh whole-branch Luna high-reasoning review, fixes to original owners, rerun affected checks.

All modules target 21, all service images run 21, CI covers complete migration, contracts stay compatible, virtual threads opt-in and tested, runtime limitations accurately recorded. Default rollout is product/cart first after load validation, then services individually. Disable threading via configuration; revert Java by redeploying previous Java 17 artifacts.
