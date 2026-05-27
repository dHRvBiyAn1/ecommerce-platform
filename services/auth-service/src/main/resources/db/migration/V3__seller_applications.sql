-- ============================================================================
-- V3: Seller-application workflow.
--
-- A customer applies to sell; an admin approves or rejects. On approve, the
-- user gets ROLE_SELLER. One row per user (uniqueness enforced) so a user
-- can submit a fresh application after a rejection but never have two
-- pending at once.
-- ============================================================================

CREATE TABLE IF NOT EXISTS seller_applications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    business_name       VARCHAR(180) NOT NULL,
    gstin               VARCHAR(15),
    contact_phone       VARCHAR(20) NOT NULL,
    pickup_full_name    VARCHAR(120),
    pickup_phone        VARCHAR(20),
    pickup_street       VARCHAR(200) NOT NULL,
    pickup_city         VARCHAR(80)  NOT NULL,
    pickup_state        VARCHAR(80)  NOT NULL,
    pickup_zip_code     VARCHAR(20)  NOT NULL,
    pickup_country      VARCHAR(80)  NOT NULL DEFAULT 'IN',
    bank_account_last4  VARCHAR(4),
    notes               TEXT,
    rejection_reason    TEXT,
    submitted_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at         TIMESTAMP,
    reviewed_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_seller_application_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_seller_applications_status
    ON seller_applications (status, submitted_at DESC);
