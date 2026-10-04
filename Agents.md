# Repository working instructions

Read [constitution.md](constitution.md) for project principles and [README.md](README.md)
for setup, contracts, and operating instructions. Follow the user's requested scope.
These are the repository's two shared guidance files; keep project rules here rather
than creating additional vendor-specific instruction or planning files.

## Project layout

- `pom.xml`, `.mvn/`, `mvnw`, `mvnw.cmd`: root Maven reactor and wrapper.
- `services/common`: shared authentication, DTO, event, and integration code.
- `services/*-service`, `services/api-gateway`, `services/config-server`,
  `services/discovery-server`: application modules.
- `frontend/ecommerce-app`: React/TypeScript application and frontend tests.
- `config-repo`, `docker-compose.yml`, `docker/`: runtime configuration and local stack.
- `scripts`, `coverage-baseline`, `docs`: regression checks, coverage policies,
  contracts, and verification evidence.

## Implementation

Use Java 21 and the root Maven 3.9.12 wrapper. Run Maven from the repository root;
service directories do not have independent wrappers. Keep existing dependency
versions unless a demonstrated incompatibility or requested feature requires a change.

Inspect the relevant implementation and tests before editing. Keep changes scoped,
preserve service boundaries and public contracts, and include meaningful regression
coverage for behavioral changes. Configuration or documentation changes should use
relevant existing checks rather than implementation-mirroring tests.

When work is parallel, assign disjoint files and serialize Maven builds/tests in the
shared checkout. Keep temporary reports and tool output outside the repository.
No knowledge-graph indexing, global tool installations, or extra instruction files
are required to modify this project.

## Verification commands

Run the checks relevant to the change; use full verification for broad integration
changes. Docker must be available for Testcontainers suites.

```bash
./mvnw -B -ntp -fae clean verify
bash scripts/check-jacoco-baseline.sh coverage-baseline/backend-modules.json
bash scripts/test-common-parent-dockerfiles.sh
bash scripts/test-java21-threading-config.sh
bash scripts/test-active-service-routes.sh
bash scripts/test-sonar-integration.sh
```

Explicit Mongo integration suites:

```bash
PRODUCT_MONGO_INTEGRATION=true ./mvnw -pl services/product-service -am verify
./mvnw -pl services/cart-service -am verify -Dcart.mongo.integration=true
./mvnw -pl services/notification-service -am verify -Dnotification.mongo.integration=true
```

Frontend commands:

```bash
npm --prefix frontend/ecommerce-app ci
npm --prefix frontend/ecommerce-app run typecheck
npm --prefix frontend/ecommerce-app run lint
npm --prefix frontend/ecommerce-app run test:coverage
npm --prefix frontend/ecommerce-app run test:coverage:check
npm --prefix frontend/ecommerce-app run build
```

Before handing off, inspect the diff, check for stale links and accidental secrets,
and state what changed, what was tested, and material limitations. Do not claim
unrun tests, a hosted quality gate, notification delivery, or production readiness
from narrower verification. Consult the Java 21 migration guide for current
runtime validation requirements.
