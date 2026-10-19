-- ============================================================
-- PLOA Database Schema - V2 Expand Garmin Health Metrics & Realtime
-- ============================================================

ALTER TABLE health_metrics
    ADD COLUMN IF NOT EXISTS sleep_score              INTEGER,
    ADD COLUMN IF NOT EXISTS body_battery_current     INTEGER,
    ADD COLUMN IF NOT EXISTS body_battery_highest     INTEGER,
    ADD COLUMN IF NOT EXISTS body_battery_lowest      INTEGER,
    ADD COLUMN IF NOT EXISTS body_battery_charged     INTEGER,
    ADD COLUMN IF NOT EXISTS body_battery_drained     INTEGER,
    ADD COLUMN IF NOT EXISTS stress_avg               INTEGER,
    ADD COLUMN IF NOT EXISTS stress_max               INTEGER,
    ADD COLUMN IF NOT EXISTS stress_qualifier         VARCHAR(50),
    ADD COLUMN IF NOT EXISTS min_hr                   INTEGER,
    ADD COLUMN IF NOT EXISTS max_hr                   INTEGER,
    ADD COLUMN IF NOT EXISTS active_time_min          INTEGER,
    ADD COLUMN IF NOT EXISTS moderate_min             INTEGER,
    ADD COLUMN IF NOT EXISTS vigorous_min             INTEGER,
    ADD COLUMN IF NOT EXISTS distance_meters          INTEGER,
    ADD COLUMN IF NOT EXISTS total_kcal               INTEGER,
    ADD COLUMN IF NOT EXISTS spo2_avg                 DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS respiration_avg          DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS raw_data_jsonb           JSONB;

COMMENT ON COLUMN health_metrics.body_battery_current IS 'Latest Body Battery value (0-100)';
COMMENT ON COLUMN health_metrics.stress_avg IS 'Average daily stress score (0-100)';
COMMENT ON COLUMN health_metrics.raw_data_jsonb IS 'Full raw JSON payload from Garmin Connect for deep exploration';
