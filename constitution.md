# Project constitution

## Architecture

Keep HTTP handling, business workflows, and persistence separate. Controllers
validate requests and call services; services own business rules and transaction
boundaries; repositories and adapters own storage and external integrations.
Do not bypass service authorization or move business transactions into controllers.

Services own their data. Use PostgreSQL and the existing Flyway migrations for
relational data, MongoDB for document aggregates, Elasticsearch for search, Redis
for caching and revocation, and Kafka for domain events. Introduce storage features
or dependencies only when the requirement needs them.

## Contracts and data

Preserve REST/OpenAPI payloads, Kafka event envelopes, authentication claims, and
persistence formats unless an explicit change is requested. Expose DTOs rather
than persistence entities. Prefer immutable DTOs and the existing MapStruct mappers
for new boundaries; retain established contract shapes during refactoring.

Schema changes require versioned migrations or an explicit compatible document
migration strategy. Cross-service workflows use durable outbox, idempotency,
reservation, and compensation patterns rather than cross-database transactions.

## Security

Backend services enforce authorization using validated JWTs, permissions, and
resource ownership. Forwarded identity headers are not an authorization boundary.
Internal callers use their configured, scoped service credentials and fail closed
when credentials or token exchange are invalid.

Keep credentials, private keys, environment values, payment client secrets, and
personal data out of commits, logs, URLs, and frontend configuration. Preserve
signature validation, secret-free response contracts, and idempotency guarantees.

## Concurrency and reliability

Use durable database constraints and atomic updates for coordination across
instances. Local locks may protect process-local token or cache state; they do
not provide distributed coordination. Preserve executor bounds, scheduler timing,
ordering, exception recovery, and shutdown behavior when changing threading.

Java 21 is the backend target. Use stable features; do not enable preview features
without an explicit requirement. Virtual threads remain disabled by default until
representative load and connection-pool validation justify enabling each service.

## Quality and verification

Prefer clear, minimal changes and existing libraries over speculative abstractions.
Use typed boundaries, centralized exception handling, and SLF4J logging.

Test changed business behavior and failure paths with the project's existing
JUnit Jupiter, Mockito, AssertJ, and frontend test tools. Maintain the checked-in coverage
baselines and critical-class thresholds; do not weaken them to accommodate a change.
Choose verification proportional to the change and report any unverified behavior.
Compilation, unit tests, and local smoke checks do not establish production readiness.
