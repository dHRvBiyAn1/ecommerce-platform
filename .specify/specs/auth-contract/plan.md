# Auth Response / Exception Contract Plan

Status: already approved; this is execution documentation, not a new design proposal.

## Goal

Remove auth-local response and exception handling in favor of shared common contracts, while preserving user-facing auth behavior and machine OAuth wire behavior.

## Execution Order

1. Read current auth-service and common response/exception classes and verify the actual `com.project.common` scan wiring in auth-service.
2. Add HTTP behavioral tests first in auth-service for:
   - common success envelope shape;
   - common error envelope shape with `traceId` and `timestamp`;
   - generic 500 sanitization;
   - client-credentials raw OAuth JSON with no refresh cookie.
3. Run the focused Maven target and capture the expected red phase before implementation.
4. Replace auth-local DTO and handler usage with shared common DTOs and handler.
5. Convert auth-local exceptions to the common `BusinessException` hierarchy with existing `ErrorCode` values.
6. Rerun the focused Maven target to green and record the evidence paths in `progress.md`.

## Constraints

- No DTO package or record refactor beyond the auth-local removal required here.
- No business validation refactor.
- No common handler changes.
- No config, Compose, or other-service edits.
- No stack operations, push, or commit.

## Verification

- Use only focused auth-service Maven test runs for red/green evidence.
- Preserve ordinary response `status` and `data` fields and preserve machine OAuth raw JSON.
- Record exact test command, result, and log file paths in `progress.md`.
