-- ============================================================================
-- Coupon-service base schema (Flyway).
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS coupons (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(64) NOT NULL UNIQUE,
    description         TEXT,
    discount_type       VARCHAR(16) NOT NULL,        -- PERCENT or FIXED
    discount_value      NUMERIC(10, 2) NOT NULL,
    /* For PERCENT, an optional cap on absolute discount (e.g. 20% off up to Rs.500). */
    max_discount_amount NUMERIC(10, 2),
    /* Minimum subtotal in cart currency to apply the coupon. */
    min_order_amount    NUMERIC(10, 2),
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    /* Window of validity (UTC). */
    valid_from          TIMESTAMP NOT NULL,
    valid_until         TIMESTAMP NOT NULL,
    /* Global usage cap; null = unlimited. */
    usage_limit         INT,
    usage_count         INT NOT NULL DEFAULT 0,
    /* Per-user cap; null = unlimited. */
    per_user_limit      INT,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    /* Optimistic locking. */
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_coupon_discount_type
        CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT chk_coupon_window
        CHECK (valid_until > valid_from),
    CONSTRAINT chk_coupon_percent_range
        CHECK (discount_type <> 'PERCENT' OR (discount_value > 0 AND discount_value <= 100))
);

CREATE INDEX IF NOT EXISTS idx_coupons_active ON coupons (active) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_coupons_window ON coupons (valid_from, valid_until);

/* Per-user redemption ledger: lets us enforce per-user-limit and audit refunds. */
CREATE TABLE IF NOT EXISTS coupon_redemptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coupon_id       UUID NOT NULL REFERENCES coupons (id) ON DELETE CASCADE,
    coupon_code     VARCHAR(64) NOT NULL,           -- denormalized for fast lookup
    user_id         UUID NOT NULL,
    order_id        VARCHAR(64),                    -- nullable until checkout completes
    discount_amount NUMERIC(10, 2) NOT NULL,
    redeemed_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (coupon_id, user_id, order_id)
);

CREATE INDEX IF NOT EXISTS idx_redemptions_coupon_user
    ON coupon_redemptions (coupon_id, user_id);
CREATE INDEX IF NOT EXISTS idx_redemptions_order
    ON coupon_redemptions (order_id) WHERE order_id IS NOT NULL;
