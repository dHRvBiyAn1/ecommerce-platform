DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM coupons
        GROUP BY UPPER(BTRIM(code))
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate normalized coupon codes prevent V3 migration';
    END IF;
END $$;

UPDATE coupons
SET code = UPPER(BTRIM(code));

UPDATE coupon_redemptions AS r
SET coupon_code = c.code
FROM coupons AS c
WHERE r.coupon_id = c.id;

CREATE UNIQUE INDEX uq_coupons_normalized_code
    ON coupons (UPPER(BTRIM(code)));

CREATE INDEX idx_coupon_redemptions_coupon_code
    ON coupon_redemptions (coupon_code);

CREATE INDEX idx_coupon_redemptions_coupon_status
    ON coupon_redemptions (coupon_id, status);
