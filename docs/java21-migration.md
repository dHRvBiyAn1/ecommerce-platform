# Java 21 migration and rollout

The backend now targets Java 21. The migration standardizes the build, CI, and
service images on Java 21 and selectively adopts Java 21 language and runtime
features. Virtual threads remain opt-in per application so product and cart
load tests can establish whether they help this workload before rollout.

## Toolchain

The recorded local toolchain is Oracle JDK 21.0.8 and Maven 3.9.12. The Maven
wrapper pins Maven for normal builds. On macOS, select an installed Java 21 JDK
with `/usr/libexec/java_home`:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
./mvnw -version
```

The root Maven property compiles the reactor for Java 21. CI uses Temurin 21,
and each service Dockerfile uses Java 21 for both its build and runtime image.
The shared `common` library also compiles to Java 21 bytecode, so all consumers
must run on Java 21. Existing Spring Boot, Spring Cloud, Kafka, and other
framework dependency versions are retained until testing demonstrates a
compatibility issue that requires an upgrade.

## Module disposition

| Module | Java 21 disposition |
| --- | --- |
| `common` | Shared library compiled for Java 21; no web-server thread switch. |
| `config-server` | Java 21 application; virtual threads can be opted in with `CONFIG_SERVER_VIRTUAL_THREADS`. |
| `discovery-server` | Java 21 application; virtual threads can be opted in with `DISCOVERY_SERVER_VIRTUAL_THREADS`. |
| `api-gateway` | Java 21 HTTP application; virtual threads can be opted in with `API_GATEWAY_VIRTUAL_THREADS`. |
| `auth-service` | Java 21 HTTP application; virtual threads can be opted in with `AUTH_SERVICE_VIRTUAL_THREADS`. |
| `product-service` | Java 21 HTTP application; virtual threads can be opted in with `PRODUCT_SERVICE_VIRTUAL_THREADS`. |
| `inventory-service` | Java 21 HTTP application; virtual threads can be opted in with `INVENTORY_SERVICE_VIRTUAL_THREADS`. |
| `order-service` | Java 21 HTTP application; virtual threads can be opted in with `ORDER_SERVICE_VIRTUAL_THREADS`. |
| `payment-service` | Java 21 HTTP application; virtual threads can be opted in with `PAYMENT_SERVICE_VIRTUAL_THREADS`. |
| `notification-service` | Java 21 HTTP application; virtual threads can be opted in with `NOTIFICATION_SERVICE_VIRTUAL_THREADS`. |
| `cart-service` | Java 21 HTTP application; virtual threads can be opted in with `CART_SERVICE_VIRTUAL_THREADS`. |
| `coupon-service` | Java 21 HTTP application; virtual threads can be opted in with `COUPON_SERVICE_VIRTUAL_THREADS`. |

All eleven application modules have an environment override. The global
`SPRING_THREADS_VIRTUAL_ENABLED=false` keeps virtual threads disabled unless a
service opts in. Set the applicable service variable to `true` to enable the
Spring Boot virtual-thread executor for that service. The application setting
`spring.main.keep-alive=true` keeps the JVM alive when virtual threads are the
only remaining threads.

## Feature changes and boundaries

Java 21 pattern matching for `switch` handles JWT scope variants, and record
patterns simplify token-validation branches. `ReentrantLock` protects the
token-refresh and cache critical sections. These locks intentionally span token
exchange and cache loading I/O to deduplicate concurrent work. In Java 21,
blocking while holding a `ReentrantLock` does not pin a carrier thread in the
same way that blocking inside a `synchronized` monitor can. Lock contention and
serialized work still remain and must be measured. Do not move this I/O outside
the locks as part of the migration; include contention and carrier-thread
pinning in product and cart evaluation. Cart and sample code use `getFirst()`
where the collection is known to be non-empty. Existing records and switch
expressions predate this migration and are not counted as new Java 21 features.

Persistence models and event envelopes keep their established shapes. Do not
convert those contracts to records or sealed hierarchies as part of this
migration. No preview features are enabled. Structured concurrency, scoped
values, and string templates are excluded because they are preview features in
Java 21.

Virtual threads do not change scheduled-work or Kafka behavior. Controller
scheduler configuration preserves platform-thread fixed-delay timing. Kafka
consumer concurrency remains bounded, and existing heartbeat settings remain
unchanged.

## Build and verification

Run the checks from the repository root with a JDK 21 selected:

```bash
./mvnw -B -ntp clean verify
bash scripts/check-jacoco-baseline.sh coverage-baseline/backend-modules.json
bash scripts/test-active-service-routes.sh
bash scripts/test-common-parent-dockerfiles.sh
bash scripts/test-sonar-integration.sh
bash scripts/test-java21-threading-config.sh
```

The Maven verification runs Testcontainers-backed tests when Docker is
available. Mongo integration checks that are deliberately opt-in can be run
explicitly with Docker available:

```bash
PRODUCT_MONGO_INTEGRATION=true ./mvnw -pl services/product-service -am verify
./mvnw -pl services/cart-service -am verify -Dcart.mongo.integration=true
./mvnw -pl services/notification-service -am verify -Dnotification.mongo.integration=true
```

These commands describe how to verify the migration; their inclusion here does
not assert that a particular local or CI run has passed. Record the checks actually
performed and any outstanding runtime limitations before rollout.

## Virtual-thread evaluation and rollout

Evaluate product and cart under the same representative request mix, data,
resource limits, and load profile with virtual threads disabled and enabled.
Compare throughput, latency percentiles, errors, CPU, and memory. Capture JFR
recordings and inspect for carrier-thread pinning, especially around
synchronized code and native calls. Record pinning events and the relevant
runtime metrics with the load results. These measurements have not been
pre-judged by this guide; rollout depends on measured validation.

Roll out product-service and cart-service first after validation, then use the
same evidence-based process for other services. To disable a service, unset its
service-specific override or set it to `false`, then recreate or restart that
service so Compose applies the change. The global
`SPRING_THREADS_VIRTUAL_ENABLED=false` is the fallback; it does not override an
explicit per-service value of `true`. For a JVM rollback, redeploy the last
Java 17 artifact; Java 21 bytecode and the Java 21 `common` library require Java
21 and cannot run on Java 17.

Before product rollout, investigate typed Redis cache rehydration after restart:
a populated cache has returned a map where a product DTO was expected. Validate
end-to-end notification delivery and tracing propagation explicitly; responsive
list endpoints and test-created Observation scopes do not establish those outcomes.
