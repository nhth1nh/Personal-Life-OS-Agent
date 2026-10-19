package com.ploa.health.engine.dto;

import java.time.LocalDate;

public record ReadinessResult(
        LocalDate date,

        // HRV metrics
        Double hrvRmssd,
        Double hrv30dMean,
        Double hrv30dStdDev,
        Double hrvZScore,

        // RHR metrics
        Integer rhr,
        Double rhr30dMean,
        Double rhr30dStdDev,
        Double rhrZScore,

        // Sleep
        Integer sleepTotalMin,
        Integer sleepDeepMin,
        Integer sleepRemMin,
        Integer sleepDebtMin,       // Cumulative 7-day debt vs 7.5h target
        Double restorativeRatioPct, // (Deep + REM) / Total * 100

        // Overall classification
        ReadinessLevel level,
        String summary
) {
    /** Combined Z-score: average of HRV Z and inverse RHR Z (higher RHR = worse). */
    public Double combinedZScore() {
        if (hrvZScore == null && rhrZScore == null) return null;
        if (hrvZScore == null) return -rhrZScore;
        if (rhrZScore == null) return hrvZScore;
        return (hrvZScore + (-rhrZScore)) / 2.0;
    }
}
