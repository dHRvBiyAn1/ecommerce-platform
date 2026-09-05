# Service Authentication Tasks

These are allocations for the already approved slice, not claims of implementation completion. Each owner preserves pre-existing work in their files. Parent alone schedules shared-reactor tests and controls commits.

## 1. Controller Authorization

**Own:** `services/inventory-service/src/main/java/com/project/inventory/controller/InventoryController.java`, `services/coupon-service/src/main/java/com/project/coupon/controller/CouponController.java`, `services/order-service/src/main/java/com/project/order/controller/OrderController.java`, and their corresponding controller/security tests in those three modules.

**Consume:** Task 2's `ServiceScopes`, `CurrentUser.isService()`, scope authorities, and ordinary user helpers. **Produce:** Endpoint authorization behavior from the spec; no common-code or deployment edits.

- [ ] Add proxy-backed failing tests for inventory machine-only writes, coupon read/write scope separation, and both order-detail lookup routes.
- [ ] Include service tokens with wrong/missing scope and nonservice tokens carrying identical scopes; prove neither gains unauthorized ownership bypass.
- [ ] Include ordinary owner/admin acceptance, permission/nonowner denial, and unchanged public product reads.
- [ ] Parent coordinates red runs; record behavioral failures before minimal controller edits, then green runs.

## 2. Common Token Provider and Security

**Own:** `services/common/src/main/java/com/project/common/feign/` service-auth implementation and `FeignAuthForwardingConfig.java`; `services/common/src/main/java/com/project/common/constant/ServiceScopes.java`; `services/common/src/main/java/com/project/common/security/CurrentUser.java` and `JwtAuthenticationConverter.java`; corresponding common tests and test resources.

**Consume:** Task 3's OAuth wire contract and client-specific configuration. **Produce:** `ServiceTokenClient.requestToken(ServiceAuthProperties): ServiceTokenResponse`, `ServiceTokenProvider.getAccessToken(): String`, service identity and scope helpers, fail-closed activation, and internal Feign bearer injection.

- [ ] Add failing tests for cache reuse/expiry refresh, exchange failures, malformed token/type/expiry/scope responses, and bounded HTTP waits.
- [ ] Add application-context tests for required-client activation and missing/blank/disabled configuration; include existing user context and demonstrate no user-token fallback.
- [ ] Add scope conversion and service-identity tests proving a nonservice token cannot become a machine principal merely by carrying scope.
- [ ] Parent coordinates red runs; implement minimal fixes and rerun green. Coordinate any public interface/property changes with Tasks 1 and 3 before edits.

## 3. Issuance and Deployment Configuration

**Own:** Service-auth changes/tests under `services/auth-service/`; service-auth configuration under `config-repo/`; `docker-compose.yml`; environment example/template files; operational service-auth documentation outside this four-file spec directory. No unrelated auth or deployment refactors.

**Consume:** Task 2's required-client properties. **Produce:** OAuth machine response, RS256 claims, allowlisted client scope grants, and client-specific deployment injection for order/payment/cart.

- [ ] Add failing issuance tests for valid grants, invalid credentials/unknown clients, blank credentials, unsupported grant, excessive scope, expected claims/signature/expiry, OAuth JSON shape, and no refresh token/cookie.
- [ ] Parent coordinates red runs; implement allowlist and constant-time secret validation without credential disclosure.
- [ ] Wire order to `inventory.write coupons.read coupons.write`, payment to `orders.read`, and cart to `coupons.read`; enforce nonblank activation jointly with Task 2.
- [ ] Use only secret placeholders in checked-in files and document client-specific environment injection; inspect for unintended public-product security changes.
- [ ] Hand static Compose validation to parent; do not operate the running stack.

## 4. Independent Review and Integration

**Own:** Review report and integration evidence; parent-owned shared-reactor execution and focused local commit. Review is read-only against Tasks 1-3; return fixes to the owning task rather than edit across boundaries. Parent updates these spec records after the documentation handoff.

- [ ] Review the complete tracked and untracked slice, not only the latest diff; check scope AND service-type gates, ordinary ownership, activation, no fallback, cache expiry, HTTP bounds, response validation, and secret handling.
- [ ] Reconcile red/green evidence and ensure method-security tests exercise actual proxies; resolve findings with owners.
- [ ] Run the exact final reactor command in `plan.md` and record command/result; run static Compose configuration validation without stack stop/restart.
- [ ] After review and passing verification, parent stages only the intended security slice and creates the approved focused local commit. No push.

## Conflict Rules

- This documentation worker owns only `spec.md`, `plan.md`, `tasks.md`, and `progress.md` in this directory.
- Task 1 does not edit common helpers; Task 2 does not edit controllers/config-repo/Compose; Task 3 does not edit common code or downstream controllers.
- Existing order validation files and other handoff work outside these allocations are preserved; parent explicitly assigns any necessary change before editing them.
- Coordinate shared interfaces before changes. If another writer changes an owned target incompatibly, stop and ask the parent; never revert their work.
