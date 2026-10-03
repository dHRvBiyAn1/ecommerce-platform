# Ecommerce Platform

A Spring Boot microservices ecommerce platform with authentication, catalog,
inventory, cart, orders, payments, notifications, and coupons. Additional gateway
routes are reserved for domains that are not deployed in this Compose stack.

## Quick start

```bash
git clone <repo> && cd ecommerce-platform

# 1. Bootstrap local configuration and RSA keys (both gitignored)
make env
make keys

# Generate three distinct machine-client secrets and set them in .env (see below)
# Run separately for ORDER_SERVICE_CLIENT_SECRET, PAYMENT_SERVICE_CLIENT_SECRET,
# and CART_SERVICE_CLIENT_SECRET:
openssl rand -hex 32

# 2. Build and verify the Maven reactor
make build

# 3. Build missing container images and bring up the local stack
make up

# 4. Tail logs
make logs
```

Compose-published host ports (left side of each mapping):

| Service              | Host → container | Health / purpose |
|----------------------|------------------|------------------|
| api-gateway          | `8080:8080` | `GET /actuator/health`; edge routes below |
| auth-service         | `8081:8081` | `GET /actuator/health`; auth, users, JWKS |
| product-service      | `8082:8082` | `GET /actuator/health`; products and categories |
| inventory-service    | `8083:8083` | `GET /actuator/health`; inventory |
| order-service        | `8084:8084` | `GET /actuator/health`; orders |
| payment-service      | `8085:8085` | `GET /actuator/health`; payments |
| notification-service | `8086:8086` | `GET /actuator/health`; notifications |
| cart-service         | `8087:8087` | `GET /actuator/health`; cart |
| coupon-service       | `8088:8088` | `GET /actuator/health`; coupons |
| discovery-server     | `8761:8761` | Eureka; `GET /actuator/health` |
| config-server        | `8888:8888` | Spring Cloud Config; `GET /actuator/health` |
| frontend             | `5173:8080` | Production SPA; container health path `/health` |
| PostgreSQL           | `5433:5432` | Database |
| MongoDB              | `27017:27017` | Database |
| Redis                | `6379:6379` | Cache and rate-limit store |
| Elasticsearch        | `9200:9200` | Product search |
| Kafka                | `29092:29092` | Host listener; services use `kafka:9092` |
| Prometheus           | `9090:9090` | Metrics |
| Grafana              | `3000:3000` | Dashboards |
| Loki                 | `3100:3100` | Log aggregation; readiness `/ready` |
| Tempo                | `3200:3200` | Tracing; OTLP ports `4317`, `4318` |

Spring-service health checks use `/actuator/health`; Compose polls them every
30 seconds after a 45-second startup allowance (data services have their own
health checks). The frontend image exposes `/health`. Configured gateway routes
forward these deployed API prefixes without rewriting them:

| Gateway path | Service |
|--------------|---------|
| `/api/auth/**`, `/api/user/**`, `/api/admin/**`, `/oauth2/**`, `/login/oauth2/**`, `/.well-known/**` | auth-service |
| `/api/v1/products/**`, `/api/v1/categories/**` | product-service |
| `/api/v1/inventory/**` | inventory-service |
| `/api/v1/orders/**` | order-service |
| `/api/v1/payments/**` | payment-service |
| `/api/v1/notifications/**` | notification-service |
| `/api/v1/cart/**` | cart-service |
| `/api/v1/coupons/**` | coupon-service |

`GET /v3/api-docs/swagger-config` is the gateway OpenAPI discovery endpoint;
it returns discovered service `/v3/api-docs` URLs. The gateway authorization
interceptor requires a nonblank `Authorization` header for this path and even
for gateway `/actuator/health`. Compose's gateway health check supplies a
non-secret header; external smoke checks can use `SMOKE_BEARER_TOKEN` below.

The service Dockerfiles compile their own JARs in multi-stage builds, so a
clean checkout can build images without host `target/` directories. `make
build` remains the fast host-side reactor check; Compose builds images when
they are missing.

For frontend hot reload, run Vite on the host while the backend stack is up:

```bash
cd frontend/ecommerce-app
npm ci
VITE_API_PROXY_TARGET=http://localhost:8080 npm run dev
```

The Compose frontend is intentionally the production static-server image. It
keeps the familiar <http://localhost:5173> URL, serves SPA routes through
`index.html`, and proxies API and OAuth paths to `api-gateway` inside the
Compose network.

## Security model (after the resource-server migration)

- Auth-service signs JWTs with RS256 using a keypair loaded from `.secrets/keys/`
  (mounted as Docker secrets in compose). The committed `private.pem` was deleted
  and the broken classpath-binding logic in `RsaKeyConfig` was replaced with
  `KeyManager` which reads PEMs from a configurable `Resource` URI.
- The JWT carries `sub` (user id), `email`, `roles` (already prefixed `ROLE_`),
  and `permissions` (fine-grained authorities like `orders:create`). The JWT
  header includes `kid` for rotation.
- Backend services are OAuth2 resource servers (`spring-boot-starter-oauth2-resource-server`).
  They fetch auth-service's JWKS at `${JWK_SET_URI}` and verify every request.
  The previous `X-User-Id` / `HeaderAuthenticationFilter` trust model is gone.
- The api-gateway no longer authenticates: it strips client-supplied `X-User-*`
  headers (defence-in-depth) and forwards the `Authorization: Bearer <jwt>` header
  unchanged. Rate limiting is per-IP via Bucket4j.
- Order, payment, and cart use scoped client-credentials tokens for internal
  Feign calls, including when a user is logged in. These configured services
  must fail closed on invalid configuration or token exchange failure, never
  fall back to forwarding the user's JWT.
- Refresh tokens are stored as SHA-256 hashes (`TokenHasher`) — never raw — with
  family-based reuse detection.

### Service authentication setup

| Client | Scopes | Secret environment variable |
| --- | --- | --- |
| order-service | `inventory.write coupons.read coupons.write` | `ORDER_SERVICE_CLIENT_SECRET` |
| payment-service | `orders.read` | `PAYMENT_SERVICE_CLIENT_SECRET` |
| cart-service | `coupons.read` | `CART_SERVICE_CLIENT_SECRET` |

Generate each secret independently with `openssl rand -hex 32` and populate the
blank entries in your gitignored `.env` before running Compose. Compose rejects
unset or empty client secrets; required callers also rely on application
validation to reject blank secrets and disabled or incomplete `service.auth`
configuration when launched outside Compose.

Each caller receives only its own client secret; auth-service receives all three
for its allowlist. Keep secrets out of `config-repo`, config-server's environment,
and the shared Compose environment anchor: the config endpoint is unauthenticated.
Checked-in configuration contains environment placeholders, not client secrets.

The callers set `service.auth.enabled: true`, `token-uri`, `client-id`,
`client-secret`, and space-delimited `scope`. `SERVICE_AUTH_TOKEN_URI` overrides
the local default `http://auth-service:8081/api/auth/token`. HTTP is only suitable
for an isolated local network; production requires TLS (HTTPS) for token exchange
and protected internal traffic, plus restricted access to the config endpoint.

## API and payment safety

The API is served through <http://localhost:8080>. Order creation is
`POST /api/v1/orders`; payment initiation is `POST /api/v1/payments`. Both accept
`X-Idempotency-Key` (the exact implemented header). Reuse the same stable key
only when retrying the same logical operation; generate a new key for a new
order/payment attempt. The Postman collection stores order, payment, and refund
keys across sends; clear only that operation's collection variable before
starting a genuinely new operation.

Payment initiation is card-only and customer-only, never a service-client flow.
The authenticated bearer customer must own the order before the backend creates
a payment. The first successful create response has `data.payment` and may
include `data.clientSecret`; only the first successful customer create may
return the secret. Idempotency replays do not return it. Keep it in memory only
for immediate Stripe.js card confirmation. Never put it in browser storage,
logs, analytics, URLs, or a `PaymentResponse`. Payment
GET/list/reference/order endpoints return secret-free status; recovery polls
`GET /api/v1/payments/{paymentId}` and must not request a replacement secret.

Compose passes the public `VITE_STRIPE_PUBLISHABLE_KEY` build argument to the
frontend Dockerfile's Vite production build; configure the Stripe publishable
`pk_…` value for Stripe card confirmation. The backend's `STRIPE_SECRET_KEY`
enables Stripe; when empty, `SandboxGateway` returns a synthetic secret that
cannot be confirmed by Stripe.js. Keep Stripe API/webhook secrets in backend
runtime configuration only, never Vite build args or browser code.

The Stripe webhook is `POST /api/v1/payments/webhook/stripe`, authenticated by
Stripe's `Stripe-Signature` and `STRIPE_WEBHOOK_SECRET`. The separate internal
HMAC webhook is `POST /api/v1/payments/webhook`, signed with
`PAYMENT_WEBHOOK_SECRET` and `X-Webhook-Signature: t=<unix>,v1=<hex>`. These
secrets and signature formats are not interchangeable.

## Release checks

`bash scripts/smoke-release.sh` only performs bounded GET requests; it never
starts, stops, or changes services. Set `SMOKE_BASE_URL` to an absolute HTTP(S)
base URL. Defaults are `/actuator/health` and
`/v3/api-docs/swagger-config`; set `SMOKE_HEALTH_URL` and
`SMOKE_OPENAPI_URL` to override either endpoint. Both probes require HTTP 2xx.
When a gateway endpoint requires Authorization, set `SMOKE_BEARER_TOKEN`; the
smoke script sends `Authorization: Bearer …` to both probes, rejects CR/LF, and
passes the header through curl stdin rather than its argument list. It does not
print or persist the token or log configured URLs. The gateway currently checks
for a nonblank Authorization header on both default paths. The upstream API
services perform actual JWT validation for protected APIs.

```bash
make smoke-test
SMOKE_BASE_URL=https://your-gateway.example \
SMOKE_BEARER_TOKEN="${SMOKE_BEARER_TOKEN:?set it from your local secret source}" \
  make smoke
bash scripts/verify-release-evidence.test.sh
bash scripts/verify-release-evidence.sh docs/release-hardening-evidence.md
```

The evidence checker is expected to reject the current partial evidence until
Task 28 records all task statuses and release gates. See
[release-hardening evidence](docs/release-hardening-evidence.md) for the current
CI run, available checks, reviewer evidence, Sonar status, and outstanding
reconciliation items. Task records are per-task entries in
`docs/release-hardening-evidence/task-records.json`; CI claims are checked
against the captured job and step conclusions in its `ci-run-*.json` artifact.
A smoke PASS requires a configured live transcript artifact with both GET probes
returning 2xx; the smoke unit test alone is never accepted as a release result.
Sonar is accepted only when the captured Sonar job and SonarCloud step both
succeeded. For a final run, capture `gh run view <run-id> --json
databaseId,headSha,url,status,conclusion,jobs` and persist its run identity plus
the release jobs and gating-step conclusions in a commit-safe evidence
artifact. The checked-in CI record is a gate-focused projection of that output;
the checker reads only local records and does not call GitHub.

Task 28 smoke evidence belongs in `docs/release-hardening-evidence/smoke-run-*.json`
and records `capturedAt`, `capturedFrom`, command/exit status, configured base
URL, both probe names/methods/URLs/HTTP statuses, and the captured smoke output.
Record explicit endpoint overrides when used. The verifier checks probe URLs
against the base defaults or those overrides. Use URLs without credentials,
query, or fragment, and never include the bearer token. A source script, mocked
test, or unit-test output cannot establish a live smoke PASS.

## What works today

- Auth: register (always `ROLE_CUSTOMER`), login (password & refresh), logout,
  change-password (authenticated), JWKS, social login (Google/GitHub when
  configured), bootstrap admin from env vars.
- Products: full catalog CRUD with seller-scoped writes; ES-backed search with
  Mongo fallback; price-range filter; category browse.
- Inventory: order-owned, idempotent reserve/commit/release lifecycle via
  atomic Mongo updates; low-stock detection; stock adjustments; events.
- Orders: real saga — fetches authoritative price from product-service, reserves
  stock and coupon capacity, persists, emits `OrderEvent.CREATED`, listens for
  `PaymentEvent`, and commits or compensates reservations.
- Payments: Stripe (when `STRIPE_SECRET_KEY` is set) or `SandboxGateway`
  (deterministic dev). HMAC-signed in-house webhook (`X-Webhook-Signature`).
  Idempotency keys for create + refund; amount and currency are sourced from the
  authoritative order rather than trusted from the browser.
- Notifications: Mongo-persisted; consumer for user/order/payment/inventory
  events; DLT for poison pills; owner-scoped REST API for listing and marking
  notifications read; configurable SMTP delivery.

## Roadmap

Tracked in `.todo/` (or follow-up sessions). Big buckets remaining:

1. **Tests**: broaden unit coverage, add Testcontainers integration tests for every service; Spring Cloud
   Contract between order/payment/inventory; E2E happy path.
2. **Service identity**: extend scoped client-credentials coverage to future callers.
3. **Observability**: Prometheus/Grafana dashboards committed; OpenTelemetry
   bridge; structured JSON logs to Loki.
4. **CI/CD**: GHCR push; SBOM via
   CycloneDX; Trivy + OWASP-DC; cosign keyless signing.
5. **Production posture**: Helm charts, Linkerd service mesh, NetworkPolicies,
   Vault dev / sops+age for secrets.
6. **Future domains**: wishlist, review, tax, shipping, seller, CMS, analytics,
   and recommendations as product requirements mature.

## Repo layout

```
ecommerce-platform/
├── pom.xml                      # parent BOM
├── Makefile                     # one-command bring-up
├── .env.example
├── scripts/
│   └── gen-keys.sh
├── config-repo/                 # Spring Cloud Config
├── docker-compose.yml
├── prometheus.yml
├── tempo.yaml
└── services/
    ├── common/                  # shared events + security + DTOs + Kafka utils
    ├── api-gateway/
    ├── auth-service/
    ├── product-service/
    ├── inventory-service/
    ├── order-service/
    ├── payment-service/
    ├── notification-service/
    ├── cart-service/
    ├── wishlist-service/        # planned
    ├── review-service/          # planned
    ├── coupon-service/
    ├── tax-service/             # planned
    ├── shipping-service/        # planned
    ├── seller-service/          # planned
    ├── cms-service/             # planned
    ├── admin-bff/               # planned
    ├── analytics-service/       # planned
    └── recommendation-service/  # planned
```
