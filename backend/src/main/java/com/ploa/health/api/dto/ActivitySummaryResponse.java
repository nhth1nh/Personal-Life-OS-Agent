package com.ploa.health.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ploa.health.model.Activity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ActivitySummaryResponse(
        String id,
        String name,
        String activityType,
        OffsetDateTime startTime,
        LocalDate date,
        Double durationMinutes,
        Double distanceKm,
        Integer calories,
        Integer averageHr,
        Integer maxHr,
        Double aerobicTrainingEffect,
        Double anaerobicTrainingEffect,
        String trainingEffectLabel,
        Double trainingLoad,
        Double avgPaceMinPerKm,
        Double avgSpeedKmh,
        Double elevationGainMeters,
        Integer steps,
        Integer moderateMinutes,
        Integer vigorousMinutes,
        Integer differenceBodyBattery,
        JsonNode hrZonesJsonb,
        JsonNode splitsJsonb
) {
    public static ActivitySummaryResponse fromEntity(Activity a) {
        Double durationMin = a.getDurationSeconds() != null ? Math.round((a.getDurationSeconds() / 60.0) * 10.0) / 10.0 : null;
        Double distKm = a.getDistanceMeters() != null ? Math.round((a.getDistanceMeters() / 1000.0) * 100.0) / 100.0 : null;
        
        Double avgSpeedKmh = null;
        Double avgPace = null;
        if (a.getAverageSpeed() != null && a.getAverageSpeed() > 0) {
            avgSpeedKmh = Math.round((a.getAverageSpeed() * 3.6) * 10.0) / 10.0;
            double secPerKm = 1000.0 / a.getAverageSpeed();
            avgPace = Math.round((secPerKm / 60.0) * 100.0) / 100.0;
        }

        return new ActivitySummaryResponse(
                a.getId(),
                a.getName(),
                a.getActivityType(),
                a.getStartTime(),
                a.getDate(),
                durationMin,
                distKm,
                a.getCalories(),
                a.getAverageHr(),
                a.getMaxHr(),
                a.getAerobicTrainingEffect() != null ? Math.round(a.getAerobicTrainingEffect() * 10.0) / 10.0 : null,
                a.getAnaerobicTrainingEffect() != null ? Math.round(a.getAnaerobicTrainingEffect() * 10.0) / 10.0 : null,
                a.getTrainingEffectLabel(),
                a.getTrainingLoad() != null ? Math.round(a.getTrainingLoad() * 10.0) / 10.0 : null,
                avgPace,
                avgSpeedKmh,
                a.getElevationGainMeters(),
                a.getSteps(),
                a.getModerateMinutes(),
                a.getVigorousMinutes(),
                a.getDifferenceBodyBattery(),
                a.getHrZonesJsonb(),
                a.getSplitsJsonb()
        );
    }
}
