-- ============================================================================
-- V4: Add extensible JSONB metadata to users and seller_applications tables.
-- Supports dynamic properties, configurations, and extensible profiles.
-- Includes high-performance GIN indexes for fast nested attribute querying.
-- ============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
CREATE INDEX IF NOT EXISTS idx_users_metadata ON users USING gin (metadata);

ALTER TABLE seller_applications ADD COLUMN IF NOT EXISTS business_metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
CREATE INDEX IF NOT EXISTS idx_seller_apps_biz_metadata ON seller_applications USING gin (business_metadata);
