# OpenAPI code generation and contract testing plan

## Accepted implementation sequence

Implementation runs on `codex/openapi-code-generation` in an isolated checkout.
Three concurrent Luna workers plus the controller share disjoint ownership;
all Maven builds and Git operations belong to the controller. Worker briefs,
reports and progress records are kept under `/tmp/ecommerce-openapi-execution`.

Frozen tools: Generator 7.25.0, Spectral 6.17.0, PyYAML 6.0.3 and test-scoped
Jackson nullable 0.2.6. Existing Java 21, Maven 3.9.12, Boot 3.3.5,
Springdoc 2.6.0 and BOM-managed Jackson versions remain unchanged.

Wave 1: tooling/bundler, cart server/client pilot, auth RestTemplate test client.
Wave 2: auth/product; inventory/coupon; order/payment/notification. Each wave
receives fresh Luna review after controller verification. The controller owns
shared Maven configuration, root bundling, CI, documentation and integration.

Server generation excludes auth `token`, `startSocialSignIn`,
`completeSocialSignIn` and payment `receiveInternalPaymentWebhook`,
`receiveStripeWebhook` through operation-ID normalizer filtering. The full
contracts and test clients retain these operations: 89 generated MVC operations,
three handwritten MVC operations and two OAuth filter routes (94 total).
Raw webhook signatures are verified before JSON parsing. Auth token requests
retain form/query compatibility and refresh cookies; generated RestTemplate
clients pass null query arguments and populated form arguments.

Server models use `openApiNullable=false`, `generateJsonIncludeAnnotations=true`,
`optionalNonNullPropertyJsonInclude=NON_NULL` and
`generateJsonSetterNullsAnnotations=false`; field overrides preserve mandatory
`data: null` and established serialization. Offset-free local-date-time maps to
LocalDateTime, RFC3339 date-time to OffsetDateTime, decimal money to BigDecimal
and UUID to UUID. Generated models belong only to HTTP boundaries. Existing
controllers implement generated interfaces and retain authorization. Direct tests
adopt the boundary signatures without compatibility overloads.

Acceptance includes clean and incremental normal test/verify generation, stale
output cleanup, exact route registration, generated clients making real embedded
HTTP requests with security enabled, unchanged handwritten coverage baselines,
zero Spectral diagnostics and reproducible root YAML. Test clients/dependencies
must remain outside runtime artifacts. Full verification includes the existing
regression checks, explicit product/cart/notification Mongo suites and all eight
business-service image builds. External-provider cases that cannot be exercised
are recorded explicitly. The existing application stays untouched. No push,
PR creation, deployment, frontend SDK or database migration is included.

## Goal and delivery boundary

Make each business service own a checked-in OpenAPI contract, generate its Java
API interfaces and models during normal Maven compilation, and generate separate
Java clients during test compilation. Preserve existing HTTP behavior, security,
service boundaries, persistence models, Kafka events, and frontend behavior.

The first delivery creates the contracts below and the combined root
`swagger.yaml`. The tasks in this document describe the subsequent implementation;
the current application still uses handwritten controllers and DTOs. Successful
specification validation or generation alone does not prove runtime compatibility.

## Contract ownership

| Service | Authoritative input | Local port |
|---|---|---|
| Authentication | `services/auth-service/src/main/openapi/swagger.yaml` | 8081 |
| Products/categories | `services/product-service/src/main/openapi/swagger.yaml` | 8082 |
| Inventory | `services/inventory-service/src/main/openapi/swagger.yaml` | 8083 |
| Orders | `services/order-service/src/main/openapi/swagger.yaml` | 8084 |
| Payments | `services/payment-service/src/main/openapi/swagger.yaml` | 8085 |
| Notifications | `services/notification-service/src/main/openapi/swagger.yaml` | 8086 |
| Cart | `services/cart-service/src/main/openapi/swagger.yaml` | 8087 |
| Coupons | `services/coupon-service/src/main/openapi/swagger.yaml` | 8088 |

The root `swagger.yaml` is a derived documentation contract. Namespace component
names, operation IDs, security schemes, and tags by service when combining inputs.
Preserve per-path server choices and public-operation security overrides. Reject
duplicate HTTP method/path pairs instead of silently overwriting them. Normal
module builds consume their own local input and never depend on a running service
or the root combined file. Infrastructure modules (`common`, `api-gateway`,
`config-server`, `discovery-server`) do not generate business API models.

Use OpenAPI 3.0.1 initially to match the existing Springdoc contract version. Keep
Java 21, Maven 3.9.12, Spring Boot 3.3.5, and Springdoc 2.6.0. Introduce only the
generator and dependencies required by its selected templates; do not upgrade the
application framework as part of this work.

## Review focus

Five compatibility risks receive explicit tests in Tasks 2–4: mandatory envelope
`data: null` versus omitted optional fields; offset-free date-times and decimal
money; grant-specific token bodies/cookies and security-filter error bodies;
idempotency replay and signed original webhook bytes; and generation from a
changed contract when stale output already exists. Each must be verified through
serialization or HTTP behavior before generated interfaces replace a controller.

## Task 1: Repeatable validation and combined documentation

Files: root `pom.xml`, `.spectral.yaml`, `swagger.yaml`, a new `scripts/bundle-openapi.py`, a new
`scripts/requirements-openapi.txt`, a new `scripts/test-openapi-contracts.sh`, and
`README.md`.

- [ ] Pin OpenAPI Generator to **7.25.0**, the version used for the initial source
  generation checks. Declare the version once in the root POM's plugin management; activate
  executions only in the eight owning modules.
- [ ] Implement `bundle-openapi.py [--check]` with an explicit eight-service input
  list and a pinned PyYAML dependency installed in a project-local virtual
  environment. Do not require globally installed Python packages.
  Bundle only local files. Rewrite local component references and security names
  consistently, prefix operation IDs/tags, retain security inheritance and
  server overrides, and produce deterministic YAML without timestamps or anchors.
  `--check` compares with the checked-in root file and exits nonzero on drift.
- [ ] Test bundling with conflicting schema names, missing references, public
  operations, duplicate method/path pairs, and mismatching root output. Fixtures
  belong under `scripts/fixtures/openapi/`; temporary output belongs outside source
  directories. A malformed contract must fail validation before generation.
- [ ] Lint each local contract and the combined file using **Spectral 6.17.0** via
  `npx --yes @stoplight/spectral-cli@6.17.0 lint swagger.yaml 'services/*/src/main/openapi/swagger.yaml' -f json --fail-severity warn`.
  Keep the checked-in `.spectral.yaml` extending `spectral:oas`; do not disable
  schema or documentation rules to make the lint pass. Save CI JSON diagnostics
  as a job artifact. No standalone Swagger/OpenAPI Generator CLI JAR is required.
  Verify unique operation IDs, required path parameters, and endpoint
  coverage against existing controller mappings. Include security-filter routes
  explicitly documented by auth, while distinguishing them from MVC controllers.
- [ ] Document edit-service-input/rebuild-root commands and the distinction between
  checked-in contracts and the current annotation-generated `/v3/api-docs` output.

Acceptance: a clean checkout validates all contracts without application startup;
the root file can be reproduced byte-for-byte; intentional drift and collisions
fail the regression script.

## Task 2: Generate and exercise test clients first

Files: root and eight service `pom.xml` files; new
`src/test/java/<existing-service-package>/contract/GeneratedClientContractTest.java`
in each service; existing controller/security contract tests.

- [ ] Add an execution named `generate-openapi-test-client` to each owning service,
  bound to **`generate-test-sources`**, with `inputSpec` equal to
  `${project.basedir}/src/main/openapi/swagger.yaml`. Use `generatorName=java`,
  `library=native` (auth uses `resttemplate`), Jackson serialization, `useJakartaEe=true`, and
  `dateLibrary=java8`. Use service-specific packages under
  `<existing-service-package>.generated.testclient` for API, model, and invoker
  code. Keep support code needed by the native client, rather than generating a
  separate runnable project.
- [ ] Write output to `${project.build.directory}/generated-test-sources/openapi`;
  set `addCompileSourceRoot=false`, `addTestCompileSourceRoot=true`,
  `skipValidateSpec=false`, `skipIfSpecIsUnchanged=false`, and `cleanupOutput=true`.
  Disable generated placeholder API/model tests and generated documentation.
  Actual behavioral tests remain handwritten. Do not tie required generation to
  `skipTests`; `mvn test` and `mvn verify` must generate clients automatically.
- [ ] Declare generator support dependencies with **test scope**, using existing
  managed versions where available. Inspect actual generated imports before
  adding dependencies. Confirm no test client classes enter the service JAR.
- [ ] Map `string+local-date-time` to `java.time.LocalDateTime`, using the tested
  generator type/import mappings; use `OffsetDateTime` or explicit `Instant`
  mappings for genuine RFC3339 date-time fields. Keep money as `BigDecimal`, UUID
  fields as UUID, and distinguish omitted fields, nullable fields, and null data.
- [ ] Run generated clients against real random-port embedded HTTP servers with
  existing test infrastructure and stubbed external adapters. Check successful
  responses and actual JSON bodies, not just compilation. Test 401/403 behavior
  separately from shared business error envelopes because security-filter errors
  can have different or empty bodies.
- [ ] Prove ordinary and clean `test` invocations both generate and compile clients.
  Remove an operation from a temporary contract and regenerate to verify stale
  generated classes are removed. Confirm changing a contract affects tests even
  when a previous `target/` directory exists.

Acceptance: all eight clients compile only on the test classpath; at least one
representative HTTP test per service uses its generated client; protected/public
routes and response decoding behave as currently implemented.

## Task 3: Generate server interfaces without changing behavior

Files: service `pom.xml` files, existing controllers, and boundary mappers/tests.

- [ ] Add `generate-openapi-server` at **`generate-sources`**. Use `generatorName=spring`,
  `library=spring-boot`, `interfaceOnly=true`, `useSpringBoot3=true`,
  `useTags=true`, `skipDefaultInterface=true`, `useBeanValidation=true`, and
  `openApiNullable=false`. Use separate service packages under
  `<existing-service-package>.generated.api` and `.generated.model`; output to
  `${project.build.directory}/generated-sources/openapi` with validation and cleanup
  enabled. Do not generate replacement applications, controllers, build files,
  persistence entities, or business services.
- [ ] Pin `generateJsonIncludeAnnotations` and `generateJsonSetterNullsAnnotations`
  deliberately after the cart serialization tests establish the correct settings.
  Generator 7.25.0 warns when these are unspecified. Test both mandatory nullable
  envelope data and omitted optional fields; a global NON_NULL mapper rule alone
  would incorrectly omit `data: null`. Prefer retaining the existing shared
  envelope with explicit boundary mappings if generated inclusion rules cannot
  preserve it. Inspect generated method names: the generator renames `list` to
  `callList`, so interface implementations must use the generated signature.
- [ ] Pilot with cart: implement generated interfaces in the existing controller,
  map generated request/response models at the HTTP boundary, and keep service
  signatures and domain DTOs intact. Use existing MapStruct support where useful.
  Preserve exception handling, JWT-derived identity, method-level authorization,
  trace fields, validation messages, and error status codes.
- [ ] Remove or reconcile handwritten mapping annotations as each operation moves
  to its interface. Assert exactly one registered mapping for every HTTP
  method/path; inherited interface mappings must not double the controller's
  class-level prefix or weaken method security.
- [ ] Treat auth token exchange, OAuth redirects, and raw signed webhook bodies as
  explicit boundary cases. A generated model must not replace the original webhook
  bytes before signature verification. Use a narrowly scoped type/schema mapping
  if a generator cannot represent an existing boundary faithfully; document and
  test each exception rather than changing the API to fit a template.
- [ ] Once cart passes its existing and generated-client tests, migrate the other
  seven controllers in independently reviewable service changes. Keep generated
  Java files under `target/`; never check them into Git or edit them directly.

Acceptance: generated interfaces are actually implemented, all routes remain
identical, existing controller/security tests pass, and generated-client tests
decode unchanged wire payloads. Check coverage with generated annotations/exclusions
scoped to generated packages; never lower existing handwritten-code baselines.

## Task 4: Service-specific compatibility coverage

- [ ] Auth: password/refresh/client-credentials grants, form parameter binding,
  grant-specific response bodies, refresh-cookie rotation/logout, invalid scope,
  invalid credentials, JWKS/discovery, conditional social-login redirects, and
  admin/seller permissions. Generated clients should submit credentials in the
  form body; accepting legacy query parameters must not encourage secrets in URLs.
- [ ] Product: public product/category reads, paginated/filter/search responses,
  seller/admin mutations, validation, and decimal prices.
- [ ] Inventory: stock reads/mutations, reservation/commit/release service scopes,
  insufficient-stock conflicts, and the different identifier semantics of GET
  versus PUT/DELETE on the normalized single templated inventory path.
- [ ] Cart: authenticated ownership, quantities, nullable coupon/image fields,
  decimal totals, empty cart, and clear/remove response bodies.
- [ ] Coupon: validation, reserve/commit/release scopes, unavailable coupons,
  reservation conflicts, and public-versus-admin permissions.
- [ ] Order: current-user pagination, checkout idempotency replay/conflict,
  inventory/coupon compensation behavior, cancellation, and admin transitions.
- [ ] Payment: card initiation, authoritative amount/currency, one-time client
  secret response, replay without secrets, read ownership, refund/process
  permissions, and both webhook signature/failure paths. Gateway and direct-service
  access policy must be tested separately.
- [ ] Notification: user-owned paginated history, mark-read, unread counts,
  administrator exceptions, and actual error envelopes. Do not invent an SSE
  endpoint: the current service exposes polling/history operations.

Acceptance: success and significant failure paths run through HTTP, with explicit
assertions for serialization, statuses, headers, and authorization. Retain existing
integration suites for database-backed behavior; mock-only HTTP tests supplement
those suites rather than replacing them.

## Task 5: CI, Docker, and developer workflow

Files: `.github/workflows/ci.yml`, affected service Dockerfiles only if necessary,
existing regression scripts, and root/service documentation.

- [ ] Include `.spectral.yaml`, `swagger.yaml`, bundle/validation scripts and their dependency file
  in CI change detection. Service-local inputs already match `services/**`.
- [ ] Configure Node/npm for the backend lint job, install the local bundler requirements,
  check root drift, and lint the
  eight contracts before the existing sequential `clean verify` and coverage checks.
  Contract changes must trigger the owning service image checks.
- [ ] Verify service-only reactor builds and Docker builds use the module-local
  input. Current Dockerfiles already copy each service's `src/`; do not add a
  dependency on root documentation or sibling service contracts. If a service
  later generates an outbound client, include its pinned dependency contract
  explicitly rather than reading a sibling checkout implicitly.
- [ ] Preserve existing Java 21, routing, Sonar, environment, and Dockerfile
  regression checks. Confirm generated code is excluded from Sonar analysis and
  handwritten-code coverage stays meaningful.
- [ ] Document generate-sources, generate-test-sources, test, verify, contract edit,
  bundle regeneration, and rollback commands. Removing generator executions does
  not undo migrated interfaces: revert each complete service migration together.

- [ ] Reconcile documentation that currently disagrees with runtime. In particular,
  `CartController` and `CartOpenApiContractTest` advertise a JSON error body for
  401, while cart's bearer filter emits a bodyless challenge. Keep the checked-in
  contract runtime-accurate, correct the annotations/documentation assertion, and
  add a real missing-token HTTP test. Do not change authentication behavior merely
  to match an old annotation.

Acceptance: CI verifies a clean checkout, all eight service packages build from
their local contracts, and generated test classes stay out of runtime artifacts.

## Parallel implementation after this contract delivery

Use three simultaneous workers and a controller, matching the four available
slots. First complete shared plugin settings, bundling behavior, package naming,
date mappings, and the cart pilot. Then use disjoint service groups:

| Worker | Exclusive service ownership |
|---|---|
| A | Auth and product |
| B | Inventory and coupon |
| C | Order, payment, and notification |
| Controller | Root POM, cart pilot, root bundle/tooling, CI, integration |

Each worker owns its service POM/controllers/mappers/tests and local contract;
workers do not edit shared files or run Maven concurrently. The controller runs
builds/tests sequentially, reviews each service change, and coordinates fixes.
Review every service's security and payload changes before integration.

## Final verification

Run the contract regression script, then `./mvnw -B -ntp -fae clean verify` and the
existing backend coverage checker. Run the explicit product/cart/notification
Mongo integration suites documented in `Agents.md`. Repeat relevant existing
Sonar, routing, environment, and Dockerfile regressions. Build all eight business
service images and smoke representative authenticated workflows with generated
clients. Record skipped external-provider scenarios accurately; successful source
generation is not application or deployment verification.

## Initial contract delivery verification

The eight source contracts cover **94 operations across 74 paths**: auth 24,
product 22, inventory 12, order 7, payment 9, notification 3, cart 7, and coupon 10.
The combined documentation namespaces 95 schemas. The current delivery checks
linting with Spectral 6.17.0, local reference resolution, unique operation IDs,
path parameters, and coverage
of every operation in the seven available live Springdoc snapshots. Payment is
checked against source/tests because its live documentation endpoint requires
authentication. Spectral's default OpenAPI ruleset reports zero diagnostics across
all nine files; its JSON report is saved outside the repository. Auth adds two conditional security-filter OAuth routes absent
from its Springdoc snapshot. The combined root file is generated for this delivery;
the reproducible repository command and CI drift check are Task 1.

Spring/native-Java source generation smoke checks used Generator 7.25.0 before
the Spectral cleanup. Repeat generation and compile the output in Tasks 2–3.
Generation outputs and logs remain under `/tmp`, outside the repository. The
temporary OpenAPI Generator CLI download has been removed; the Maven generator
remains part of the implementation plan for generating code. These
checks do not compile or run the generated code; Maven integration, compilation,
HTTP compatibility tests, and full reactor verification belong to the tasks above.

## Reference settings

Use the official [Spectral CLI documentation](https://github.com/stoplightio/spectral/blob/develop/docs/guides/2-cli.md)
for linting and JSON diagnostics, the [Maven plugin documentation](https://github.com/OpenAPITools/openapi-generator/blob/v7.25.0/modules/openapi-generator-maven-plugin/README.md)
for execution/source-root options, the [Spring generator documentation](https://openapi-generator.tech/docs/generators/spring/)
for interface settings, and the [Java generator documentation](https://openapi-generator.tech/docs/generators/java/)
for the native test client. Recheck options against the pinned version before
implementing; do not depend on floating template versions.
