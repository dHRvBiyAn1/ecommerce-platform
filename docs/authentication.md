# Authentication and service identity

This is the canonical maintained reference for auth-service's implemented
contracts and the accepted scoped service-auth decisions. Implementation sources
include [auth-service configuration](../config-repo/auth-service.yml),
[AuthController](../services/auth-service/src/main/java/com/project/authservice/controller/AuthController.java),
[JwtService](../services/auth-service/src/main/java/com/project/authservice/service/JwtService.java),
and [ClientCredentialsService](../services/auth-service/src/main/java/com/project/authservice/service/ClientCredentialsService.java).

## User identity

- `POST /api/auth/register` creates a `ROLE_CUSTOMER`; it never accepts a
  caller-selected seller role. Seller access is granted through seller
  onboarding or an authorized administrator.
- `POST /api/auth/token` accepts form-encoded `grant_type=password` with email
  and password, or `grant_type=refresh_token` with the `refresh_token` cookie.
  It returns the access token in the ordinary API response envelope and sets
  the refresh cookie (`HttpOnly`, `SameSite=Strict`, Secure according to
  `SECURE_COOKIES`). Current defaults are a 15-minute access token and a 7-day
  refresh token.
- Refresh tokens are random opaque values; only SHA-256 hashes are stored.
  Rotation reuses a family ID and detection of a revoked token revokes that
  family. Logout blacklists a supplied access token and revokes the refresh
  token.
- User JWTs are RS256 signed. Claims include UUID `sub`, `email`, `roles` already
  prefixed with `ROLE_`, and fine-grained `permissions`; the header contains
  `kid`. `GET /.well-known/jwks.json` publishes current and configured previous
  public keys. Keys are loaded from configurable resource URIs. The generated
  ephemeral development key is lost on restart; deployments must configure
  persistent private/public key locations and protect the private key.
- `GET /api/user/profile` and `PUT /api/user/profile` operate on the authenticated
  user. `POST /api/auth/change-password` also requires authentication.
  `GET /api/auth/providers` reports password and configured OAuth2 providers;
  Google/GitHub login is enabled only when both provider credentials are set.
- Ordinary endpoints use the shared response envelope (`status`, `message`,
  `data`, `traceId`, `timestamp`) and shared error shape. Generic server errors
  are sanitized. Client-credentials success and OAuth errors retain raw OAuth
  JSON compatibility.

## Scoped machine identity

`POST /api/auth/token` accepts form-encoded
`grant_type=client_credentials`, `client_id`, `client_secret`, and optional
space-delimited `scope`. It returns raw JSON with `access_token`,
`token_type=Bearer`, `expires_in`, and granted `scope`; it sets no refresh cookie
and returns no user envelope. Checked-in configuration contains placeholders
only. Inject separate secrets through each caller's environment and the
auth-service allowlist; never share, log, or commit them.

| Client | Allowed scopes | Configured callers |
| --- | --- | --- |
| `order-service` | `inventory.write`, `coupons.read`, `coupons.write` | Inventory reservation/commit/release and coupon capacity workflows |
| `payment-service` | `orders.read` | Order lookup required by payment processing |
| `cart-service` | `coupons.read` | Coupon validation |

The default service-token TTL is 5 minutes; configured TTLs are accepted from 1
second through 15 minutes inclusive. Service JWTs are RS256 signed and carry
`sub=<client_id>`, `token_type=service`, a unique `jti`, issuer/validity claims,
and a normalized space-delimited `scope`. A missing requested scope grants that
client's full allowlist; requested scopes outside it are rejected. Unknown
clients, blank/unconfigured secrets, and invalid credentials are rejected, with
constant-time secret comparison.

### Authorization and caller requirements

- Machine-only inventory writes require both a service identity and
  `inventory.write`. Coupon machine operations require the matching read/write
  scope. Order detail lookup accepts `orders.read` for a service identity.
- A scope claim on a normal user token never makes it a service principal or
  bypasses user permissions and ownership checks. Product reads remain public as
  currently configured.
- Order, payment, and cart callers use their own machine token even when a user
  token is present. Missing/blank/disabled required-client configuration and
  token-exchange failures fail closed; callers do not fall back to forwarding a
  user's token.
- The shared token provider caches only validated, unexpired tokens and refreshes
  before expiry. Token exchange has bounded connection/read waits and rejects
  malformed responses, unsupported token types, invalid expiry, or insufficient
  granted scope.

## HTTP compatibility summary

| Flow | Wire contract |
| --- | --- |
| Register, profile, user token grants | Shared ordinary API envelope |
| Client-credentials success | Raw OAuth token JSON; no refresh cookie |
| OAuth grant/client/scope errors | Raw OAuth error JSON with HTTP status |
| Invalid user authentication / refresh token / duplicate account | HTTP 401 `UNAUTHENTICATED` / 403 `FORBIDDEN` / 409 `DUPLICATE_RESOURCE` |
| Shared application errors | `status`, `error`, `message`, `path`, `code`, `traceId`, `timestamp`; generic 500 message is sanitized |

The service summary and root operational setup are in the
[auth-service README](../services/auth-service/README.md) and
[repository README](../README.md). Historical planning files were retired after
their accepted decisions and implemented contracts were consolidated here.
