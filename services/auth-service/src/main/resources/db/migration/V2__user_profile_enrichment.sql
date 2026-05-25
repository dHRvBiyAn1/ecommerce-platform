-- ============================================================================
-- V2: User profile enrichment — phone, profile image, embedded shipping +
-- billing addresses. Keeps everything on the users row (1 user → 1 shipping
-- + 1 billing) to keep the API and JOINs simple. Multi-address book is a
-- future migration if/when needed.
-- ============================================================================

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone                  VARCHAR(20),
    ADD COLUMN IF NOT EXISTS shipping_full_name     VARCHAR(120),
    ADD COLUMN IF NOT EXISTS shipping_phone         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS shipping_street        VARCHAR(200),
    ADD COLUMN IF NOT EXISTS shipping_city          VARCHAR(80),
    ADD COLUMN IF NOT EXISTS shipping_state         VARCHAR(80),
    ADD COLUMN IF NOT EXISTS shipping_zip_code      VARCHAR(20),
    ADD COLUMN IF NOT EXISTS shipping_country       VARCHAR(80),
    ADD COLUMN IF NOT EXISTS billing_full_name      VARCHAR(120),
    ADD COLUMN IF NOT EXISTS billing_phone          VARCHAR(20),
    ADD COLUMN IF NOT EXISTS billing_street         VARCHAR(200),
    ADD COLUMN IF NOT EXISTS billing_city           VARCHAR(80),
    ADD COLUMN IF NOT EXISTS billing_state          VARCHAR(80),
    ADD COLUMN IF NOT EXISTS billing_zip_code       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS billing_country        VARCHAR(80);

-- image_url is already nullable in V1; nothing to add. Profile image
-- support is a URL (data URL, S3, etc.) — frontend renders it directly.
