# Backend State — Ecommerce Platform

> **Snapshot date**: 2026-05-24
> **Spring Boot 3.3.5 / Spring Cloud 2023.0.3 / Java 17**
> **Purpose of this file**: keep a durable record across sessions of what backend
> work is finished, what is in flight, and what is still queued — so any future
> session (mine or yours) can resume without re-deriving context.

---

## Table of contents
1. [Architecture overview](#architecture-overview)
2. [What is built](#what-is-built)
3. [Endpoints by service](#endpoints-by-service)
4. [Event topics & schemas](#event-topics--schemas)
5. [Security model](#security-model)
6. [How to run locally](#how-to-run-locally)
7. [Known caveats / verify-before-prod list](#known-caveats--verify-before-prod-list)
8. [What is NOT yet built](#what-is-not-yet-built)
9. [Recommended next sessions](#recommended-next-sessions)

---

## Architecture overview

```
                     ┌─────────────────────────────────────┐
                     │   api-gateway (8080)                 │
                     │   • Routes via Eureka (lb://)        │
                     │   • Bucket4j per-IP rate limit       │
                     │   • Strips client X-User-* headers   │
                     │   • NO authentication (forwards JWT) │
                     └────────────────┬────────────────────┘
                                      │  Authorization: Bearer <jwt>
        ┌──────────────────┬──────────┼──────────┬──────────────────┐
        ▼                  ▼          ▼          ▼                  ▼
  auth-service       product-svc  order-svc  inventory-svc    payment-svc
  (Postgres)         (Mongo+ES)   (Mongo)    (Mongo)          (Mongo)
        │                  │          │          │                  │
        └────── all 5 are OAuth2 Resource Servers, JWKS ←──┐        │
                                                            │        │
                                  notification-svc (Mongo) ─┘        │
                                  (consumes Kafka events)            │
                                                                     │
                          discovery-server (Eureka)                  │
                          config-server (Spring Cloud Config)        │
                          ↑                                          │
                          ALL services register and pull config      │
                                                                     │
                                  Kafka ←──────── PaymentEvent ──────┘
                                  + 11 topics + DLTs
                                                  │
                                  Redis ← idempotency keys, cache, blacklist
                                  MailHog (8025) ← outbound mail in dev
```

### Module layout

```
ecommerce-platform/
├── pom.xml                        # Parent BOM (active modules + commented future ones)
├── Makefile                       # one-command bring-up
├── .env.example                   # All required env vars; copy to .env
├── scripts/gen-keys.sh            # Generates RSA keypair into .secrets/keys/
├── config-repo/                   # Spring Cloud Config (mounted into config-server)
│   ├── application.yml
│   ├── api-gateway.yml
│   ├── auth-service.yml
│   ├── product-service.yml
│   ├── inventory-service.yml
│   ├── order-service.yml
│   ├── payment-service.yml
│   └── notification-service.yml
├── docker/
│   ├── docker-compose.yml         # postgres, mongo, redis, kafka, ES, MailHog,
│   │                              # prometheus, grafana, loki, tempo + 8 services
│   └── observability/
│       ├── prometheus.yml
│       └── tempo.yaml
└── services/
    ├── common/                    # Shared events, security, errors, DTOs, Kafka utils, Feign auth
    ├── api-gateway/               # SCG MVC + Bucket4j + header stripper
    ├── auth-service/              # JWT issuer with KeyManager + JWKS, Postgres + Flyway
    ├── product-service/           # Mongo + Elasticsearch catalog, OAuth2 RS
    ├── inventory-service/         # Mongo with atomic findAndModify reservation
    ├── order-service/             # Saga: ProductClient + InventoryClient + payment-event listener
    ├── payment-service/           # Stripe / sandbox + HMAC webhook
    └── notification-service/      # Mongo-persisted notifications + DLT handling
```

### Core technology choices
- **Spring Boot 3.3.5** + Spring Cloud 2023.0.3 (downgraded from 4.0.x preview).
- **OAuth2 Resource Server** in every backend service; JWKS pulled from `auth-service`.
- **JWT signing** with RS256 via `auth-service.security.KeyManager` (current+previous key rotation).
- **PostgreSQL** for auth-service (with Flyway migrations).
- **MongoDB** for product, order, inventory, payment, notification.
- **Elasticsearch** for product search.
- **Redis** for token blacklist, idempotency keys, inventory cache.
- **Kafka** for events; `acks=all`, `enable.idempotence=true`, ErrorHandlingDeserializer + DLT-aware error handler routes poison messages to `<topic>.DLT`.
- **MailHog** in compose at port 8025 (web UI) for dev mail capture.
- **Prometheus / Grafana / Loki / Tempo** containers for observability (configs scaffolded; instrumentation not yet wired in services).
- **Stripe Java SDK** for payments (active when `STRIPE_SECRET_KEY` set; `SandboxGateway` otherwise).

---

## What is built

### Phase 1.1 — Foundations ✅
- Parent POM with proper BOM and pluginManagement (jacoco, cyclonedx, spring-boot, compiler with annotation processor paths).
- `services/common`:
  - `constant/{Topics,Roles,Permissions}.java` — every Kafka topic, role, and permission name lives here.
  - `dto/{ApiResponse,ErrorResponse,PageResponse}.java` — standard envelopes.
  - `exception/{BusinessException,ResourceNotFoundException,DuplicateResourceException,ForbiddenOperationException,ValidationException,GlobalExceptionHandler}.java` — unified error model with field-level validation handling.
  - `event/{BaseEvent,UserEvent,ProductEvent,OrderEvent,PaymentEvent,InventoryEvent,CartEvent,ReviewEvent,AuditEvent}.java` — schema-versioned events with `eventId`, `traceId`, `timestamp`. `OrderEvent.Item` subdoc.
  - `security/{ResourceServerSecurityConfig,JwtAuthenticationConverter,CurrentUser,HmacSignatureVerifier}.java` — drop-in resource-server config (gated by `common.security.resource-server.enabled`), JWT-to-authority mapping that fixes the role double-prefix bug, `CurrentUser` helper that replaces the old X-User-Id reads, Stripe-style `t=…,v1=…` HMAC verification.
  - `kafka/{KafkaProducerProps,KafkaConsumerProps,KafkaErrorHandlerFactory}.java` — idempotent producer defaults, ErrorHandlingDeserializer-wrapped consumer defaults, DLT-aware error handler factory (3 retries, 1 s backoff, NOT_RETRYABLE for IllegalArgumentException/IOException).
  - `idempotency/IdempotencyService.java` — Redis-backed `setIfAbsent` gate.
  - `feign/FeignAuthForwardingConfig.java` — propagates inbound JWT to outbound Feign calls so service-to-service traffic stays authenticated.

### Phase 1.2 — Auth-service hardened ✅
- **Removed** committed `private.pem`/`public.pem`. Treat the old keys as compromised (rotate before any prod-like deploy).
- `scripts/gen-keys.sh` writes a fresh PKCS-8 keypair into `.secrets/keys/` (gitignored, mode 600).
- `security/{KeyManager,JwtKey}.java` loads RSA from a configurable `Resource` URI with current+previous key rotation; ephemeral fallback in dev with a loud warning.
- `service/JwtService` — RS256, includes `kid` in header, `keyLocator` selects matching key for verify.
- `controller/JwkSetController` — JWKS exposing both keys for rotation; correctly strips BigInteger sign byte.
- Public registration always assigns `ROLE_CUSTOMER`. The previous `userType=SELLER` self-elevation is gone. Sellers are promoted via `PUT /api/admin/users/{id}/roles`.
- Bootstrap admin: `ADMIN_EMAIL` + `ADMIN_PASSWORD` env vars create one on first start; skipped if either is unset.
- `/api/auth/change-password` is `@PreAuthorize("isAuthenticated()")` and uses the JWT subject as user id (no NPE).
- Refresh tokens stored as SHA-256 hashes (`TokenHasher`) with family-based reuse detection.
- Cookie `Secure` flag driven by `security.cookies.secure` config.
- `CustomOAuth2SuccessHandler` uses configurable `app.frontend-base-url` + `app.oauth2-redirect-path` (no more hardcoded localhost).
- `KafkaProducerConfig` defines a bounded `kafkaPublisherExecutor`.
- `DataInitializer` seeds permissions + 4 roles (ADMIN, SELLER, CUSTOMER, SUPPORT) using common's constants.
- `db/migration/V1__initial_schema.sql` — full schema (users, roles, permissions, refresh_tokens with token_hash CHAR(64), plus future tables for MFA, magic-link, password-reset, consent log).
- `dto/admin/{CreateRoleRequest,AssignRolesRequest,UpdateRolePermissionsRequest}.java` — typed DTOs replacing raw Strings.
- `AuthServiceApplication` — scans both `com.project.authservice` and `com.project.common`.

### Phase 1.3 — OAuth2 Resource Server migration ✅
Every backend service:
- Inherits parent BOM, depends on `common`, uses `spring-boot-starter-oauth2-resource-server`.
- Has its own `SecurityConfig` that sets up the resource server with the common `JwtAuthenticationConverter` (no role double-prefix).
- All `HeaderAuthenticationFilter` classes deleted; the X-User-Id trust model is gone.
- Controllers use `CurrentUser.requireId()` / `CurrentUser.email()` / `CurrentUser.isAdmin()` instead of `@RequestHeader("X-User-Id")`.
- Application classes scan `com.project.<service>` + `com.project.common`.

### Phase 1.4 — order-service / inventory-service authorization ✅
- `order-service`: every endpoint authenticated; `@PreAuthorize` with `Permissions.ORDERS_*` constants. Customers can only see/cancel their own orders unless they have `ROLE_ADMIN`. Status updates are admin-only.
- `inventory-service`: writes require `INVENTORY_WRITE`; deletes are `ROLE_ADMIN` only; reads require auth; reservation/release endpoints are `isAuthenticated()` (any logged-in customer can reserve their own items via the order saga, with the user JWT propagated by Feign).
- `inventory-service.InventoryServiceImpl.reserveStock` now uses `MongoTemplate.findAndModify` with a `$expr: { $gte: [...] }` guard so concurrent reservations cannot over-commit. Release uses a similar atomic `$inc`.
- `getLowStockItems` is a Mongo aggregation (`$lte`) not an in-memory filter.

### Phase 1.5 — payment-service hardening ✅
- HMAC-signed in-house webhook (`X-Webhook-Signature: t=…,v1=…`) verified via `HmacSignatureVerifier` with 5-minute tolerance (configurable via `payment.webhook.tolerance-seconds`).
- `payments:refund` authority required for refunds. Supports partial amounts, idempotency-keyed retries.
- `PaymentGateway` interface with two impls:
  - `SandboxGateway` (default, deterministic — succeeds unless `orderNumber` contains "FAIL").
  - `StripeGateway` (active when `STRIPE_SECRET_KEY` is set) — proper minor-units conversion, `automaticPaymentMethods` for 3DS/SCA, refund reasons, Stripe-side idempotency keys.
- `PaymentServiceImpl` uses `StringRedisTemplate` for create + refund idempotency.

### Phase 2.1 — ProductClient ✅
- `services/order-service/src/main/java/com/project/order/client/ProductClient.java` — Feign client to product-service via Eureka.
- `client/dto/ProductSummary.java` — subset response with `@JsonIgnoreProperties(ignoreUnknown=true)`.
- Order creation fetches authoritative price → snapshots `unitPrice`/`totalPrice` into `OrderItem`. **The "$5.99 for everything" bug is gone.**

### Phase 2.2 — Order saga ✅
- `OrderServiceImpl.createOrder` flow:
  1. Idempotency lookup via Redis (`order:idemp:<userId>:<key>`).
  2. Fetch every product snapshot via `ProductClient`; reject if not active.
  3. Compute totals (subtotal + 18% GST placeholder + shipping + discount).
  4. Persist order PENDING.
  5. Reserve stock via `InventoryClient` for each line; on failure release the already-reserved items and abort.
  6. Publish `OrderEvent.CREATED` so payment-service can initiate the PaymentIntent.
- `kafka/PaymentEventListener` consumes `payment-events` and calls `onPaymentResult()`:
  - COMPLETED → status CONFIRMED, paidAt timestamp, publish `OrderEvent.PAYMENT_COMPLETED`.
  - FAILED → release reserved stock, status CANCELLED, publish `OrderEvent.PAYMENT_FAILED`.
  - REFUNDED / PARTIALLY_REFUNDED → publish status change.
- Cancel endpoint releases stock.

### Phase 2.3 — Event schema unification ✅
- All three duplicated `OrderEvent` classes deleted. Every service publishes/consumes `com.project.common.event.*`.
- All Kafka producer configs use `KafkaProducerProps.defaults(${spring.kafka.bootstrap-servers})` — no more hardcoded `localhost:9092`.
- All consumer configs use `KafkaConsumerProps.defaults(...)` with `ErrorHandlingDeserializer` + `JsonDeserializer` (trusted packages locked to `com.project.common.event`).
- Order-service `KafkaConfig` wires both producer and DLT-aware listener container factory; payment-events listener is wired through it.
- Notification-service `KafkaConfig` similarly.

### Phase 2.4 — Notification persistence + MailHog ✅
- `Notification` is now a Mongo document with `Status` enum (PENDING/SENT/FAILED/READ) and a unique-sparse `sourceEventId` for idempotent delivery.
- `NotificationRepository`: `findByUserIdOrderByCreatedAtDesc`, `countByUserIdAndStatus`, `findBySourceEventId`.
- `NotificationService.record(...)` dedupes by `sourceEventId`, persists, sends mail when `channel=EMAIL`.
- Four event handlers (User/Order/Payment/Inventory) consume common events with the unified `KafkaConfig`.
- REST API: `GET /api/v1/notifications`, `GET /api/v1/notifications/unread/count`, `POST /api/v1/notifications/{id}/read`. All `@PreAuthorize("isAuthenticated()")`.
- MailHog added to `docker-compose` at port 8025 (web UI) / 1025 (SMTP).

### Cross-cutting infra ✅
- `docker/docker-compose.yml` rewritten:
  - `.env`-driven credentials (POSTGRES_*, MONGO_INITDB_ROOT_*, REDIS_PASSWORD, STRIPE_*, AUTH_RSA_*).
  - YAML anchor `&svc-common-env` for shared env across services.
  - Docker secrets mounting auth keys from `.secrets/keys/`.
  - `kafka-init` creates 11 topics + their `.DLT` counterparts.
  - Healthchecks rely on `/actuator/health`.
  - MailHog, Prometheus, Grafana, Loki, Tempo containers.
- `Makefile` targets: `keys`, `env`, `build`, `up`, `down`, `logs`, `reset`, `psql`, `mongo`, `help`.
- All 8 services have multi-stage Dockerfiles using Spring Boot's layered jars (`Djarmode=layertools`).
- Cleanup: removed `services/user-service` (empty), `services/hello-world` (legacy), `services/.idea`, `services/auth-service/README.md` (outdated). Parent POM commented out the 11 unbuilt service modules.

---

## Endpoints by service

> All paths shown without the gateway prefix; access them via `http://localhost:8080/<path>`.

### auth-service (8081)
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Register customer |
| POST | `/api/auth/token` | public | grant_type=password \| refresh_token; returns access token; sets refresh cookie |
| POST | `/api/auth/logout` | public | Blacklists access token, revokes refresh token |
| POST | `/api/auth/change-password` | authenticated | Self-service password change |
| GET | `/api/user/profile` | authenticated | Current user profile |
| PUT | `/api/user/profile` | authenticated | Update displayName |
| GET | `/api/admin/users` | `admin:users:read` | List users |
| GET | `/api/admin/users/{id}` | `admin:users:read` | One user |
| PUT | `/api/admin/users/{id}/roles` | `admin:users:write` | Assign roles |
| PUT | `/api/admin/users/{id}/active` | `admin:users:write` | Activate/deactivate |
| GET | `/api/admin/roles` | `admin:roles:read` | List roles |
| POST | `/api/admin/roles` | `admin:roles:write` | Create role |
| PUT | `/api/admin/roles/{id}/permissions` | `admin:roles:write` | Set role permissions |
| DELETE | `/api/admin/roles/{id}` | `admin:roles:write` | Delete role |
| GET | `/.well-known/jwks.json` | public | JWKS used by every backend service |
| GET | `/oauth2/**`, `/login/oauth2/**` | public | Google / GitHub social login |

### product-service (8082)
| Method | Path | Auth |
|---|---|---|
| GET | `/api/v1/products`, `/api/v1/products/{id}`, `/api/v1/products/search?keyword=`, `/api/v1/products/category/{id}`, `/api/v1/products/seller/{id}`, `/api/v1/products/filter` | **public** |
| GET | `/api/v1/categories`, `/api/v1/categories/{id}`, `/api/v1/categories/{id}/products` | public |
| GET | `/api/v1/products/seller` | SELLER or ADMIN |
| POST | `/api/v1/products` | `products:create` |
| PUT | `/api/v1/products/{id}` | `products:update` (own product or admin) |
| DELETE | `/api/v1/products/{id}` | `products:delete` |
| PATCH | `/api/v1/products/{id}/stock` | `products:update` |
| PUT | `/api/v1/products/admin/{id}/active` | ADMIN |
| POST/PUT/DELETE | `/api/v1/categories/**` | ADMIN |

### inventory-service (8083)
| Method | Path | Auth |
|---|---|---|
| GET | `/api/v1/inventory` | INVENTORY_READ \| ADMIN \| SELLER |
| GET | `/api/v1/inventory/{productId}`, `/api/v1/inventory/sku/{sku}`, `/api/v1/inventory/{id}/check` | authenticated |
| GET | `/api/v1/inventory/low-stock` | ADMIN \| SELLER |
| POST/PUT | `/api/v1/inventory`, `/api/v1/inventory/{id}` | INVENTORY_WRITE |
| DELETE | `/api/v1/inventory/{id}` | ADMIN |
| POST | `/api/v1/inventory/{id}/add-stock` | INVENTORY_WRITE |
| POST | `/api/v1/inventory/{id}/reserve`, `/api/v1/inventory/{id}/release` | authenticated (called by order-service via Feign with user JWT) |

### order-service (8084)
| Method | Path | Auth |
|---|---|---|
| POST | `/api/v1/orders` | `orders:create` (header `X-Idempotency-Key` supported) |
| GET | `/api/v1/orders` | `orders:read` (admins see all, customers see own) |
| GET | `/api/v1/orders/{id}`, `/api/v1/orders/number/{orderNumber}` | `orders:read` (own or admin) |
| PUT | `/api/v1/orders/{id}/status` | `orders:update` AND ROLE_ADMIN |
| POST | `/api/v1/orders/{id}/cancel` | `orders:cancel` |
| GET | `/api/v1/orders/status/{status}` | ROLE_ADMIN |

### payment-service (8085)
| Method | Path | Auth |
|---|---|---|
| POST | `/api/v1/payments` | `payments:process` or ROLE_CUSTOMER |
| GET | `/api/v1/payments` | authenticated (own list) |
| GET | `/api/v1/payments/{id}`, `/api/v1/payments/reference/{ref}`, `/api/v1/payments/order/{orderId}` | `payments:read` |
| POST | `/api/v1/payments/{id}/process` | `payments:process` |
| POST | `/api/v1/payments/{id}/refund` | `payments:refund` (header `X-Idempotency-Key`) |
| POST | `/api/v1/payments/webhook` | **public**, HMAC-verified inside controller |

### notification-service (8086)
| Method | Path | Auth |
|---|---|---|
| GET | `/api/v1/notifications` | authenticated |
| GET | `/api/v1/notifications/unread/count` | authenticated |
| POST | `/api/v1/notifications/{id}/read` | authenticated |

---

## Event topics & schemas

All schemas live in `com.project.common.event.*` and extend `BaseEvent` (eventId, schemaVersion, traceId, timestamp).

| Topic | Producer | Consumers | Schema |
|---|---|---|---|
| `user-events` | auth-service | notification-service | `UserEvent { type, userId, email, displayName }` |
| `product-events` | product-service | (future: search-indexer, recommendations) | `ProductEvent { type, productId, sku, name, sellerId }` |
| `order-events` | order-service | notification-service, (future: analytics) | `OrderEvent { type, orderId, orderNumber, userId, userEmail, totalAmount, currency, items[] }` |
| `payment-events` | payment-service | order-service, notification-service | `PaymentEvent { type, paymentId, paymentReference, orderId, userId, amount, currency, paymentMethod, failureReason }` |
| `inventory-events` | inventory-service | notification-service (low-stock alerts) | `InventoryEvent { type, productId, sku, warehouseId, quantityChange, newQuantity, reservedQuantity, availableQuantity, orderId, productName }` |
| `cart-events` | (cart-service, future) | (future) | `CartEvent` |
| `review-events` | (review-service, future) | (future) | `ReviewEvent` |
| `audit-events` | every service | (audit-service, future) | `AuditEvent` |
| `coupon-events`, `shipping-events`, `notification-events` | (future) | (future) | placeholder |

Each topic also has a `<topic>.DLT` companion auto-created by `kafka-init`.

---

## Security model

1. **Edge** (api-gateway):
   - No authentication — strips client-supplied `X-User-Id`/`X-User-Email`/`X-Roles` headers in `ClientHeaderStrippingFilter` (highest precedence).
   - Per-IP Bucket4j rate limit: 100/min default, 20/min on `/api/auth/*`.
   - Forwards `Authorization: Bearer <jwt>` unchanged.
2. **Service-to-service**:
   - Each backend is an OAuth2 Resource Server with `jwk-set-uri` pointing at auth-service's JWKS.
   - Common's `JwtAuthenticationConverter` maps JWT `roles` (already `ROLE_*` prefixed) and `permissions` claims to authorities — no double-prefix.
   - Feign-based service calls (order → product, order → inventory) propagate the inbound JWT via `FeignAuthForwardingConfig`.
3. **JWT signing**:
   - RS256 with kid header. Auth-service's `KeyManager` loads PEMs from a configurable `Resource` URI; supports current+previous key rotation.
   - JWKS exposes both keys so verifiers don't break during a rotation window.
4. **Refresh tokens**:
   - SHA-256 hashed before persistence; reuse triggers family-wide revocation.
5. **Webhooks**:
   - Payment webhook is HMAC-signed (Stripe-style `t=…,v1=…`) with 5 min tolerance.

---

## How to run locally

```bash
# 1. Bootstrap secrets (gitignored)
make keys
cp .env.example .env
# Fill in ADMIN_EMAIL, ADMIN_PASSWORD, STRIPE_*, etc.

# 2. Build everything
make build

# 3. Bring up the stack
make up

# 4. Tail logs
make logs

# 5. Tear down
make down
```

UIs:
- **API**: http://localhost:8080
- **MailHog**: http://localhost:8025
- **Eureka**: http://localhost:8761
- **Grafana**: http://localhost:3000 (admin / admin or `${GRAFANA_PASSWORD}`)
- **Prometheus**: http://localhost:9090

---

## Known caveats / verify-before-prod list

- **The build hasn't been compiled in this session.** Spring Boot 3.3.5 + the new package layout has been applied uniformly, but a clean `./mvnw -B clean install` is the next step. Likely friction points if any: residual unused imports, the `OrderItem.imageUrl` field already exists in the model, and Mongo's `MongoTemplate` injection in `inventory-service` requires `spring-boot-starter-data-mongodb`.
- **Image URLs are intentionally null** everywhere per requirements (`ProductServiceImpl.createProduct` sets `imageUrls = null`, etc.). Wire up an image pipeline later.
- **18% GST is a placeholder** in `OrderServiceImpl`. Real engine will live in `tax-service`.
- **`SandboxGateway` is the default payment gateway**. Set `STRIPE_SECRET_KEY` in `.env` to flip to real Stripe (test mode).
- **Old auth-service tests were deleted**. Will be rewritten in Phase 3.2 with Testcontainers.
- **The frontend referenced in `ci.yml`** at `frontend/ecommerce-app/` does not exist yet. About to be built (this session).
- **CI does not yet build/push Docker images** — Phase 3.4 work.
- **No observability instrumentation yet** in the application code (containers exist; OTel exporter to wire up in Phase 3.3).

---

## What is NOT yet built

| Phase | Task | Status |
|---|---|---|
| 3.1 | Sample data seeders (20-30 records each: users, products, categories, inventory, orders, payments, notifications, coupons, reviews, addresses) | ❌ |
| 11 | Cart service (Redis for guests, Mongo for users; abandoned-cart events) | ❌ |
| 12 | Wishlist service + Reviews service (with moderation queue + verified-purchase badge) | ❌ |
| 13 | Product variants (size/color/material with per-variant SKU/price/stock) | ❌ |
| 14 | Q&A on products + recently-viewed + frequently-bought-together recommender | ❌ |
| 15 | Search improvements (synonyms, faceted filters, completion suggester, sort modes via ES) | ❌ |
| 16 | Coupon service with rule engine + flash sales + loyalty + gift cards | ❌ |
| 17 | Multi-currency + i18n + India GST tax engine (CGST/SGST/IGST per HSN) | ❌ (placeholder rate is in order-service) |
| 18 | Stripe PaymentIntent with 3DS/SCA + in-house fraud velocity rules | 🟡 (Stripe SDK + sandbox gateway done; fraud rules pending) |
| 19 | Order modifications + shipping rate engine + RMA + subscriptions + multi-warehouse + stock alerts | ❌ |
| 20 | Marketplace (KYB onboarding + seller dashboard + Stripe Connect + buyer-seller messaging + ratings) | ❌ |
| 21 | Customer engagement (profile/addresses/payment-methods, MFA TOTP, magic-link, sessions, GDPR) | ❌ |
| 22 | Admin BFF + CMS + feature flags (Unleash OSS) + campaign manager + Customer-360 | ❌ |
| 23 | Analytics (Debezium CDC → ClickHouse + recommendations + search relevance + forecasting) | ❌ |
| 3.2 | Testcontainers tests + JaCoCo coverage gate + Spring Cloud Contract + E2E happy path | ❌ |
| 3.3 | Observability wiring (micrometer-tracing-bridge-otel + OTLP exporter + Loki appender + dashboards + alert rules) | 🟡 (containers in compose; instrumentation pending) |
| 3.4 | CI overhaul (multi-stage Dockerfiles, GHCR push, SBOM via CycloneDX, Trivy, OWASP-DC, cosign keyless) | 🟡 (multi-stage Dockerfiles done; CI not yet updated) |
| 3.5 | Secrets via sops+age + Sealed Secrets + Vault dev mode | 🟡 (.env / docker-secrets done; sops/Vault pending) |
| 3.6 | Service mesh (Linkerd) + NetworkPolicies + Helm + GraphQL BFF + Backstage + hCaptcha + audit logs | ❌ |
| Final | ARCHITECTURE.md, RUNBOOK.md, CONTRIBUTING, SECURITY, CODEOWNERS docs | 🟡 (README.md done; rest pending) |

---

## Recommended next sessions

When you come back, here's the suggested order. Each line is a self-contained chunk you can ask for in one session.

1. **"Build the frontend"** ← in progress this session
2. **"Verify the backend builds end-to-end and run the saga happy-path"** — `mvn clean install` per service, fix any compile issues, write a simple E2E script that registers → logs in → creates product → creates order → completes payment via webhook → checks notification.
3. **"Build the cart-service and coupon-service"** — they're the most-used storefront services after product/order.
4. **"Build sample-data seeders"** — every service gets a `DataInitializer` that runs only when its collection is empty; 20–30 records per entity type.
5. **"Wire observability instrumentation"** — add `micrometer-tracing-bridge-otel`, OTLP exporter, Loki logback appender, and check in 4–5 Grafana dashboards.
6. **"Build review-service and wishlist-service"**.
7. **"Build the tax-service for India GST + multi-currency support"**.
8. **"Build seller-service for marketplace onboarding"**.
9. **"Build the analytics-service with Debezium CDC + ClickHouse"**.
10. **"CI overhaul: GHCR push, SBOM, Trivy, OWASP-DC, cosign"**.
11. **"Helm charts + Linkerd mesh + NetworkPolicies for k8s deployment"**.
12. **"Add MFA TOTP, magic-link login, GDPR data export"**.

Anything in the "What is NOT yet built" table is fair game in any order.
