-- ============================================================
-- Migration V4: OCR Stability, Multi-Layer Deduplication & Timestamps
-- ============================================================

-- 1. Enhance transactions table with precise timestamps and deduplication attributes
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS tx_timestamp TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS image_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS invoice_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS duplicate_warning VARCHAR(255);

-- Backfill tx_timestamp from tx_date for existing rows
UPDATE transactions SET tx_timestamp = tx_date::TIMESTAMP WITH TIME ZONE WHERE tx_timestamp IS NULL;

-- Indexes for deduplication lookups
CREATE INDEX IF NOT EXISTS idx_transactions_image_sha256 ON transactions(image_sha256);
CREATE INDEX IF NOT EXISTS idx_transactions_invoice_num  ON transactions(invoice_number);
CREATE INDEX IF NOT EXISTS idx_transactions_dedup_triplet ON transactions(merchant, amount, tx_date);

-- 2. Create OCR Audits table to track quality, stability, and edit rates
CREATE TABLE IF NOT EXISTS ocr_audits (
    id                 UUID                     NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    transaction_id     UUID                     REFERENCES transactions(id) ON DELETE SET NULL,
    image_sha256       VARCHAR(64),
    invoice_number     VARCHAR(100),
    status             VARCHAR(30)              NOT NULL DEFAULT 'PENDING', -- PENDING, CONFIRMED_AS_IS, CONFIRMED_MODIFIED, DISCARDED
    initial_amount     BIGINT,
    final_amount       BIGINT,
    initial_merchant   VARCHAR(255),
    final_merchant     VARCHAR(255),
    initial_category   VARCHAR(50),
    final_category     VARCHAR(50),
    initial_spend_type VARCHAR(15),
    final_spend_type   VARCHAR(15),
    edit_count         INTEGER                  NOT NULL DEFAULT 0,
    confidence_score   DOUBLE PRECISION,
    user_notes         TEXT,
    document_type      VARCHAR(30),
    source_platform    VARCHAR(50),
    raw_ocr_json       JSONB,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    resolved_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_ocr_audits_status  ON ocr_audits(status);
CREATE INDEX IF NOT EXISTS idx_ocr_audits_created ON ocr_audits(created_at);
CREATE INDEX IF NOT EXISTS idx_ocr_audits_tx      ON ocr_audits(transaction_id);
