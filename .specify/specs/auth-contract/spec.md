# Auth Response / Exception Contract

Status: already approved in conversation; this records the approved production-hardening slice. No new design approval is requested.

## Scope

Update auth-service to use shared common response and exception handling contracts only. Work only in `/Users/dhruv/Developer/ecommerce-platform/.worktrees/release-hardening-impl` on `release-hardening-impl`.

## Contract

- Replace auth-local `dto.ApiResponse`, `dto.ErrorResponse`, and `exception.AuthGlobalExceptionHandler` usage with shared `com.project.common.dto.ApiResponse` and `com.project.common.exception.GlobalExceptionHandler`.
- Keep normal REST envelopes for ordinary auth endpoints: `status`, `message`, `data`, `traceId`, and `timestamp` must remain present through the common envelope.
- Keep machine OAuth responses raw JSON. `POST /api/auth/token` with `grant_type=client_credentials` must return `ServiceTokenResponse` directly and must not emit the ordinary envelope or a refresh cookie.
- Preserve auth HTTP semantics through common business exceptions:
  - `AuthException` -> `401 UNAUTHENTICATED`
  - `TokenRefreshException` -> `403 FORBIDDEN` or the closest existing 403 business code
  - `UserAlreadyExistsException` -> `409 DUPLICATE_RESOURCE`
- Preserve the existing shared `GlobalExceptionHandler` error shape, including `status`, `error`, `message`, `path`, `code`, `traceId`, and `timestamp`.
- Keep generic server failures sanitized. The 500 body must not leak the thrown exception message.

## Wiring

- Auth-service already scans `com.project.common` in `AuthServiceApplication`; this slice must use that actual wiring, not a local handler copy.
- Do not change common handler implementation, other services, Compose, config-repo, or DTO/package-wide refactors outside auth-service.

## Acceptance Criteria

- Behavioral HTTP tests fail before implementation and pass after minimal auth-service changes.
- Success responses still expose the common envelope fields.
- Error responses use the common contract with trace id and timestamp.
- Generic 500 responses are sanitized.
- OAuth client-credentials JSON stays unchanged and cookie-free.
