# Spring Boot 4 and Jackson 3 migration

Version research date: 2026-10-10. Java 21 remains the LTS runtime and Maven
3.9.12 remains the wrapper. Libraries do not all publish an LTS channel: use the
latest compatible stable release, excluding milestones, betas and snapshots.
Spring Boot and Spring Cloud BOMs own their transitive versions.

## Dependency decisions and official references

| Component | Selected version | Reference and decision |
| --- | --- | --- |
| Spring Boot | 4.1.1 | [Migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), [4.1 release notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes), [support policy](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions). Latest stable at research time; retain Java 21. |
| Spring Cloud | 2025.1.3 | [Compatibility matrix](https://spring.io/projects/spring-cloud/). Compatible stable train for Boot 4.1; exclude 2026.0 milestones. |
| Jackson | 3.1.5, Boot managed | [Migration guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md), [Boot JSON support](https://docs.spring.io/spring-boot/reference/features/json.html). Application mappers, HTTP, Redis and Kafka use Jackson 3. |
| Springdoc | 3.1.1 | [Compatibility and configuration](https://springdoc.org/). Boot 4 compatible; explicitly retain OpenAPI 3.0 output. |
| OpenAPI Generator | 7.26.0 | [Spring generator](https://openapi-generator.tech/docs/generators/spring/), [Java generator](https://openapi-generator.tech/docs/generators/java/). Enable `useSpringBoot4`, `useJackson3` and Jakarta annotations. |
| Jackson nullable | 0.2.12, test scope | [Releases](https://github.com/OpenAPITools/jackson-databind-nullable/releases). Jackson 3 support; generated HTTP test clients only. |
| MapStruct | 1.6.3 | [Stable reference](https://mapstruct.org/documentation/stable/reference/html/). Latest stable; exclude 1.7 beta. Supports records and Lombok builders. |
| Lombok / MapStruct binding | 1.18.48 / 0.2.0 | [Lombok changelog](https://projectlombok.org/changelog), [MapStruct integration](https://mapstruct.org/documentation/stable/reference/html/#_lombok). Binding coordinates annotation processing. |
| JJWT | 0.13.0 | [Documentation](https://github.com/jwtk/jjwt/blob/0.13.0/README.adoc). Preserve token/security contracts; its Jackson adapter still requires Jackson 2. |
| Stripe Java | 34.0.0 | [Releases](https://github.com/stripe/stripe-java/releases). API version `2026-09-30.endive`; external provider validation remains a rollout task. |
| Bucket4j | 8.21.0 | [Official project](https://github.com/bucket4j/bucket4j). Preserve Redis rate limits; JDK 17 artifacts support Java 21. |
| ShedLock | 7.10.1 | [Compatibility](https://github.com/lukas-krecan/ShedLock). Supports Spring 7; retain distributed scheduler locks. |
| Caffeine | 3.3.0 | [Releases](https://github.com/ben-manes/caffeine/releases). Preserve existing eviction/loading semantics. |
| Testcontainers | 2.0.5, Boot managed | [Documentation](https://java.testcontainers.org/). Renamed 2.x artifacts; unused Mongo, Kafka and Elasticsearch test modules removed. |
| JaCoCo | 0.8.15 | [Changes](https://www.jacoco.org/jacoco/trunk/doc/changes.html). Coverage thresholds remain unchanged. |
| CycloneDX Maven | 2.9.3 | [Official plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin). Retain SBOM generation. |
| Spotless | 3.10.4 | [Maven documentation](https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md). Formatting remains enforced during compilation. |
| Google Java Format | 1.36.1 | [Release](https://github.com/google/google-java-format/releases/tag/v1.36.1). 1.37 changes its Style enum to a record, incompatible with [Spotless's adapter](https://raw.githubusercontent.com/diffplug/spotless/main/lib/src/googleJavaFormat/java/com/diffplug/spotless/glue/java/GoogleJavaFormatFormatterFunc.java). Revisit when supported. |
| Sonar Maven scanner | 5.8.0.7211 | [Documentation](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/scanners/sonarscanner-for-maven). Already current; unchanged. |

The [Boot dependency coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
own Framework, Security, Data, Kafka, Hibernate, validation, database drivers,
Flyway, logging, tracing, JUnit and Mockito versions. Removed stale Kafka,
Testcontainers, Micrometer and OpenTelemetry overrides so the BOM can provide a
tested set. Removed unused Loki, TOTP and Commons Codec declarations.
Focused test starters supply generic test support transitively. Removed duplicate
generic starters, unused Kafka/JPA test utilities, Mongo slice support from
modules without slices, and test dependencies from infrastructure modules with
no tests. Real Kafka integration uses Testcontainers and the production client.

Boot 4.1.1 resolves Framework 7.0.9, Security 7.1.1, Spring Data BOM 2026.0.1,
Spring Kafka 4.1.1 with Kafka clients 4.2.1, Hibernate 7.4.5.Final, Validator
9.1.3.Final, Flyway 12.4.0, MongoDB driver 5.8.1, PostgreSQL JDBC 42.7.13,
Lettuce 7.5.2.RELEASE, Micrometer 1.17.1, Tracing 1.7.1, OpenTelemetry 1.62.0,
Logback 1.5.38, Jupiter 6.0.3 and Mockito 5.23.0. These versions come from the
published BOM, rather than independent version overrides.

| Managed integration | Official documentation | Choice |
| --- | --- | --- |
| Security | [Reference](https://docs.spring.io/spring-security/reference/index.html) | Retain service authorization and JWT resource servers. |
| JPA / Hibernate | [JPA](https://docs.spring.io/spring-data/jpa/reference/), [Validator migration](https://hibernate.org/validator/documentation/migration-guide/) | Preserve entities and applied SQL migrations; adapt validation differences. |
| PostgreSQL / Flyway | [JDBC](https://jdbc.postgresql.org/documentation/), [Flyway](https://documentation.red-gate.com/flyway) | Use the Boot Flyway starter plus PostgreSQL extension. |
| MongoDB | [Spring Data](https://docs.spring.io/spring-data/mongodb/reference/), [Java driver](https://www.mongodb.com/docs/drivers/java/sync/current/) | Retain aggregates and legacy UUID representation. |
| Redis / Lettuce | [Redis](https://docs.spring.io/spring-data/redis/reference/redis/template.html), [Lettuce](https://github.com/redis/lettuce) | Retain caches, revocation, rate limits and distributed locks. |
| Kafka | [Serialization](https://docs.spring.io/spring-kafka/reference/kafka/serdes.html) | Jackson 3 serializers preserve event envelopes and type headers. |
| Elasticsearch | [Version matrix](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/versions.html) | Spring Data 6.1 requires matching Elasticsearch 9.4.5. |
| Metrics / tracing | [Micrometer](https://docs.micrometer.io/micrometer/reference/) | Preserve metrics, propagation and OTLP with BOM-managed dependencies. |
| Actuator / Prometheus | [Metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html) | Retain existing scrape endpoints and metric names. |
| Mail | [Boot mail support](https://docs.spring.io/spring-boot/reference/io/email.html) | Retain JavaMailSender and existing delivery/retry adapters; external delivery needs provider verification. |
| Data Commons | [Reference](https://docs.spring.io/spring-data/commons/reference/) | Retain pagination, sorting and repository abstractions. |
| JUnit | [User guide](https://docs.junit.org/current/user-guide/) | Boot-managed JUnit 6; Jupiter package names remain compatible. |
| Config / discovery | [Config](https://docs.spring.io/spring-cloud-config/reference/), [Netflix](https://docs.spring.io/spring-cloud-netflix/reference/) | Retain Config Server and Eureka. |
| Load balancing | [Cloud Commons](https://docs.spring.io/spring-cloud-commons/reference/spring-cloud-commons/loadbalancer.html) | Retain discovery-backed service resolution and existing cache configuration. |
| Gateway | [Web MVC](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc.html) | Renamed starter and `spring.cloud.gateway.server.webmvc.routes` properties. |
| OpenFeign | [Reference](https://docs.spring.io/spring-cloud-openfeign/reference/index.html) | Feature complete. HTTP service clients are the supported alternative for a separate migration; retain existing load balancing, scoped tokens and fallback semantics here. |
| Resilience4j | [Cloud circuit breakers](https://docs.spring.io/spring-cloud-circuitbreaker/reference/), [Resilience4j](https://resilience4j.readme.io/docs/getting-started) | Preserve circuit breakers, bulkheads and time limits; Framework retry alone cannot replace them. |

Jackson 2 and 3 can coexist because core/databind packages differ. The Boot BOM
manages the Jackson 2 compatibility line needed by JJWT and Eureka dependencies.
Removing those libraries breaks their SDKs. Application imports use `tools.jackson`;
annotations correctly remain `com.fasterxml.jackson.annotation`. Replacing JJWT
with Nimbus solely to remove a serializer needs a separate security migration.

## MapStruct, Java 21 records and Lombok

The root compiler runs Lombok, `lombok-mapstruct-binding` and MapStruct's processor
with `release=21`. Services declare MapStruct without runtime processor jars.
Implementations stay under `target/generated-sources/annotations`; do not commit
generated Java.

Auth user/address/seller, coupon, inventory, payment and notification response
mappers use Spring MapStruct beans. Existing normalization, defaults and money
rules remain explicit. Implementations use the existing `generated.mapper`
package/coverage convention; handwritten mapping rules retain their tests.

`services/common/src/test/java/com/project/common/mapping/RecordMappingTest.java`
compiles a mapper between a record and a Lombok builder, checking UUID, decimal
precision and nulls in both directions. MapStruct uses record component names.
Do not add mutable Lombok setters to records or convert persistence entities to
records. Existing generated HTTP bean contracts remain intact; the shared token
response remains a record.

## Compatibility changes

- Boot 4 uses focused MVC, security, Kafka, Flyway and test starters. Test slices
  and Mockito overrides use new APIs; inventory explicitly enables its
  RestTemplate test client.
- Jackson 3 mappers use builders and built-in Java time support. Generated bean
  constructors disable implicit creator selection, preserving omitted/null cart
  quantity's zero default. Auth's obsolete client template is replaced by the
  upstream Spring 7-compatible RestTemplate template.
- Standard `@Pattern` and `@NotNull` preserve former trim-based blank handling
  under Hibernate Validator 9, including control-only strings, role/permission
  list elements and original messages.
- Logout cookies use Spring's cookie builder so Tomcat 11 preserves `Max-Age=0`,
  secure, HttpOnly, path and SameSite response attributes.
- The shared security auto-configuration backs off for a service-owned chain;
  the shared default remains enabled and requires authentication. Tests verify
  both the default 401 challenge and a service-owned denial with one chain.
- Mongo properties move to `spring.mongodb.uri` and
  `spring.mongodb.representation.uuid`. Explicit `java_legacy` preserves stored
  UUID bytes; tests intentionally using `standard` retain that setting. No
  document migration is performed.
- Dockerfiles use `jarmode=tools` with `--launcher` for layer extraction, retaining
  Java 21 JRE images and the `JarLauncher` entry point.

## Rollout and rollback

Publish updated Config Server properties with the new artifacts. Old Mongo and
gateway prefixes cannot configure Boot 4 correctly. Virtual threads remain
disabled by default; existing scheduler and heartbeat executors are retained.
Follow the [Java 21 runtime validation guide](java21-migration.md).

Compose's Elasticsearch 9 service uses a new `elasticsearch_data_v9` volume.
Existing Elasticsearch 8 data is neither mounted into 9.x nor deleted. Rebuild
local search indexes from Mongo after starting the upgraded stack. For valuable
indexes follow Elastic's [upgrade preparation](https://www.elastic.co/docs/deploy-manage/upgrade/prepare-to-upgrade)
and [Upgrade Assistant](https://www.elastic.co/docs/deploy-manage/upgrade/prepare-to-upgrade/upgrade-assistant).
Do not switch a data volume directly from 8.17 to 9.4.5; take snapshots and confirm
index compatibility. This change does not migrate existing indexes.

Coordinate the Stripe webhook endpoint/API version with `2026-09-30.endive`.
The SDK rejects incompatible typed event versions. Signed raw-body tests cover
validation and processing; external endpoint settings, credentials and sandbox
provider behavior require separate verification.

Rollback requires prior Boot 3 artifacts and matching Config Server files.
Restore the previous Elasticsearch image/volume association using the previous
Compose file; never attach a 9.x-written volume to 8.x. Coordinate external Stripe
version changes separately. Applied SQL migrations and Mongo formats are unchanged.

## Verification

Run full Maven verification, checked-in coverage baselines, OpenAPI/Spectral,
routing, threading, environment, Dockerfile and Sonar regression scripts. Run the
documented product/cart/notification Mongo opt-ins, build service images and
inspect Java versions and Boot layers. Verify runtime jars exclude generated
test clients and nullable test support. Local tests do not establish external
provider compatibility, production load behavior or a hosted Sonar quality gate.

Local verification on Java 21.0.8 and Docker Desktop arm64:

- Final `./mvnw -B -ntp -fae clean verify`: 725 tests, zero failures/errors,
  four opt-in tests skipped in the ordinary run. All 12 backend modules passed.
- Coverage baseline and critical-class thresholds passed without baseline edits.
- Explicit product Mongo/Elasticsearch 9, cart Mongo and notification Mongo suites
  passed with all their opt-in tests enabled.
- All eight business jars contain generated server APIs and exclude generated
  test clients and Jackson nullable support.
- Deterministic bundle checks and 13 bundler regressions passed. Strict Spectral
  produced zero diagnostics for the root and service contracts.
- Routing, virtual-thread configuration, Java 21 Dockerfiles, Sonar integration,
  environment/release smoke fixtures and coverage-checker fixtures passed.
- The actual Maven Sonar scanner's offline dump verified both reactor modes,
  coverage locations and Java test classification; no hosted analysis was run.
- All eleven images built from the final POMs and reported Java 21. Config Server
  and discovery reached healthy status through their real Docker entry points
  in disposable containers with no application network or persistent volumes.

The application stack was not redeployed and its existing data was not modified.
External Stripe/OAuth/SMTP endpoints, an existing Elasticsearch 8 index migration,
representative production load and a hosted Sonar quality gate remain unverified.

The production search configuration has an opt-in Elasticsearch 9 regression:

```bash
PRODUCT_MONGO_INTEGRATION=true PRODUCT_ELASTICSEARCH_INTEGRATION=true \
  ./mvnw -B -ntp -pl services/product-service -am verify
```

It creates an isolated index and checks document read/write, UUID/decimal/enum
mapping, flattened attributes, search/count and deletion. CI enables it alongside
the product Mongo suite. It does not test migration of existing Elasticsearch 8
indexes.
