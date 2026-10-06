# OpenAPI code generation and contract testing

## Goal and contract ownership

Each business service owns an OpenAPI 3.0.1 contract in
`services/<service>/src/main/openapi/swagger.yaml`. Edit that input, regenerate the
root `swagger.yaml`, and run Maven verification. The combined file is derived
API documentation; module builds never consume it or sibling service contracts.

| Service | Package base | Documented operations | Generated MVC operations |
|---|---|---:|---:|
| auth-service | `com.project.authservice` | 24 | 21 |
| product-service | `com.project.product_service` | 22 | 22 |
| inventory-service | `com.project.inventory` | 12 | 12 |
| order-service | `com.project.order` | 7 | 7 |
| payment-service | `com.project.payment` | 9 | 7 |
| notification-service | `com.project.notification` | 3 | 3 |
| cart-service | `com.project.cart` | 7 | 7 |
| coupon-service | `com.project.coupon` | 10 | 10 |
| **Total** | | **94** | **89** |

The remaining operations are three handwritten MVC methods and two OAuth filter
routes. Infrastructure modules (`common`, `api-gateway`, `config-server`,
`discovery-server`) do not generate business API code. Existing service/domain
DTOs, persistence mappings, Kafka contracts, public payloads, authorization,
cookies and status codes remain the compatibility boundary.

## Frozen build configuration

Keep Java 21, Maven 3.9.12, Spring Boot 3.3.5, Springdoc 2.6.0 and the existing
managed Jackson versions. The root POM manages OpenAPI Generator 7.25.0 and
Jackson nullable 0.2.6; services activate their executions. Nullable support is
only a test dependency. Contract tools use Spectral 6.17.0 through `npx` and
PyYAML 6.0.3 in a local Python environment. No standalone generator CLI download
or global Python installation is required.

| Setting | Server | Test client |
|---|---|---|
| Execution ID | `generate-openapi-server` | `generate-openapi-test-client` |
| Phase | `generate-sources` | `generate-test-sources` |
| Generator/library | `spring` / `spring-boot` | `java` / `native`; auth `resttemplate` |
| Output under `target/` | `generated-sources/openapi` | `generated-test-sources/openapi` |
| Packages under service base | `.generated.api`, `.generated.model` | `.generated.testclient.api`, `.model`, `.invoker` |
| Source root | Main only | Test only |

Both executions validate input, clean their output, regenerate even when the
specification is unchanged, hide timestamps, and disable placeholder tests and
generated API/model documentation. Required generation is independent of
`skipTests`. Ordinary `./mvnw test` and `./mvnw verify` generate and compile both
outputs. Generated Java must never be edited or committed.

Server options are `interfaceOnly=true`, `useSpringBoot3=true`, `useTags=true`,
`skipDefaultInterface=true`, `useBeanValidation=true`, `openApiNullable=false`,
`generateJsonIncludeAnnotations=true`,
`optionalNonNullPropertyJsonInclude=NON_NULL`, and
`generateJsonSetterNullsAnnotations=false`. Field-level inclusion overrides
preserve explicit nulls such as mandatory envelope `data: null`. Prefer the
pinned generator's `x-jackson-json-include-policy: ALWAYS` extension, which
overrides its policy instead of adding a duplicate annotation. Mark those fields
nullable in the contract, using a nullable `allOf` wrapper for references. Cart's
optional nullable fields also retain their tested explicit `JsonInclude(ALWAYS)`
annotations.
Clients use Jakarta annotations, Jackson and `dateLibrary=java8`.

Offset-free `local-date-time` maps to `LocalDateTime`; RFC3339 `date-time` maps to
`OffsetDateTime`. Decimal amounts map to `BigDecimal`, UUID values to `UUID`.
Generated packages are excluded from JaCoCo and already fall under Sonar's
`target/` exclusion. The generator's reserved `org.openapitools.configuration`
supporting classes are also excluded from JaCoCo; they stay outside the service's
component scan to retain Spring's existing enum conversion behavior. New MapStruct HTTP boundary implementations use the service's
`.generated.mapper` package. Their handwritten interfaces/default methods and
all existing domain mappers retain coverage, and the checked-in coverage thresholds
remain unchanged. Order's HTTP mapper is separate from its existing domain mapper.

## Controller adoption and narrow exceptions

Controllers implement generated interfaces. Those interfaces own the complete
routes, so duplicate controller mapping annotations and class prefixes are
removed. Method authorization stays on implementations. MapStruct boundary
mappers convert HTTP models to/from existing service DTOs without changing
business method signatures. Existing Spring `Pageable` operations use the native
`x-spring-paginated: true` operation extension and retain their original
`@PageableDefault` annotations. Spring continues to resolve sort expressions,
page sizes and defaults; clients still generate the documented query parameters.
Status-filter parameters reference named enum schemas, so generated signatures
retain Spring enum binding, including invalid-value errors, whitespace handling
and optional empty values. Direct controller tests use the generated boundary
signatures; compatibility overloads are not added for old test calls.

Preserve legacy missing/null/blank validation, messages, primitive defaults,
pagination defaults, ordering, and null/omitted serialization. The shared Spring
`notNull.mustache` supports `x-not-null-message`; other supported schema message
extensions preserve pattern, size and numeric messages. This overrides only the
required-field validation template. Legacy `@NotBlank` constraints use the native
`x-field-extra-annotation` extension, preserving Hibernate's trim-based handling
of multiline and control characters; a generic whitespace regex is not equivalent.
The shared `nullableDataType.mustache` wraps the unmodified 7.25.0
JavaSpring partial with an opt-in `x-not-blank-items: true` branch for string lists.
Auth's roles and permissions retain `List<@NotBlank String>`, including null-item
rejection; item-level extra annotations are ignored by the upstream template.
Other models use the upstream branch. A pinned `beanValidationCore.mustache` override adds only the missing
`x-email-message` support, preserving custom email validation messages. Review
these partials when upgrading the generator.
Auth server generation opts into `jsonOnlyControllerResponses=true` through the
execution's `additionalProperties`. The pinned `libraries/spring-boot/api.mustache` changes only that
branch: MVC responses negotiate JSON, while response annotations use each
status's own content/schema. Upstream 7.25.0 otherwise unions filter-only
`text/plain` errors into success media types, causing JSON DTO responses to fail
for mixed Accept headers. The complete service contract and test client retain
the security-filter media types. Other services use the unmodified upstream
branch. Its library-specific location prevents Spring templates from overriding
native Java test-client templates. Recheck this override when upgrading the generator.

Class-based method-security proxies are explicit because MVC must invoke concrete
controller handlers implementing the generated interfaces.

Exclude these operation IDs from **server generation only**:

- Auth: `token`, `startSocialSignIn`, `completeSocialSignIn`.
- Payment: `receiveInternalPaymentWebhook`, `receiveStripeWebhook`.

Use the normalizer's positive operation-ID filter with pipe delimiters:
`FILTER=operationId:firstOperation|secondOperation|...`. Full contracts and test
clients retain excluded operations. CI checks the generated operation counts to
catch an incomplete filter silently producing too few interfaces.

Auth token handling keeps its original servlet, query/form compatibility,
grant-specific response bodies and refresh-cookie behavior. Generated clients
pass null query arguments and populated form arguments so credentials stay out
of URLs. OAuth routes remain Spring Security filter handlers. Public JWKS output
is mapped without changing key generation.

Auth's RestTemplate 7.25.0 template calls a Spring 6.2 header API unavailable in
the project's Spring 6.1.14. Its pinned local template changes only two
`HttpHeaders.headerSet()` calls to `entrySet()`; provenance is beside the
[override](../services/auth-service/src/main/openapi/templates/README.md).
Review that small compatibility patch when upgrading the generator. The native
form template is unsuitable for this token contract, so auth uses RestTemplate.

Payment webhooks retain unparsed request bodies and signature verification ahead
of JSON parsing. Their exact-byte tests use JDK HTTP instead of generated object
serialization. Payment client secret values appear only in the initial response. Replay retains
the legacy `clientSecret: null` field, with no secret value. Payment and notification
record fields retain explicit nulls; raw HTTP JSON is compared with legacy record
serialization rather than relying on decoded null getters.

## Deterministic documentation and linting

```bash
python3 -m venv .openapi-venv
.openapi-venv/bin/python -m pip install -r scripts/requirements-openapi.txt
.openapi-venv/bin/python scripts/bundle-openapi.py
.openapi-venv/bin/python scripts/bundle-openapi.py --check
bash scripts/test-openapi-contracts.sh
```

The bundler has an explicit eight-service input list. It namespaces components,
security schemes, tags and operation IDs, preserves security inheritance and
servers, and leaves example/default/enum payload literals intact. It rejects
duplicate YAML keys, duplicate/normalized method-path collisions, missing or
external references, malformed structures, and path-item references requiring
unsupported merging. Output is deterministic without timestamps or YAML aliases.
`--check` fails on root drift.

The regression script creates/removes its own temporary local Python environment,
runs bundling fixtures and strict Spectral linting. Set `OPENAPI_REPORT_DIR` to
retain raw stdout/stderr and normalized JSON. Spectral's successful CLI output
can include prose after `[]`; normalization retains the valid JSON array while
preserving the CLI exit status. Warnings fail verification; rules are not disabled.

Direct lint command:

```bash
npx --yes @stoplight/spectral-cli@6.17.0 lint swagger.yaml 'services/*/src/main/openapi/swagger.yaml' -f json --fail-severity warn
```

Runtime `/v3/api-docs` is derived from implemented controllers and interface
annotations, with handwritten exceptions retained. It is useful runtime evidence;
it is not the source used by code generation. Payment's documentation endpoint
requires authentication.

## HTTP compatibility coverage

Use real random-port embedded servers with the service's security configuration,
stubbed external adapters and generated clients. Preserve existing database and
workflow tests; these HTTP tests supplement them.

| Service | Required scenarios |
|---|---|
| Cart | Get/add/update, empty/null fields, decimal totals, bodyless bearer 401 challenge, coupon outage |
| Auth | All token grants, credentials absent from URLs, refresh/logout cookies, invalid scope/credentials, admin permissions, conditional OAuth redirects |
| Product | Public catalog/categories, pagination, decimal prices, seller/admin denial |
| Inventory | Reads, record ID versus product ID, scoped reservations, insufficient stock, missing scopes |
| Coupon | Validation/reservation/transitions, decimal amounts, ownership, conflicts, unavailable coupons |
| Order | Checkout, idempotency replay/conflict, ownership, cancellation, admin transitions |
| Payment | Initiation, initial-only secret, replay, ownership, refunds, valid/invalid signed raw webhooks |
| Notification | History pagination, unread count, mark-read ownership, actual security error bodies |

Assert exactly one MVC method/path mapping for every existing handler. Test
security-filter responses separately from business error envelopes. Cart's 401
is a bodyless bearer challenge. Auth anonymous protected requests are bodyless
403 when OAuth is unavailable and can redirect when it is configured;
authenticated method permission denial uses the JSON `ACCESS_DENIED` envelope.
Revoked tokens use the existing 401 text, and revocation-store failure remains a
bodyless fail-closed 503. Do not change runtime security to fit old annotations.

Embedded test configurations use explicitly selected plain `@Configuration` plus
`@TestComponent`, avoiding extra application roots and component-scan leakage.
Native clients use host/port plus a path-only base path; RestTemplate uses the
full base URL and JDK request factory so error bodies remain observable. Auth's
supplied RestTemplate client registers `JsonNullableModule` on a copy of its
Jackson converter's mapper: the upstream JSON converter does not register it
itself. This supports populated nullable nested fields without changing the
application's ObjectMapper or adding a runtime dependency.

## Parallel implementation and verification

Implementation uses an isolated `feature/openapi-code-generation` checkout. Keep
briefs, progress records, reports and diagnostics under `/tmp`; do not create new
instruction folders. Three concurrent Luna workers plus the controller use
exclusive file ownership. Workers never run Maven or Git. The controller runs
builds serially, integrates shared files and requests fresh reviews after evidence.

Wave 1 owns tooling, cart pilot, and auth client separately. Its gates are strict
lint/bundling, real client HTTP behavior, full pilot-module verification and fresh
reviews. Wave 2 owns auth/product, inventory/coupon, and order/payment/notification
in parallel. Start it only after the pilot gates pass. Fix concrete compatibility
or security defects before continuing to integration.

Final acceptance runs sequentially:

```bash
bash scripts/test-openapi-contracts.sh
./mvnw -B -ntp -fae clean verify
python3 scripts/check-openapi-artifacts.py
bash scripts/check-jacoco-baseline.sh coverage-baseline/backend-modules.json
PRODUCT_MONGO_INTEGRATION=true ./mvnw -B -ntp -pl services/product-service -am verify
./mvnw -B -ntp -pl services/cart-service -am verify -Dcart.mongo.integration=true
./mvnw -B -ntp -pl services/notification-service -am verify -Dnotification.mongo.integration=true
bash scripts/test-common-parent-dockerfiles.sh
bash scripts/test-java21-threading-config.sh
bash scripts/test-active-service-routes.sh
bash scripts/test-sonar-integration.sh
bash scripts/smoke-release.test.sh
bash scripts/test-sonar-maven-scope.sh
```

Also exercise clean and incremental ordinary `test`, change/delete an operation
and verify stale generated source **and class** removal, and build all eight
business images independently from their copied module-local `src/` inputs.
Runtime JARs must contain server code and exclude test clients/nullable support.
CI configures Node/npm and local Python, detects contract/tool changes, runs the
same checks, and saves lint and test diagnostics as artifacts.

Fresh review of the complete branch follows final evidence. Record unverified
external-provider scenarios explicitly: stubbed HTTP tests do not prove live
Google login, Stripe processing or notification delivery. Keep the existing
application untouched; no production deployment, database migration, frontend
SDK generation is part of this migration. Publishing the branch and opening a PR
require a separate request.

## Recorded local acceptance

Verification completed on 2026-10-06 in the isolated migration checkout:

- Clean reactor `verify`: 657 tests reported, zero failures/errors. Three opt-in
  Mongo cases were skipped in the default run and passed in explicit integration
  runs for product, cart and notification.
- Existing coverage baselines and all 11 critical-class thresholds passed without
  changes to the baseline file. Handwritten mapper defaults remain measured.
- Thirteen bundling regressions, deterministic root drift detection and strict
  Spectral linting passed with zero diagnostics across all nine contracts.
- All eight runtime JARs contained the expected 89 generated MVC operations and
  excluded generated test clients and the nullable module.
- Ordinary incremental cart `test` executions added then deleted an independent
  operation/model, confirming removal of stale generated source and class files.
- Dockerfile, threading, routing, environment/release and Sonar regression checks
  passed. The real Maven scanner converter passed in both analysis modes; this
  does not represent a hosted Sonar quality gate.
- All eight business images built independently and reported Temurin Java
  21.0.12.1 in their runtime version checks.

Diagnostics and review reports remain outside the repository. These results
cover embedded HTTP compatibility and local integration; live Google login,
Stripe processing and notification delivery remain unverified. The original
workspace and application stack were not modified by this verification.

Rollback requires reverting each complete service migration, including interfaces,
mappers, POM executions and corresponding contract changes. Removing generator
executions alone leaves controllers unable to compile. Rebuild and redeploy the
previous complete artifacts through the normal release process.
