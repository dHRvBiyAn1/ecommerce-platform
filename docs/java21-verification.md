# Java 21 migration verification — 2026-10-04

Implementation is on `codex/java21-migration`. Code/config revision `67655f56`
passed the checks below. All 12 backend modules target Java 21, all 11 service
images built successfully and reported Java 21 at runtime, and virtual threads
remain disabled by default. This is local migration evidence, not production
rollout approval. Numeric results are in [java21-verification.json](java21-verification.json).

## Build and regression evidence

The controller ran Maven sequentially with Oracle JDK 21.0.8 and Maven 3.9.12:

```bash
DOCKER_HOST=unix:///Users/dhruv/.docker/run/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
PRODUCT_MONGO_INTEGRATION=true \
./mvnw -B -ntp -fae \
  -Dcart.mongo.integration=true -Dnotification.mongo.integration=true clean verify
bash scripts/check-jacoco-baseline.sh coverage-baseline/backend-modules.json
```

All 565 backend tests passed with no failures, errors, or skips. Existing coverage
baselines and the 11 critical-class thresholds passed without baseline changes.
This includes Docker-backed authentication, order/payment Mongo/Kafka, and the
explicitly enabled product/cart/notification Mongo suites. New tests cover token
refresh/cache load deduplication and recovery after exceptions; embedded HTTP
requests in both threading modes; authentication context isolation in product
and cart; Observation scope restoration; platform scheduler capacity, fixed-delay
callbacks, and shutdown in order/payment/notification.

The following regression checks passed:

- Dockerfile parent/common build invariants for nine consumers and Java 21 image
  assertions for all 11 services; threading configuration and actual Compose
  disabled defaults/global flag/service-specific override precedence.
- Sonar integration contract and actual Maven scanner scope/property dump, using
  real frontend LCOV from 30 passing tests in nine files. The dump test contacts
  no Sonar server; a hosted Sonar analysis/quality gate was not run here.
- Active service routing, coverage checker fixtures, release-evidence fixtures,
  and environment/release smoke fixtures. `scripts/test-env.sh` also passed
  authenticated health/OpenAPI probes against the isolated gateway.

Four existing test-fixture problems surfaced during verification and were fixed:
product/cart duplicated their application's Mongo auditing registration; a raw
notification Mongo client lacked UUID representation; a directly constructed
payment controller lacked its injected 300-second signature tolerance. Production
code and schema were unchanged by those corrections. A new cart regression covers
existing-item quantity, refreshed price/image snapshot, and coupon invalidation;
this also preserves the existing covered-line baseline after `getFirst()` removed
one source line.

## Isolated application smoke

All 11 application containers were healthy. Temporary credentials, RSA keys,
loopback-only random ports, and separate volumes were used; the test stack and
its volumes were removed afterwards. No production deployment occurred.

Registration/password grant, authoritative cart snapshots, SAVE10 validation,
checkout/inventory reservation, sandbox payment processing, Kafka-driven order
confirmation, and authenticated gateway product routing passed. The notification
list API responded, but remained empty after a 45-second polling window. End-to-end
notification event delivery and email transport are **unverified**, even though
notification unit and Mongo integration suites passed. Resolve or explain this
runtime observation before rollout; do not count endpoint access as delivery.

A product restart with an existing Redis cache entry produced a
`LinkedHashMap` → `ProductResponse` class-cast error on product detail requests.
The cache serializer/rehydration path is unchanged by this migration; preserving
serialization was an explicit scope constraint. Clearing the affected entry in
the isolated Redis while the product process was stopped allowed validation to
continue. This is not an automatic rollout remedy: typed Redis rehydration and
restart behavior need separate investigation before product rollout. The
`ReentrantLock` change preserves the existing cache-wide serialization of misses.

## Virtual-thread comparison

Both modes used the same Docker images, persisted customer/cart/product, 768 MiB
service limits, 45% JVM heap limit, 200 warm-up requests, and 6,000 measured GETs
per service at concurrency 12. Product detail exercises a warmed local cache;
cart reads exercise Mongo. Product/cart were recreated between modes, and the
isolated product Redis entry was cleared before each restart to avoid the known
rehydration failure. The final pair ran after Maven/frontend test activity ended.

| Service | Mode | Requests/s | p50 ms | p95 ms | Errors | Memory after load |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Product | Platform | 2342.50 | 4.39 | 9.55 | 0 | 427.6 MiB |
| Product | Virtual | 1763.61 | 5.69 | 12.60 | 0 | 417.7 MiB |
| Cart | Platform | 1700.42 | 6.39 | 11.80 | 0 | 366.8 MiB |
| Cart | Virtual | 1314.21 | 8.21 | 16.22 | 0 | 360 MiB |

Mongo pool measurements were sampled every 100 ms through authenticated Actuator
metrics exposed only in the test Compose configuration. Product's cached workload
had a pool size of one and no sampled checkouts/waiters. Cart sampled maxima were
5 checked out / 8 pool size / 2 waiting with platform threads, and 11 checked out /
12 pool size / 2 waiting with virtual threads. These are sampled maxima, not an
exhaustive count of transient contention.

JFR `profile` recordings from both services/modes contained zero
`jdk.VirtualThreadPinned` events (the profile's default 20 ms threshold). This
means no qualifying pinning events were recorded in this small workload; it does
not rule out shorter pinning or other workloads. Recordings remain in the local
`/tmp/java21-verification/` directory; credentials and JFR binaries are excluded
from the committed report.

Platform mode was faster in this single local run. The few-second read-only runs,
fixed mode order, shared Docker host, and limited workload do not support a
production performance conclusion. Repeat with sustained representative traffic,
mixed reads/writes, token refresh/cache misses, multiple trials, and pool saturation
before enabling product/cart. Keep the disabled default in the meantime.

The HTTP tests verify real Tomcat thread mode and test-created Micrometer
Observation scope cleanup. The project currently has no Micrometer Tracing
implementation dependency; exported traces, tracing-context propagation through
asynchronous executors, and a full production filter chain are **not verified** by
those fixtures. No tracing dependency was added solely to expand migration scope.

Fresh high-reasoning Luna review found no blocking code/config defects. Its CI
Dockerfile-check omission and stale Java 17 scanner assertion were fixed and
re-reviewed. The concurrency tests exercise multiple virtual callers but do not
deterministically prove every waiter is blocked on the lock before release;
stronger contention stress remains useful for rollout validation.
