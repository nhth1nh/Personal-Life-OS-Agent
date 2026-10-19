-- ============================================================
-- PLOA Database Schema - V1 Initial Migration
-- ============================================================

-- Health metrics: one row per day (upsert-safe)
CREATE TABLE IF NOT EXISTS health_metrics (
    date              DATE        NOT NULL PRIMARY KEY,
    hrv_rmssd         DOUBLE PRECISION,          -- HRV rMSSD in ms
    hrv_weekly_avg    DOUBLE PRECISION,          -- 7-day Garmin HRV average
    rhr               INTEGER,                   -- Resting Heart Rate (bpm)
    sleep_total_min   INTEGER,                   -- Total sleep in minutes
    sleep_deep_min    INTEGER,                   -- Deep sleep in minutes
    sleep_rem_min     INTEGER,                   -- REM sleep in minutes
    sleep_light_min   INTEGER,                   -- Light sleep in minutes
    sleep_awake_min   INTEGER,                   -- Awake time in minutes
    steps             INTEGER,                   -- Total daily steps
    active_kcal       INTEGER,                   -- Active calories burned
    weight_kg         DOUBLE PRECISION,          -- Body weight in kg
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE health_metrics IS 'Daily physiological metrics ingested from Garmin/HealthKit';

-- Transactions: financial ledger
CREATE TABLE IF NOT EXISTS transactions (
    id            UUID                     NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tx_date       DATE                     NOT NULL,
    amount        BIGINT                   NOT NULL,  -- amount in VND (smallest unit)
    currency      VARCHAR(3)               NOT NULL DEFAULT 'VND',
    category      VARCHAR(50)              NOT NULL,  -- Food, Transport, Bills, Shopping, Sports, Other
    spend_type    VARCHAR(15)              NOT NULL,  -- FIXED | DISCRETIONARY
    merchant      VARCHAR(255),
    note          TEXT,
    document_type VARCHAR(30),                       -- BANK_TRANSFER | RECEIPT | E_COMMERCE | TEXT
    source_platform VARCHAR(50),                     -- VietQR, Shopee, manual, etc.
    items_jsonb   JSONB,                             -- line items from receipt
    telegram_msg_id BIGINT,                          -- Telegram message_id for callback reference
    confirmed     BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE transactions IS 'Financial transactions captured via Telegram Bot (receipts, transfers, manual text)';

CREATE INDEX idx_transactions_tx_date      ON transactions(tx_date);
CREATE INDEX idx_transactions_category     ON transactions(category);
CREATE INDEX idx_transactions_spend_type   ON transactions(spend_type);
CREATE INDEX idx_transactions_confirmed    ON transactions(confirmed);

-- Daily insights: output of the morning briefing LLM synthesis
CREATE TABLE IF NOT EXISTS daily_insights (
    date                  DATE   NOT NULL PRIMARY KEY,
    readiness_score       VARCHAR(20),       -- OPTIMAL | NORMAL | FATIGUED | STRESSED
    hrv_z_score           DOUBLE PRECISION,
    rhr_z_score           DOUBLE PRECISION,
    sleep_debt_min        INTEGER,
    weight_ema7           DOUBLE PRECISION,
    weight_trend_kg_week  DOUBLE PRECISION,  -- kg change per week
    daily_burn_rate_vnd   BIGINT,            -- discretionary spend so far today
    mtd_spend_vnd         BIGINT,            -- month-to-date total spend
    remaining_budget_vnd  BIGINT,
    briefing_text         TEXT,              -- LLM-generated morning message
    sent_at               TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE daily_insights IS 'Daily briefing output: deterministic metrics + LLM synthesis';

-- Auto-update updated_at via trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_health_metrics_updated_at
    BEFORE UPDATE ON health_metrics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
