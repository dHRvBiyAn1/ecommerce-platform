# Auth Response / Exception Contract Progress

## Approval

- The production-hardening slice was already approved in conversation. This directory records that approval and the implementation evidence.

## Evidence Log

- Behavioral red phases exposed machine/user identity confusion, TTL limits, OAuth error classification, local advice mismatch, and missing nullable response data. Subagent reports record each failing run; initial test setup errors were corrected before green verification.
- Final parent verification: `./mvnw -B -ntp -pl services/common,services/auth-service,services/inventory-service,services/coupon-service,services/order-service,services/payment-service,services/cart-service -am test` passed on Java 21.0.8 with JaCoCo enabled: 212 tests, zero failures/errors/skips. Cart has no tests yet.
- Independent final static review approved the slice after shared advice, typed exceptions, and null-data compatibility fixes. The MVC contract suite imports common advice explicitly; full application-context integration coverage remains outstanding.
- No running Docker stack changes or pushes were performed.

## Notes

- Scope includes `services/auth-service`, this spec directory, and the shared response field serialization regression fix and test in `services/common`.
- Ruling: service token lifetimes are bounded to 1 second through 15 minutes, inclusive, preserving the five-minute default. Deployments configured above 15 minutes must lower their TTL.
- Common scan wiring is expected to come from `AuthServiceApplication` scanning `com.project.common`.
- No design approval is being requested in this slice.
