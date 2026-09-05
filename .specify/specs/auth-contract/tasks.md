# Auth Response / Exception Contract Tasks

These tasks are for the already approved slice. No new design approval is needed.

## 1. Behavior Tests First

- [x] Add HTTP behavioral tests in `services/auth-service` that fail before implementation.
- [x] Prove common success envelope fields on an auth endpoint using the shared `ApiResponse`.
- [x] Prove common error envelope fields, including `traceId` and `timestamp`, on an auth failure path.
- [x] Prove generic 500 sanitization through the shared `GlobalExceptionHandler`.
- [x] Prove client-credentials OAuth JSON remains raw and cookie-free.

## 2. Auth-Service Implementation

- [ ] Replace auth-local `dto.ApiResponse` and `dto.ErrorResponse` usage with shared common DTOs.
- [x] Remove auth-local `AuthGlobalExceptionHandler` in favor of the shared common handler.
- [x] Convert `AuthException`, `TokenRefreshException`, and `UserAlreadyExistsException` to the common `BusinessException` hierarchy with existing `ErrorCode` values.
- [x] Keep HTTP semantics aligned with 401, 403, and 409.

## 3. Verification and Evidence

- [ ] Run focused Maven tests for the auth-service slice and capture the red phase.
- [x] Rerun after implementation and capture the green phase.
- [ ] Record the exact command and log paths in `progress.md`.
