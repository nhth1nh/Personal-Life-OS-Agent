-- ============================================================
-- PLOA Database Schema - V5 Activities & Workouts Migration
-- ============================================================

CREATE TABLE IF NOT EXISTS activities (
    id                          VARCHAR(64)              NOT NULL PRIMARY KEY, -- Garmin activityId
    name                        VARCHAR(255)             NOT NULL,
    activity_type               VARCHAR(64)              NOT NULL,
    start_time                  TIMESTAMP WITH TIME ZONE NOT NULL,
    date                        DATE                     NOT NULL,
    duration_seconds            DOUBLE PRECISION,
    moving_duration_seconds     DOUBLE PRECISION,
    distance_meters             DOUBLE PRECISION,
    calories                    INTEGER,
    average_hr                  INTEGER,
    max_hr                      INTEGER,
    aerobic_training_effect     DOUBLE PRECISION,
    anaerobic_training_effect   DOUBLE PRECISION,
    training_effect_label       VARCHAR(64),
    training_load               DOUBLE PRECISION,
    average_speed               DOUBLE PRECISION,
    max_speed                   DOUBLE PRECISION,
    elevation_gain_meters       DOUBLE PRECISION,
    elevation_loss_meters       DOUBLE PRECISION,
    steps                       INTEGER,
    moderate_minutes            INTEGER,
    vigorous_minutes            INTEGER,
    difference_body_battery     INTEGER,
    hr_zones_jsonb              JSONB,
    splits_jsonb                JSONB,
    raw_data_jsonb              JSONB,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE activities IS 'Individual athletic activities, sports, and workouts synced from Garmin Connect';

CREATE INDEX idx_activities_date       ON activities(date);
CREATE INDEX idx_activities_start_time ON activities(start_time DESC);
CREATE INDEX idx_activities_type       ON activities(activity_type);
