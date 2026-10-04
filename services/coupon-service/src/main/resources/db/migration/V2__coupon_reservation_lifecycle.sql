ALTER TABLE coupons
    ADD COLUMN IF NOT EXISTS reserved_count INT NOT NULL DEFAULT 0;

ALTER TABLE coupon_redemptions
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'COMMITTED',
    ADD COLUMN IF NOT EXISTS reserved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS committed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS released_at TIMESTAMP;

UPDATE coupon_redemptions
SET committed_at = redeemed_at
WHERE status = 'COMMITTED' AND committed_at IS NULL;

ALTER TABLE coupon_redemptions
    ADD CONSTRAINT chk_coupon_redemption_status
        CHECK (status IN ('RESERVED', 'COMMITTED', 'RELEASED'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_coupon_redemptions_order
    ON coupon_redemptions (order_id)
    WHERE order_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_coupon_redemptions_active
    ON coupon_redemptions (coupon_id, user_id, status)
    WHERE status IN ('RESERVED', 'COMMITTED');
