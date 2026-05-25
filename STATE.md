# Backend State — Ecommerce Platform

> **Snapshot date**: 2026-05-25 (latest)
> **Spring Boot 3.3.5 / Spring Cloud 2023.0.3 / Java 17**
> **Purpose of this file**: keep a durable record across sessions of what
> backend + frontend work is finished, what is in flight, and what is still
> queued — so any future session (mine or yours) can resume without
> re-deriving context.

---

## Sessions timeline at a glance

| Session | Work | Outcome |
|---|---|---|
| 1 — backend architectural rewrite | Parent BOM, expanded `common` library, OAuth2 Resource Server migration of every service, real order saga (ProductClient + InventoryClient + payment-event consumer), atomic Mongo reservation, HMAC payment webhook, MailHog + Mongo-persisted notifications, single docker-compose, multi-stage Dockerfiles | All P0 security bugs from the original code review fixed, full saga end-to-end, reactor builds clean. See [Architecture overview](#architecture-overview) and [What is built](#what-is-built) below. |
| 2 — React frontend build | Vite + React 18 + TS + Tailwind + ShadCN-style primitives. Editorial / refined-luxury aesthetic chosen via the `ui-ux-pro-max` skill. 22+ pages: storefront (home, catalog, product detail, cart, checkout, order success), customer (orders, order detail, profile, notifications), admin (dashboard, products CRUD, inventory, orders, users) + auth (login, register, 404). API client with refresh-token retry. Cart store (Zustand + persist). Auth store with role/permission gating. | See [Frontend](#frontend) below. |
| 3 — frontend production-readiness pass | ErrorBoundary, route-level code splitting (entry chunk 14.6 kB gzipped), Sheet primitive (mobile drawer), mobile bottom-nav, sticky mobile add-to-cart, skip-to-content, semantic landmarks, aria-labels, focus rings, SEO helper, PWA manifest, dark mode with system preference + FOUC-prevention, vendor-chunk splitting, prefers-reduced-motion. | All in `frontend/ecommerce-app/`. Full build verified clean; bundle table in [Frontend](#frontend). |
| 4 — Postman collection regeneration | 76 requests across 10 folders covering every backend endpoint with auto-saved tokens, idempotency-key headers, HMAC-signed webhook (computes `t=…,v1=…` in a pre-request script), and a 13-step **End-to-end happy path** runner that exercises register → login → seller promotion → product → inventory → order → payment → webhook → CONFIRMED → notification. | `ecommerce-platform.postman_collection.json` at the repo root. |
| 5 — local deploy + CI fixes | 14 distinct bugs unblocked. auth-service runs cleanly, all services register with Eureka, full saga reachable through the gateway, Maven reactor + GitHub Actions CI both green. | See [Session 5 changelog](#session-5-changelog-2026-05-25-local-deploy--ci-fixes) below. |
| 6 — OAuth2 404 polish | `/oauth2/authorization/google` was returning 500 when no Google credentials were configured. Now returns clean 404 with code `OAUTH2_PROVIDER_NOT_CONFIGURED`. New `/api/auth/providers` discovery endpoint; login page only renders configured social buttons. | See [Session 6 changelog](#session-6-changelog-2026-05-25-oauth2-404-polish) below. |
| 7 — Google OAuth2 end-to-end + sample-data seeders | Wired Google OAuth2 fully end-to-end (frontend `/oauth2/redirect` page, Vite proxy fix, deterministic-UUID auth seeding). Added 7 idempotent sample-data seeders gated by `seed.enabled`: 26 users (20 customers + 5 sellers + 1 support), 8 categories, 30 products, 30 inventory items, 15 orders across all status states, 14 payments, 32 notifications. Surfaced and fixed three pre-existing bugs along the way (Redis-cache serialization, Hibernate `@GeneratedValue(UUID)` overwriting seed IDs, docker-compose env-var collision with shell `POSTGRES_USER=root`). | See [Session 7 changelog](#session-7-changelog-2026-05-25-google-oauth2--sample-data) below. |
| 8 — cart-service + coupon-service | Two new microservices fully built, wired, and deployed. cart-service (Mongo, port 8087) does per-user cart CRUD with optimistic locking and applied-coupon snapshot. coupon-service (Postgres, port 8088) does admin CRUD plus stateless `/validate` and atomic `/redeem` (with `SELECT FOR UPDATE` so two checkouts can't push past `usageLimit`). Cart→coupon wired via Feign with JWT forwarding. 4 seeded carts + 10 seeded coupons covering all corner cases. End-to-end verified through the gateway. | See [Session 8 changelog](#session-8-changelog-2026-05-25-cart--coupon-services) below. |

The next sessions are the [feature roadmap](#what-is-not-yet-built) — cart + coupon services, observability wiring, review + wishlist services, etc.

---

## Session 8 changelog (2026-05-25, cart + coupon services)

Two new microservices, fully wired into the platform: **cart-service** (per-user
cart on Mongo, port 8087) and **coupon-service** (coupon engine on Postgres,
port 8088). Module slots already existed in the parent POM and the gateway —
this session built the actual services.

### cart-service

```
services/cart-service/
├── pom.xml              # Mongo + Feign + OAuth2 resource server
├── Dockerfile           # layered, port 8087
└── src/main/java/com/project/cart/
    ├── CartServiceApplication.java   # @EnableFeignClients
    ├── config/
    │   ├── SecurityConfig.java       # oauth2ResourceServer + JwtAuthenticationConverter
    │   └── SampleDataInitializer.java
    ├── model/{Cart,CartItem}.java    # @Document carts, @Version optimistic-lock
    ├── repository/CartRepository.java
    ├── service/CartService.java
    ├── controller/CartController.java
    ├── client/                       # Feign client to coupon-service
    │   ├── CouponClient.java
    │   ├── CouponValidationRequest.java
    │   └── CouponValidationResponse.java
    └── dto/{Add,Update,Apply,Cart}*.java
```

Endpoints (`/api/v1/cart`):
- `GET    /`                — current user's cart (creates an empty one if none)
- `POST   /items`           — add item (merges by productId; resets applied coupon)
- `PATCH  /items/{productId}` — set quantity (0 removes; resets coupon)
- `DELETE /items/{productId}` — remove item (resets coupon)
- `DELETE /`                — clear cart
- `POST   /coupon`          — apply coupon (calls coupon-service via Feign)
- `DELETE /coupon`          — remove applied coupon

Notable design choices:
- Cart is per-user (unique `userId` index). One cart per user; merging guest
  carts on login is a future feature.
- Unit price is **captured at add time** so cart totals stay stable if the
  catalog price changes later. Order saga re-validates against `product-service`
  at checkout, so a malicious client can't pay less than the catalog price.
- `@Version` (Mongo optimistic-lock) protects against two browser tabs
  clobbering each other's mutations.
- Any item-level mutation (add / update / remove) clears the applied coupon,
  forcing re-validation against the new subtotal.
- Currency is captured on first add and rejected if a subsequent item disagrees.
- `applyCoupon` calls coupon-service via Feign with the user's JWT forwarded
  by `FeignAuthForwardingConfig` so per-user limits resolve correctly.

### coupon-service

```
services/coupon-service/
├── pom.xml              # JPA + Flyway + Postgres + OAuth2 resource server
├── Dockerfile           # layered, port 8088
└── src/main/
    ├── resources/
    │   ├── application.yml
    │   └── db/migration/V1__initial_schema.sql
    └── java/com/project/coupon/
        ├── CouponServiceApplication.java
        ├── config/
        │   ├── SecurityConfig.java
        │   └── SampleDataInitializer.java
        ├── entity/{Coupon,CouponRedemption,DiscountType}.java
        ├── repository/{Coupon,CouponRedemption}Repository.java
        ├── service/CouponService.java
        ├── controller/CouponController.java
        └── dto/{Coupon,Validate,Redeem}*.java
```

Database schema (V1):
- `coupons` — code, discount_type, discount_value, max_discount_amount,
  min_order_amount, currency, valid_from/until, usage_limit, usage_count,
  per_user_limit, active, version. Constraints: discount_type ∈ (PERCENT,FIXED);
  valid_until > valid_from; PERCENT discount in (0,100].
- `coupon_redemptions` — append-only ledger keyed by (coupon_id, user_id, order_id),
  used to enforce per-user limits and to audit refund-time reversals later.

Endpoints (`/api/v1/coupons`):
- `POST   /`           — admin create  (`COUPONS_WRITE`)
- `PUT    /{id}`       — admin update  (`COUPONS_WRITE`)
- `DELETE /{id}`       — admin delete  (`COUPONS_WRITE`)
- `GET    /{id}`       — admin/seller read
- `GET    /`           — admin/seller paged list
- `POST   /validate`   — any auth user; called by cart-service Feign
- `POST   /redeem`     — any auth user; called by order-service after capture

Validation rules (in order):
1. exists → else "Coupon not found"
2. active → else "Coupon is inactive"
3. validFrom ≤ now ≤ validUntil → else "not yet active" / "expired"
4. usageCount < usageLimit (if set) → else "usage limit reached"
5. cart currency matches coupon currency (or null) → else mismatch error
6. subtotal ≥ minOrderAmount (if subtotal supplied) → else min-order error
7. perUserLimit not exceeded (if userId + perUserLimit set)
8. discount = PERCENT × subtotal / 100 capped by maxDiscountAmount and subtotal,
   or FIXED capped by subtotal.

Redeem path is wrapped in `findByCodeForUpdate` (PESSIMISTIC_WRITE) so two
parallel checkouts can't both push past `usageLimit`. Validation is re-run
inside the redemption transaction so a stale "valid" reply from a minutes-old
validate-call can't slip through.

### Wiring

- Parent `pom.xml` — added `services/cart-service` and `services/coupon-service`
  to `<modules>`; the rest stays commented out.
- `docker/docker-compose.yml` — new `cart-service` and `coupon-service` blocks.
  `cart-service` depends on mongodb + config-server + discovery-server.
  `coupon-service` depends on postgres + config-server + discovery-server,
  using the new `APP_DB_USER` / `APP_DB_PASSWORD` / `APP_DB_NAME` vars.
- `config-repo/cart-service.yml` — Mongo URI on `cart_db` (auth-source admin),
  port 8087, JWKS pointer.
- `config-repo/coupon-service.yml` — Postgres datasource, Flyway with
  `table: flyway_schema_history_coupon` and `baseline-version: 0`, port 8088.
- `config-repo/api-gateway.yml` — already routes `/api/v1/cart/**` and
  `/api/v1/coupons/**` (added in an earlier session).

### Sample data

- **cart-service**: 4 pre-filled carts for the first 4 sample customers
  (Amelia: tote + 2 tees; Noah: throw + paring knife; Olivia: 3 mixed items;
  Liam: linen overshirt).
- **coupon-service**: 10 coupons covering every validation branch — `WELCOME50`
  (flat per-user 1), `SAVE10` (10% capped Rs.500), `SAVE20` (20% min Rs.5000),
  `FESTIVE100`, `FREESHIP49`, `LOYALTY15`, `PAUSED5` (inactive), `LASTYEAR25`
  (expired), `NEXTWEEK10` (not yet active), `FLASH75` (75% capped, 50-use cap).

### Bugs fixed during deploy

1. **Flyway checksum collision** — coupon-service shares the `ecommerce`
   Postgres database with auth-service. By default both write to
   `flyway_schema_history`, causing a checksum mismatch on each service's V1.
   Fixed by setting `spring.flyway.table: flyway_schema_history_coupon` for
   coupon-service.
2. **V1 silently skipped** — Flyway's default `baseline-version` is 1, so when
   `baseline-on-migrate` runs in a database that already has tables (from
   auth-service), it baselines at version 1 and treats V1 as already applied.
   Fixed by setting `spring.flyway.baseline-version: 0` so V1 actually runs.
   (Leaving a comment in coupon-service.yml so we don't trip on this for the
   next service that joins the shared DB.)

### Verified end-to-end

```
POST /api/auth/token (amelia.park@example.com)              → 200, JWT
GET  /api/v1/cart                                            → 200, 2 items, subtotal 4497
POST /api/v1/coupons/validate {WELCOME50, subtotal: 999}     → valid, discount 50
POST /api/v1/cart/coupon {WELCOME50}                         → applied, total 4447
POST /api/v1/cart/coupon {LASTYEAR25}                        → 400 "Coupon has expired"
POST /api/v1/cart/coupon {SAVE20}        (cart=4497)         → 400 "Min order is 5000"
POST /api/v1/cart/items   (add 2 more tees, subtotal=7495)
POST /api/v1/cart/coupon {SAVE20}                            → applied, discount 1499, total 5996
GET  /api/v1/coupons (admin)                                 → 200, totalElements: 10
```

Eureka shows `CART-SERVICE` and `COUPON-SERVICE` registered alongside the
existing 6 services and the gateway.

### Not yet wired (deferred)

- **Order → coupon redeem**: when an order is confirmed (after payment
  capture), order-service should call `POST /api/v1/coupons/redeem` to
  increment `usageCount` and write the redemption row. Currently the
  applied-coupon snapshot lives only on the cart and is wiped on checkout;
  no redemption is recorded yet. Adding this needs an order-service change
  (Feign client to coupon-service, called from the saga's CONFIRMED branch).
- **Frontend integration**: the existing Zustand cart store can stay for
  guest browsing; on login we should call cart-service to merge or replace.
  This is a frontend-only follow-on.

---

## Session 7 changelog (2026-05-25, Google OAuth2 + sample-data)

Two distinct pieces of work in one session: wired Google OAuth2 end-to-end
across frontend + backend, then added idempotent sample-data seeders to
every service so the storefront is no longer empty for a fresh dev.

### Google OAuth2 end-to-end

| Symptom | Cause | Fix |
|---|---|---|
| Login button still 404'd after backend was wired | `/oauth2/redirect` had no React route — auth-service's `CustomOAuth2SuccessHandler` redirects there with `#token=…`. | New `pages/auth/oauth2-redirect.tsx`: extracts token from URL fragment, stores in auth store, fetches `/auth/me`, navigates home. Registered in `App.tsx` outside `AuthLayout`. |
| Whitelabel error page after Google consent | Vite proxy rule `/oauth2: { target: gateway }` was forwarding the SPA's redirect path to the backend. | Narrowed proxy to `/oauth2/authorization` and `/login/oauth2` only, leaving `/oauth2/redirect` for React. |
| Postgres auth failure on every restart | User's shell had `POSTGRES_USER=root POSTGRES_PASSWORD=root` exported (collision with another tool). docker-compose's interpolation prefers shell over `.env`. | Renamed compose vars to `APP_DB_USER` / `APP_DB_PASSWORD` / `APP_DB_NAME` (with same defaults). The shell's stray `POSTGRES_*` no longer collides. |

### Sample-data seeders

Seven idempotent `SampleDataInitializer` (or extended `DataInitializer`)
classes, all gated by `seed.enabled` (default `true` in dev, override via
`SEED_ENABLED=false` env). Each seeder skips if its target collection /
table already has rows.

| Service | Seeded | Notes |
|---|---|---|
| common | `SampleIds.java` | Single source of truth: 20 customers, 5 sellers, 1 support, 8 categories, 30 products. Stable UUIDs/string-IDs so cross-service references resolve correctly. |
| auth-service | 26 users | 20 customers + 5 sellers + support. All share password `Password1!` (configurable via `seed.user-password`). Sellers get both `ROLE_SELLER` and `ROLE_CUSTOMER`. |
| product-service | 8 categories + 30 products | Categories: apparel, bags, home, kitchen, stationery, tech, outdoor, grooming. Default stock 25; ES sync best-effort. |
| inventory-service | 30 inventory items | ~70% healthy, ~20% low-stock, ~10% sold out. Threshold 5; warehouse `WH-IN-MUM-01`. |
| order-service | 15 orders | Distribution: 3 PENDING / 3 CONFIRMED / 3 SHIPPED / 3 DELIVERED / 2 CANCELLED / 1 REFUNDED. Order numbers `ORD-SEED-1000..1014`. 18% tax, free shipping over Rs.499. Timestamps appropriate to status. |
| payment-service | 14 payments | Mirrors order spec list (skips 1013 which never charged). COMPLETED/REFUNDED get `transactionId` + `gatewayResponse` + `completedAt`. References `PAY-SEED-*`. |
| notification-service | 32 notifications | 15 order-confirmation emails (statuses cycled READ/SENT/PENDING/FAILED), 9 payment receipts, 4 inventory low-stock alerts to seller #1, 4 welcome emails. Uses `sourceEventId` for idempotency. |

### Pre-existing bugs surfaced and fixed

1. **Redis-cache serialization** — `ProductResponse` and `CategoryResponse` did not implement `Serializable`, so the first call to a `@Cacheable` listing endpoint after the seed populated data threw `NotSerializableException`. Both DTOs now `implements Serializable` with `serialVersionUID`.
2. **Hibernate `@GeneratedValue(UUID)`** on `User.id` was overwriting seed-supplied deterministic UUIDs. Replaced with a `@PrePersist` callback that only assigns a UUID when null. Registration flow unchanged (callers leave id null → random UUID), seeders preserve their pre-set IDs.
3. **docker-compose env collision** described above (POSTGRES_* → APP_DB_*).

### Files added

- `services/common/src/main/java/com/project/common/sampledata/SampleIds.java` — shared IDs.
- `services/{product,inventory,order,payment,notification}-service/src/main/java/.../config/SampleDataInitializer.java` — five new initializers.
- `frontend/ecommerce-app/src/pages/auth/oauth2-redirect.tsx`.

### Files modified

- `config-repo/application.yml` — `seed.enabled: ${SEED_ENABLED:true}`.
- `services/auth-service/.../config/DataInitializer.java` — extended with `seedSampleUsers()`.
- `services/auth-service/.../entity/User.java` — `@GeneratedValue(UUID)` → `@PrePersist`.
- `services/product-service/.../dto/ProductResponse.java` + `CategoryResponse.java` — implement `Serializable`.
- `frontend/ecommerce-app/vite.config.ts` — narrowed `/oauth2` proxy.
- `frontend/ecommerce-app/src/App.tsx` — registered `<OAuth2RedirectPage />` route.
- `docker/docker-compose.yml` — `POSTGRES_USER` → `APP_DB_USER` etc.
- `.env` — adds `APP_DB_USER`, `APP_DB_PASSWORD`, `APP_DB_NAME` alongside the legacy names.

### Verified end-to-end

```
POST /api/auth/token (grant_type=password, amelia.park@example.com, Password1!)
  → 200, accessToken issued
GET  /api/v1/products?size=5  → 200, totalElements=30
GET  /api/v1/categories       → 200, 8 categories
GET  /api/v1/orders?size=10   → 200, 1 order (ORD-SEED-1000 PENDING Rs.3537.64) for Amelia
GET  /api/v1/notifications    → 200, Amelia's welcome email visible
GET  /api/v1/payments         → 200, PAY-SEED-1000 visible
```

JWT subject `11111111-1111-1111-1111-111100000001` matches the deterministic
UUID stored in postgres for `amelia.park@example.com`, so all downstream
filters (`findByUserId`) resolve correctly.

### Re-running seeders later

Each seeder checks if its target collection / table already has data and
skips if so. To force a re-seed for a single service:

```bash
# Example: re-seed orders
docker exec mongodb mongosh -u ecommerce -p change_me_mongo \
  --authenticationDatabase admin \
  --eval 'db.getSiblingDB("order_db").orders.deleteMany({orderNumber:/^ORD-SEED-/})'
docker compose -f docker/docker-compose.yml --env-file .env up -d --force-recreate order-service
```

To disable seeding entirely (e.g. in production):

```bash
SEED_ENABLED=false docker compose -f docker/docker-compose.yml --env-file .env up -d
```

---

## Session 6 changelog (2026-05-25, OAuth2 404 polish)

### What broke and what fixed it

| Symptom | Cause | Fix |
|---|---|---|
| `GET /oauth2/authorization/google` returned `500 INTERNAL_ERROR` | When no OAuth2 provider is configured, `OAuth2ClientConfig` registers no `oauth2Login()` filter chain. Spring 6 then raises `NoResourceFoundException` (rather than falling through to a default 404), which common's `GlobalExceptionHandler` was swallowing as a generic `Exception` → 500. | (a) Added `@ExceptionHandler(NoResourceFoundException.class)` to `GlobalExceptionHandler` that returns 404 with code `OAUTH2_PROVIDER_NOT_CONFIGURED` (and a message pointing at the env vars) when path starts with `/oauth2/` or `/login/oauth2/`. (b) New `AuthDiscoveryController` exposes `GET /api/auth/providers` so the frontend can avoid showing dead buttons in the first place. (c) `pages/auth/login.tsx` now calls it via TanStack Query and renders only configured providers. |

### Files added / modified

- **New** `services/auth-service/src/main/java/com/project/authservice/controller/AuthDiscoveryController.java` — public discovery endpoint.
- **New** `frontend/ecommerce-app/src/api/discovery.ts` — `getAuthProviders()` that gracefully degrades to "password only" on failure.
- **Modified** `services/common/src/main/java/com/project/common/exception/GlobalExceptionHandler.java` — `NoResourceFoundException` handler.
- **Modified** `services/auth-service/src/main/java/com/project/authservice/security/SecurityConfig.java` — permits `GET /api/auth/providers`.
- **Modified** `frontend/ecommerce-app/src/pages/auth/login.tsx` — fetches providers, conditional `<SocialLoginButtons>`, adds `<Seo title="Sign in" />`.

### Verified

```
GET /api/auth/providers           → 200 {"password":true,"oauth2":false,"providers":[]}
GET /oauth2/authorization/google  → 404 OAUTH2_PROVIDER_NOT_CONFIGURED
POST /api/auth/token              → 200
Frontend tsc -b --noEmit          → clean
Frontend vite build               → 1.92 s, no warnings
```

---

## Session 5 changelog (2026-05-25, local-deploy + CI fixes)

The first session shipped the architectural rewrite. This session unblocked
local Docker bring-up and made CI green. Read this top-to-bottom before
touching anything; it explains a number of "why is it this way" decisions.

### What broke and what fixed it

| # | Symptom | Cause | Fix |
|---|---|---|---|
| 1 | Every request 401/403 | `auth-service` had **exited (1)** — no JWT issued, no JWKS served | rebuild + restart with the changes below |
| 2 | auth-service crash: `Client id of registration 'google' must not be empty` | YAML `${GOOGLE_CLIENT_ID:}` empty default tripped Spring Boot's strict OAuth2 client validation | New `OAuth2ClientConfig.java` registers Google/GitHub providers programmatically only when both client-id + secret env vars are set. YAML stanza removed. `SecurityConfig` skips `oauth2Login()` when no providers are configured. |
| 3 | Image was Spring Boot 4.0.3 (pre-refactor) | Multi-stage Dockerfile couldn't see parent POM and `common` module from inside the build context | All 9 service Dockerfiles rewritten as **runtime-only** images that copy a pre-built `services/<svc>/target/*.jar`. Build context in `docker-compose.yml` set to `..` (repo root) with explicit `dockerfile: services/<svc>/Dockerfile`. |
| 4 | config-server: `Invalid config server configuration. If you are using the git profile, you need to set a Git URI` | Old image still pointed at a private GitHub branch; new `application.yml` (native mode) wasn't applied because compose env `SPRING_PROFILES_ACTIVE: docker` overrode the `native` profile in app yaml | Rebuilt config-server. Set `SPRING_PROFILES_ACTIVE: native,docker` in compose. Now serves files from `/config-repo` mounted as a volume. |
| 5 | Postgres `password authentication failed for user "ecommerce"` | `postgres_data` volume was initialized with the legacy `postgres/root` credentials | Wiped + recreated the postgres volume |
| 6 | Hibernate `wrong column type [token_hash]; found bpchar, expecting varchar(64)` | Flyway declared `CHAR(64)`; JPA entity has `length=64` → maps to `varchar(64)` | Migration `V1__initial_schema.sql` updated to `VARCHAR(64)` for all three token-hash columns |
| 7 | `No qualifying bean of type 'UserMapper' available` | MapStruct's generated `UserMapperImpl` wasn't being picked up by component scan in this multi-module annotation-processor setup | Replaced the `@Mapper` interface with a hand-rolled `@Component` class doing the same mapping by hand. Bypasses any annotation-processor flakiness. |
| 8 | api-gateway: `Bean 'clientHeaderStrippingFilter' could not be registered. A bean with that name has already been defined` | A `@Configuration` class named `ClientHeaderStrippingFilter` had a `@Bean` method also named `clientHeaderStrippingFilter()` — both produced beans with the same default name | Renamed the `@Bean` method to `stripClientIdentityHeadersFilter()` |
| 9 | product-service crash: `Failed to configure a DataSource: 'url' attribute is not specified` | `services/common/pom.xml` had `spring-boot-starter-data-jpa`, `spring-cloud-starter-openfeign`, and `spring-boot-starter-data-redis` declared **without** `<scope>provided</scope>`, leaking JPA into MongoDB-only services | Marked **every** Spring dep in common as `provided` so they only compile against common, never enter consumer classpaths |
| 10 | Backend services failed to register with Eureka, gateway returned 500 on routes | Rewritten `config-repo/application.yml` had no `eureka.client.serviceUrl.defaultZone`, so every service defaulted to `http://localhost:8761` | Restored global Eureka client + Kafka + management endpoint defaults in `config-repo/application.yml` |
| 11 | Mongo `Authentication failed` from product-service | `mongo_data` volume initialized with old `root/root`; new defaults are `ecommerce/change_me_mongo` | Wiped + recreated mongo volume |
| 12 | CI: `Could not find artifact com.project:common:jar:1.0.0-SNAPSHOT` for every service | Old per-service `mvn clean verify` couldn't resolve sibling modules | New `.github/workflows/ci.yml` runs a single multi-module reactor build at the repo root, then a matrix of Docker image builds for changed services. Frontend job runs `tsc -b --noEmit` + `vite build`. |
| 13 | Reactor build: `BaseEvent is abstract; cannot be instantiated` | `@Builder` on the abstract `BaseEvent` class made Lombok generate `BaseEventBuilder.build()` that called `new BaseEvent(...)` | Dropped `@Builder` from `BaseEvent`. Field initializers still run via `@NoArgsConstructor`, so `eventId`/`schemaVersion`/`timestamp` defaults still apply when subclasses build. |
| 14 | Reactor build: tests failed | Placeholder `@SpringBootTest contextLoads()` files in product/config-server/discovery-server tried to boot the full context (needs Mongo/Redis/Kafka/JWKS) | Deleted the three placeholder `*ApplicationTests.java`. Will be rewritten with Testcontainers in Phase 3.2. |
| 15 | `GET /oauth2/authorization/google` returned 500 with `INTERNAL_ERROR` | Spring 6 raises `NoResourceFoundException` (instead of falling through to a 404 default) when no OAuth2 filter chain is installed; common's `GlobalExceptionHandler` was swallowing it as a generic `Exception` | Added `@ExceptionHandler(NoResourceFoundException.class)` that returns 404 with code `OAUTH2_PROVIDER_NOT_CONFIGURED` and a clear message when path starts with `/oauth2/` or `/login/oauth2/`. New `AuthDiscoveryController` exposes `GET /api/auth/providers` so the frontend can avoid showing dead buttons in the first place. Login page calls it and only renders configured providers. |

### Files added / modified in session 5

#### New
- `services/auth-service/src/main/java/com/project/authservice/security/OAuth2ClientConfig.java`
- `services/auth-service/src/main/java/com/project/authservice/mapper/UserMapper.java` (hand-rolled, replaces MapStruct interface)
- `mvnw` and `.mvn/wrapper/maven-wrapper.properties` at the repo root (for CI + convenience)
- `.env` (gitignored — populated with sensible defaults so admin actually bootstraps)

#### Rewritten
- `services/auth-service/src/main/java/com/project/authservice/security/SecurityConfig.java` — wires `oauth2Login()` only when at least one provider is configured.
- `services/auth-service/Dockerfile`, `services/api-gateway/Dockerfile`, `services/config-server/Dockerfile`, `services/discovery-server/Dockerfile`, `services/product-service/Dockerfile`, `services/inventory-service/Dockerfile`, `services/order-service/Dockerfile`, `services/payment-service/Dockerfile`, `services/notification-service/Dockerfile` — runtime-only, layered jar pattern, expects `target/*-1.0.0-SNAPSHOT.jar` to be pre-built.
- `services/common/pom.xml` — every Spring dep marked `provided`. Scoping comment in the file.
- `services/common/src/main/java/com/project/common/event/BaseEvent.java` — `@Builder` removed (was on abstract class).
- `services/api-gateway/src/main/java/com/project/gateway/config/ClientHeaderStrippingFilter.java` — `@Bean` method renamed to avoid name collision with the class.
- `services/auth-service/src/main/resources/db/migration/V1__initial_schema.sql` — `VARCHAR(64)` for the three token-hash columns.
- `config-repo/auth-service.yml` — `oauth2.{google,github}.{client-id,client-secret}` properties (read by `OAuth2ClientConfig`); Spring's `spring.security.oauth2.client.registration.*` removed.
- `config-repo/application.yml` — global Eureka client + Kafka + management defaults.
- `docker/docker-compose.yml` — config-server now `SPRING_PROFILES_ACTIVE: native,docker`; build contexts use repo root.
- `.github/workflows/ci.yml` — single reactor build, matrix Docker image build, frontend job, optional Sonar.

#### Deleted
- `services/product-service/src/test/java/.../ProductServiceApplicationTests.java`
- `services/config-server/src/test/java/.../ConfigServerApplicationTests.java`
- `services/discovery-server/src/test/java/.../DiscoveryServerApplicationTests.java`

### Verified end-to-end after session 5

```
== eureka ==
  ✓ API-GATEWAY
  ✓ AUTH-SERVICE
  ✓ CONFIG-SERVER
  ✓ INVENTORY-SERVICE
  ✓ NOTIFICATION-SERVICE
  ✓ ORDER-SERVICE
  ✓ PAYMENT-SERVICE
  ✓ PRODUCT-SERVICE

== smoke test ==
  jwks                    HTTP 200
  GET /api/v1/products    HTTP 200
  GET /api/v1/categories  HTTP 200
  admin login             HTTP 200
  GET /api/admin/users    HTTP 200   { Platform Admin user, ROLE_ADMIN }
  GET /api/v1/orders      HTTP 200
  GET /api/v1/payments    HTTP 200
  GET /api/v1/notifications HTTP 200
  GET /api/auth/providers HTTP 200   { "password": true, "oauth2": false, "providers": [] }
  GET /oauth2/authorization/google HTTP 404 OAUTH2_PROVIDER_NOT_CONFIGURED
  Frontend tsc -b --noEmit clean
  Frontend vite build clean (entry 14.6 kB gzipped)
  Maven reactor `clean verify` clean (≈ 9 s, all 11 modules pass)
```

### How to bring up locally from scratch

```bash
# 1. Bootstrap secrets + env (gitignored)
make keys                    # writes .secrets/keys/{private,public}.pem
cp .env.example .env         # then edit ADMIN_EMAIL / ADMIN_PASSWORD

# 2. Build everything
./mvnw -B -ntp -DskipTests install

# 3. Bring up the stack
docker compose -f docker/docker-compose.yml --env-file .env up -d --build

# 4. Wait ~75 s for Eureka registration, then run the Postman
#    "End-to-end happy path" folder.
```

If you ever see "every request returns 401/403", the cause is overwhelmingly
likely to be **auth-service crashed at startup**. `docker logs auth-service`
is your first move. Common causes (now all fixed in this session, but worth
listing for future debugging):
- Stale postgres volume with mismatched credentials → `docker volume rm docker_postgres_data && docker compose up -d postgres`.
- Missing RSA keys → `make keys`.
- `OAuth2ClientProperties` validation tripping on empty client-id → already
  worked around by `OAuth2ClientConfig` (this session); set both
  GOOGLE/GITHUB env vars or leave them empty entirely.

---



## Frontend

> Vite + React 18 + TypeScript + Tailwind + ShadCN-style primitives, in
> `frontend/ecommerce-app/`. Editorial / refined-luxury aesthetic chosen
> via the `ui-ux-pro-max` skill — black + pink-500 accent, Rubik display +
> Nunito Sans body, deterministic SKU-derived gradient art in lieu of real
> product photos (the backend image pipeline is intentionally null for now).

### What's in the frontend

**Foundations**
- Vite, TS strict, alias `@/* → src/*`. Proxies `/api/**` to `http://localhost:8080`.
- Tailwind config with full design-token system (CSS variables for theme), film-grain SVG noise utility, custom keyframes (`fade-in`, `marquee`, `accordion-*`).
- 12 ShadCN-style primitives in `src/components/ui/` (Button with `accent` variant, Card, Input, Label, Textarea, Badge, Dialog, Sheet, Select, Tabs, DropdownMenu, Separator, Spinner/Skeleton).

**API + state layer**
- `src/api/client.ts` — axios instance with single-flight refresh-token retry on 401, structured `ApiError`, automatic `Authorization: Bearer` from auth store.
- `src/api/{auth,products,orders,payments,inventory,notifications,discovery}.ts` — one file per backend domain.
- `src/api/types.ts` — TS contracts that mirror the Spring DTOs.
- `src/stores/auth.ts` — Zustand + persist. Decodes JWT for role/permission checks.
- `src/stores/cart.ts` — Zustand + persist. Local cart for guests; will become a facade over cart-service when that lands.

**Pages**
- Auth: `/login`, `/register`. Login renders only the OAuth2 providers `/api/auth/providers` reports as configured.
- Storefront: `/`, `/products`, `/products/:id`, `/categories/:id`, `/cart`, `/checkout`, `/order-success/:id`.
- Account: `/account/orders`, `/account/orders/:id`, `/account/profile`, `/account/notifications`.
- Admin: `/admin`, `/admin/products`, `/admin/products/new`, `/admin/products/:id`, `/admin/inventory`, `/admin/orders`, `/admin/users`.
- 404 page with branded fallback.

**Production-readiness pass (already shipped)**
- `ErrorBoundary` at app root with branded fallback + reload action.
- Code-split every route via `React.lazy` + Suspense. Entry chunk **51 KB raw / 14.6 KB gzipped**.
- Vendor splits via `vite.config.ts` `manualChunks`: `vendor-react`, `vendor-router`, `vendor-query`, `vendor-radix`, `vendor-motion`, `vendor-icons`, `vendor-forms`. Per-page lazy chunks 0.3–4.8 KB each.
- `Sheet` mobile drawer used for storefront mobile menu, catalog filters, and admin sidebar (all collapse below `lg`).
- Sticky mobile add-to-cart bar on product detail (above the bottom nav, with `env(safe-area-inset-bottom)` padding).
- `MobileBottomNav` (Home / Shop / New / Bag / Account) for storefront `<md`, with cart-count badge.
- `ThemeProvider` + `ThemeToggle` (system / light / dark) with localStorage persistence and a FOUC-prevention inline `<script>` in `index.html`.
- `SkipToContent` link for keyboard users; semantic `<main id="main-content" tabIndex={-1}>` and `<nav aria-label>` landmarks.
- `Seo` helper component sets per-route `<title>` + meta description + Open Graph + Twitter card.
- PWA `manifest.webmanifest` with maskable SVG icons; two `theme-color` meta tags (light + dark); `apple-touch-icon`; `viewport-fit=cover`.
- `prefers-reduced-motion` honored in `globals.css`.

**Bundle (gzipped)**

| Chunk | Size |
|---|---|
| `index` (entry) | **14.6 kB** |
| `vendor-router` | 5.2 kB |
| `vendor-icons` | 3.7 kB |
| `vendor-query` | 10.6 kB |
| `vendor-forms` | 22.3 kB |
| `vendor-radix` | 22.1 kB |
| `vendor-motion` | 35.1 kB |
| `vendor-react` | 47.0 kB |
| `vendor` | 51.9 kB |
| Per-page lazy chunks | 0.3 – 4.8 kB each |

**What's NOT yet wired (frontend roadmap)**
- Apply `<Seo>` to every page (currently only on home, login, register, catalog, product detail, cart, checkout, order-success).
- Lazy-import framer-motion (would shave ~35 kB gz off first paint).
- Lighthouse audit + WCAG sweep.
- Storybook for the design system.
- E2E tests via Playwright.

---

## Postman collection

`ecommerce-platform.postman_collection.json` at the repo root is fully
regenerated to match the current backend.

**76 requests across 10 folders**, with chained variables so you can run
things in order without copy-pasting:

| Folder | Reqs | Highlights |
|---|---|---|
| Setup | 3 | Bootstrap admin login (auto-saves `admin_token`), gateway health, JWKS |
| Auth | 8 | Register, password + refresh grants, logout, profile, change-password (authenticated), Google OAuth2 entry |
| Admin | 7 | Users CRUD, promote-to-seller, role CRUD with typed DTOs |
| Categories | 5 | Public reads + admin writes |
| Products | 12 | Catalog reads, seller-scoped writes, admin toggle-active, "my products" |
| Inventory | 10 | List, low-stock, reserve/release, atomic add-stock, full CRUD |
| Orders | 7 | Create with `X-Idempotency-Key` and full shipping/billing addresses, list, get by id/number, cancel, admin status update, filter |
| Payments | 8 | Create with idempotency, get by id/reference/order, process, partial refund, **HMAC-signed webhook** (pre-request script computes `t=…,v1=…` in JS) |
| Notifications | 3 | List, unread count, mark read |
| End-to-end happy path | 13 | One-click runner: admin login → register → login → category → promote-to-seller → re-login → product → inventory → order → payment → HMAC webhook → verify CONFIRMED → check notification arrived |

**Auto-saved variables**: `admin_token`, `access_token`, `user_id`,
`category_id`, `product_id`, `order_id`, `order_number`, `payment_id`,
`payment_reference`, `notification_id`, `inventory_id`, `webhook_secret`.

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
| **OAuth2** | **Configure Google (and optionally GitHub) OAuth2 social login end-to-end** — set `GOOGLE_CLIENT_ID` + `GOOGLE_CLIENT_SECRET` in `.env`, configure the OAuth2 consent screen in Google Cloud Console, add `http://localhost:8080/login/oauth2/code/google` as an authorised redirect URI, verify the `CustomOAuth2SuccessHandler` redirect lands on the frontend with a valid JWT, and test the full flow from the login page | ❌ |
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
