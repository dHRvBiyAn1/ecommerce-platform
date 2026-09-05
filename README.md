# Ecommerce Platform

A Spring Boot microservices ecommerce platform: auth, catalog, inventory, orders,
payments (Stripe), notifications, plus 11 more services in the roadmap (cart,
wishlist, reviews, coupons, tax, shipping, marketplace, CMS, admin BFF, analytics,
recommendations).

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

Services:

| Service              | Port  | Description                                      |
|----------------------|-------|--------------------------------------------------|
| api-gateway          | 8080  | Edge router, rate limiting, header stripping     |
| auth-service         | 8081  | JWT issuer, JWKS, OAuth2 social login            |
| product-service      | 8082  | Catalog, search (ES), categories                 |
| inventory-service    | 8083  | Atomic stock reservation                         |
| order-service        | 8084  | Order saga, ProductClient, payment-event consumer|
| payment-service      | 8085  | Stripe + sandbox; HMAC webhook                   |
| notification-service | 8086  | Mongo-persisted notifications + Mailhog          |
| discovery-server     | 8761  | Eureka                                           |
| config-server        | 8888  | Spring Cloud Config                              |
| frontend             | 5173  | Production-built SPA and same-origin API proxy   |
| Prometheus           | 9090  | Metrics                                          |
| Grafana              | 3000  | Dashboards                                       |
| Loki                 | 3100  | Log aggregation                                  |
| Tempo                | 3200  | Distributed tracing                              |

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
