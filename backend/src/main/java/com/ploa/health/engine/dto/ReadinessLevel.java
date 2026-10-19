package com.ploa.health.engine.dto;

/**
 * Nervous system readiness classification based on HRV/RHR Z-scores.
 */
public enum ReadinessLevel {
    OPTIMAL,   // Z > 0.5  — train hard, high-intensity badminton
    NORMAL,    // -0.5 <= Z <= 0.5 — moderate intensity
    FATIGUED,  // Z < -0.5 — reduce intensity
    STRESSED   // Z < -1.0 — rest/recovery day
}
