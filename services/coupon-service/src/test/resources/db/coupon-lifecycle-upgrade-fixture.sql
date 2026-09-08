INSERT INTO coupons (code, discount_type, discount_value, valid_from, valid_until)
VALUES (' legacy ', 'PERCENT', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day');

INSERT INTO coupon_redemptions (coupon_id, coupon_code, user_id, order_id, discount_amount)
SELECT id, code, gen_random_uuid(), 'legacy-order', 10
FROM coupons
WHERE code = ' legacy ';
