# Scoped Service Authentication Specification

Status: already approved in conversation; this records the approved production-hardening slice, not a new approval request.

## Scope

Replace user-token forwarding for required internal callers with scoped OAuth-style `client_credentials` authentication. Preserve the ongoing uncommitted implementation and ordinary user authorization. Work only in `/Users/dhruv/Developer/ecommerce-platform/.worktrees/release-hardening-impl` on `release-hardening-impl`.

## Token Contract

- Issue short-lived RS256 JWTs with `sub` equal to the client ID, `token_type=service`, space-delimited `scope`, unique `jti`, and bounded validity claims.
- Authenticate only configured allowlisted clients using constant-time secret comparison; reject unknown clients, invalid credentials, unsupported grants, and scopes outside the client's allowlist.
- Return an OAuth JSON machine response with `access_token`, `token_type=Bearer`, `expires_in`, and granted `scope`. Do not use the ordinary user response envelope, issue refresh tokens, or set refresh cookies.
- Use placeholders only in checked-in configuration and documentation. Inject client-specific secrets through the environment; never log credentials or tokens.

| Client | Allowed Scopes |
| --- | --- |
| order-service | `inventory.write coupons.read coupons.write` |
| payment-service | `orders.read` |
| cart-service | `coupons.read` |

## Authorization Contract

| Operation | Machine Authorization | Ordinary User Behavior |
| --- | --- | --- |
| Inventory reserve, commit, release | Service token AND `inventory.write` | Denied; machine-only operations |
| Coupon validate | Service token AND `coupons.read` | Preserve existing permission and owner/admin checks where applicable |
| Coupon reserve, commit, release, redeem | Service token AND `coupons.write` | Preserve existing permission and owner/admin checks |
| Order detail GET by ID or number | Service token AND `orders.read` | Require existing permission AND owner/admin validation |
| Public product reads | Unchanged | Preserve current public access |

A scope alone never grants an ownership bypass to a nonservice token. A service token with missing or incorrect scope must not fall through to an ordinary-user bypass. Continue using verified JWT security context, not caller-supplied identity headers.

## Internal Caller Contract

- Order, payment, and cart must activate service authentication fail-closed with nonblank endpoint, client ID, secret, and required scope configuration. Missing, blank, or disabled required-client configuration must not silently enable user-token forwarding.
- Internal calls use the service token even when a user security context exists. Exchange or configuration failure must fail the call, not fall back to a user token.
- Cache valid tokens with expiry awareness and refresh before expiry. Never cache invalid responses or reuse expired tokens.
- Bound token-exchange HTTP connection/read waits. Reject malformed responses, including missing/blank access token, unsupported token type, invalid expiry, or insufficient granted scope.

## Acceptance Evidence

- Observe behavioral tests failing for the intended missing behavior before each corresponding implementation edit; compilation errors alone are not the red phase.
- Exercise controller method security through actual Spring proxies, covering correct/wrong/missing scope, nonservice tokens carrying scope, anonymous callers, and ordinary owner/admin/nonowner cases.
- Cover common token cache, bounded exchange, malformed responses, activation/application context, and no-fallback behavior; cover auth issuance, claims/signature, credential and scope rejection, JSON shape, and absence of refresh cookies.
- Parent runs the final shared reactor command in `plan.md` and static Compose configuration validation without stopping or restarting the stack.
- Independent review and passing verification precede a focused local security commit controlled by the parent. No push.

## Architectural Boundaries

Follow `GEMINI.md` and `.specify/memory/constitution.md`: controller-to-service-to-adapter flow, immutable DTOs, service transaction boundaries, and JUnit 5/Mockito/AssertJ tests. No schema, DDL, index, database mapping, or distributed cache migration is needed. Target at least 80% new-logic coverage while respecting the directive's greater-than-80% service coverage requirement.
