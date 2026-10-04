# Authentication Service

Auth-service is the platform's Spring Boot 3.3.5 / Java 17 identity provider.
It stores users, roles, permissions, and hashed refresh-token records in
PostgreSQL, issues RS256 JWTs, and publishes public signing keys at
`/.well-known/jwks.json`. The Maven version and Java release are managed by the
root POM.

## Run and verify

Configuration is supplied by the root Compose setup and Spring Cloud Config;
bootstrap admin creation requires configured `ADMIN_EMAIL` and `ADMIN_PASSWORD`.
Do not use sample credentials or commit secrets. From the repository root:

```bash
make env
make keys
make up
./mvnw -pl services/auth-service -am verify
```

The root wrapper supports Windows with `mvnw.cmd`. The service itself uses
PostgreSQL, Redis, Kafka, and the configured RSA keys. See the root README for
ports, stack operations, and release-evidence checks.

## Implemented identity contracts

- Registration through `POST /api/auth/register` always assigns `ROLE_CUSTOMER`.
  Seller access requires the seller-application flow or an authorized admin
  role assignment.
- Password and refresh grants use `POST /api/auth/token` with form fields.
  User access tokens live for 15 minutes; refresh tokens live for 7 days by
  default, are stored as SHA-256 hashes, rotate by family, and are sent only in
  an HttpOnly `refresh_token` cookie (`SameSite=Strict`, Secure according to
  `SECURE_COOKIES`).
- User JWTs contain a UUID `sub`, email, `ROLE_*` roles, and fine-grained
  permissions. RS256 signing includes a `kid`; the JWKS endpoint can publish
  current and previous public keys for rotation. Configure key locations using
  `AUTH_RSA_PRIVATE_KEY_LOCATION`, `AUTH_RSA_PUBLIC_KEY_LOCATION`, and related
  key-rotation variables. An ephemeral development key invalidates tokens at
  restart and is not a persistent deployment key.
- `POST /api/auth/token` with `grant_type=client_credentials` returns raw OAuth
  JSON, without the ordinary API envelope or refresh cookie. The three
  allowlisted callers, scopes, TTL limits, and fail-closed rules are in the
  [authentication contract](../../docs/authentication.md).
- Profiles use `GET` and `PUT /api/user/profile`; password changes require an
  authenticated user. Normal REST responses use the shared API envelope.
- Google and GitHub sign-in are enabled only when both credentials for that
  provider are configured. `GET /api/auth/providers` advertises the configured
  options.

See [Authentication contracts and decisions](../../docs/authentication.md) for
the complete token, key, cookie, scope, compatibility, and service-client
contract. It is the canonical maintained reference; this file summarizes only
service setup and endpoints.
