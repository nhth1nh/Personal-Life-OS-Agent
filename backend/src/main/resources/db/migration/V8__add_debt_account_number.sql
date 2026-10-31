-- ============================================================
-- PLOA Database Schema - V8 Add Debt Account / Contract Number
-- ============================================================

ALTER TABLE debts ADD COLUMN IF NOT EXISTS account_number VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_debts_account_number ON debts(account_number);
