-- V7: Make transactions.spend_type nullable for balance-sheet transfers (e.g. DEBT_PRINCIPAL_PAYMENT, INTERNAL_TRANSFER)
ALTER TABLE transactions ALTER COLUMN spend_type DROP NOT NULL;
