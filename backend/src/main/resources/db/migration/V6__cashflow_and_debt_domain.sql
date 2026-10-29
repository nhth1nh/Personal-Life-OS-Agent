-- ============================================================
-- PLOA Database Schema - V6 Cash-Flow & Debt Domain Migration
-- ============================================================

-- 1. Financial Accounts (Checking, Savings, Credit Cards, Cash Wallets)
CREATE TABLE IF NOT EXISTS financial_accounts (
    id                 UUID                     NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name               VARCHAR(100)             NOT NULL,
    account_type       VARCHAR(30)              NOT NULL, -- CHECKING, SAVINGS, CREDIT_CARD, CASH_WALLET
    currency           VARCHAR(3)               NOT NULL DEFAULT 'VND',
    current_balance    NUMERIC(19, 2)           NOT NULL DEFAULT 0,
    last_reconciled_at TIMESTAMP WITH TIME ZONE,
    is_active          BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE financial_accounts IS 'Tracked financial accounts and liquid balances';

-- Seed default primary cash account for legacy and unallocated transactions
INSERT INTO financial_accounts (id, name, account_type, currency, current_balance, is_active)
VALUES ('00000000-0000-0000-0000-000000000001', 'Primary Cash Ledger', 'CHECKING', 'VND', 0, TRUE)
ON CONFLICT (id) DO NOTHING;

-- 2. Debts & Liabilities (Mortgages, Auto Loans, Installments/BNPL, Revolving Cards)
CREATE TABLE IF NOT EXISTS debts (
    id                           UUID                     NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name                         VARCHAR(255)             NOT NULL,
    creditor                     VARCHAR(255)             NOT NULL,
    debt_type                    VARCHAR(30)              NOT NULL, -- AMORTIZING_LOAN, REVOLVING_CREDIT, INSTALLMENT_BNPL, PEER_DEBT
    currency                     VARCHAR(3)               NOT NULL DEFAULT 'VND',
    original_principal           NUMERIC(19, 2)           NOT NULL,
    current_principal            NUMERIC(19, 2)           NOT NULL,
    interest_rate_apr            NUMERIC(7, 4)            NOT NULL DEFAULT 0, -- e.g. 10.5000%
    interest_accrual_convention  VARCHAR(30)              NOT NULL DEFAULT 'ACTUAL_365', -- ACTUAL_365, THIRTY_360, MONTHLY_FLAT
    monthly_minimum_due          NUMERIC(19, 2)           NOT NULL DEFAULT 0,
    due_day_of_month             INTEGER                  NOT NULL DEFAULT 1,
    origination_date             DATE                     NOT NULL,
    maturity_date                DATE,
    status                       VARCHAR(20)              NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PAID_OFF, DEFAULTED, SETTLED
    notes                        TEXT,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE debts IS 'Tracked liabilities, contracts, loans, and revolving credit obligations';

CREATE INDEX IF NOT EXISTS idx_debts_status   ON debts(status);
CREATE INDEX IF NOT EXISTS idx_debts_creditor ON debts(creditor);

-- 3. Debt Contractual Schedules (Predicted Installment Milestones)
CREATE TABLE IF NOT EXISTS debt_schedules (
    id                       UUID                     NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    debt_id                  UUID                     NOT NULL REFERENCES debts(id) ON DELETE CASCADE,
    installment_number       INTEGER                  NOT NULL,
    due_date                 DATE                     NOT NULL,
    expected_payment         NUMERIC(19, 2)           NOT NULL,
    expected_principal       NUMERIC(19, 2)           NOT NULL,
    expected_interest        NUMERIC(19, 2)           NOT NULL,
    remaining_principal_after NUMERIC(19, 2)          NOT NULL,
    status                   VARCHAR(20)              NOT NULL DEFAULT 'UPCOMING', -- UPCOMING, PAID, OVERDUE
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_debt_schedules_debt_due ON debt_schedules(debt_id, due_date);
CREATE INDEX IF NOT EXISTS idx_debt_schedules_status   ON debt_schedules(status);

-- 4. Incomes & Future Cash Inflows (Salary, Freelance, Bonuses, Dividends)
CREATE TABLE IF NOT EXISTS incomes (
    id                UUID                     NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    source            VARCHAR(255)             NOT NULL,
    expected_amount   NUMERIC(19, 2)           NOT NULL,
    actual_amount     NUMERIC(19, 2),
    currency          VARCHAR(3)               NOT NULL DEFAULT 'VND',
    expected_date     DATE                     NOT NULL,
    received_date     DATE,
    income_type       VARCHAR(30)              NOT NULL, -- SALARY, BONUS, FREELANCE, INVESTMENT, OTHER
    confidence_factor DOUBLE PRECISION         NOT NULL DEFAULT 1.0,
    is_recurring      BOOLEAN                  NOT NULL DEFAULT FALSE,
    frequency         VARCHAR(20),                       -- MONTHLY, BIWEEKLY, AD_HOC
    status            VARCHAR(20)              NOT NULL DEFAULT 'EXPECTED', -- EXPECTED, RECEIVED, CANCELLED
    note              TEXT,
    account_id        UUID                     REFERENCES financial_accounts(id),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE incomes IS 'Expected and received income inflows with confidence weighting';

CREATE INDEX IF NOT EXISTS idx_incomes_status        ON incomes(status);
CREATE INDEX IF NOT EXISTS idx_incomes_expected_date ON incomes(expected_date);

-- 5. Enhance Transactions Table with Account, Debt Reference, and Transaction Types
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS account_id UUID REFERENCES financial_accounts(id),
    ADD COLUMN IF NOT EXISTS debt_id UUID REFERENCES debts(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(30);

-- Backfill legacy records to avoid fabrication of data
UPDATE transactions
SET account_id = '00000000-0000-0000-0000-000000000001'
WHERE account_id IS NULL;

UPDATE transactions
SET transaction_type = CASE
    WHEN spend_type = 'FIXED' THEN 'EXPENSE_FIXED'
    ELSE 'EXPENSE_DISCRETIONARY'
END
WHERE transaction_type IS NULL;

ALTER TABLE transactions
    ALTER COLUMN account_id SET NOT NULL,
    ALTER COLUMN transaction_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_account ON transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_debt    ON transactions(debt_id);
CREATE INDEX IF NOT EXISTS idx_transactions_type    ON transactions(transaction_type);

-- 6. Debt Payments (Actual payments reconciling principal vs interest)
CREATE TABLE IF NOT EXISTS debt_payments (
    id                           UUID                     NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    debt_id                      UUID                     NOT NULL REFERENCES debts(id) ON DELETE CASCADE,
    transaction_id               UUID                     REFERENCES transactions(id) ON DELETE SET NULL,
    payment_date                 DATE                     NOT NULL,
    total_paid                   NUMERIC(19, 2)           NOT NULL,
    principal_portion            NUMERIC(19, 2)           NOT NULL,
    interest_portion             NUMERIC(19, 2)           NOT NULL DEFAULT 0,
    fee_portion                  NUMERIC(19, 2)           NOT NULL DEFAULT 0,
    remaining_principal_snapshot NUMERIC(19, 2)           NOT NULL,
    note                         TEXT,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE debt_payments IS 'Empirical payments allocated between principal, interest, and fees';

CREATE INDEX IF NOT EXISTS idx_debt_payments_debt_date ON debt_payments(debt_id, payment_date DESC);
CREATE INDEX IF NOT EXISTS idx_debt_payments_tx        ON debt_payments(transaction_id);
