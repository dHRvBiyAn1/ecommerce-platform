# Ecommerce Platform

A Spring Boot microservices ecommerce platform: auth, catalog, inventory, orders,
payments (Stripe), notifications, plus 11 more services in the roadmap (cart,
wishlist, reviews, coupons, tax, shipping, marketplace, CMS, admin BFF, analytics,
recommendations).

## Quick start

```bash
git clone <repo> && cd ecommerce-platform

# 1. Bootstrap secrets (gitignored)
make keys
cp .env.example .env

# 2. Build everything
make build

# 3. Bring up the local stack
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
| MailHog UI           | 8025  | Captures outbound email in dev                   |
| Prometheus           | 9090  | Metrics                                          |
| Grafana              | 3000  | Dashboards                                       |
| Loki                 | 3100  | Log aggregation                                  |
| Tempo                | 3200  | Distributed tracing                              |

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
- The order saga propagates the user's JWT to inventory-service via Feign
  (`com.project.common.feign.FeignAuthForwardingConfig`), so service-to-service
  calls remain authenticated end-to-end.
- Refresh tokens are stored as SHA-256 hashes (`TokenHasher`) — never raw — with
  family-based reuse detection.

## What works today

- Auth: register (always `ROLE_CUSTOMER`), login (password & refresh), logout,
  change-password (authenticated), JWKS, social login (Google/GitHub when
  configured), bootstrap admin from env vars.
- Products: full catalog CRUD with seller-scoped writes; ES-backed search with
  Mongo fallback; price-range filter; category browse.
- Inventory: atomic reservation/release via Mongo `$expr` guard; low-stock
  detection; stock adjustments; events.
- Orders: real saga — fetches authoritative price from product-service, reserves
  stock, persists, emits `OrderEvent.CREATED`, listens for `PaymentEvent` and
  transitions to CONFIRMED/CANCELLED with stock-release compensation.
- Payments: Stripe (when `STRIPE_SECRET_KEY` is set) or `SandboxGateway`
  (deterministic dev). HMAC-signed in-house webhook (`X-Webhook-Signature`).
  Idempotency keys for create + refund.
- Notifications: Mongo-persisted; consumer for user/order/payment/inventory
  events; DLT for poison pills; REST API for listing & marking read; MailHog
  catches outbound mail in dev.

## Roadmap

Tracked in `.todo/` (or follow-up sessions). Big buckets remaining:

1. **New services**: cart, wishlist, review, coupon (rule engine), tax (India GST),
   shipping, seller, cms, admin-bff, analytics, recommendation. Their module
   slots already exist in the parent POM.
2. **Sample data seeders**: 20-30 records per service.
3. **Tests**: Testcontainers integration tests for every service; Spring Cloud
   Contract between order/payment/inventory; E2E happy path.
4. **Observability**: Prometheus/Grafana dashboards committed; OpenTelemetry
   bridge; structured JSON logs to Loki.
5. **CI/CD**: multi-stage Dockerfiles for every service; GHCR push; SBOM via
   CycloneDX; Trivy + OWASP-DC; cosign keyless signing.
6. **Production posture**: Helm charts, Linkerd service mesh, NetworkPolicies,
   Vault dev / sops+age for secrets.

## Repo layout

```
ecommerce-platform/
├── pom.xml                      # parent BOM
├── Makefile                     # one-command bring-up
├── .env.example
├── scripts/
│   └── gen-keys.sh
├── config-repo/                 # Spring Cloud Config
├── docker/
│   ├── docker-compose.yml
│   └── observability/{prometheus,tempo}.{yml,yaml}
└── services/
    ├── common/                  # shared events + security + DTOs + Kafka utils
    ├── api-gateway/
    ├── auth-service/
    ├── product-service/
    ├── inventory-service/
    ├── order-service/
    ├── payment-service/
    ├── notification-service/
    ├── cart-service/            # planned
    ├── wishlist-service/        # planned
    ├── review-service/          # planned
    ├── coupon-service/          # planned
    ├── tax-service/             # planned
    ├── shipping-service/        # planned
    ├── seller-service/          # planned
    ├── cms-service/             # planned
    ├── admin-bff/               # planned
    ├── analytics-service/       # planned
    └── recommendation-service/  # planned
```
