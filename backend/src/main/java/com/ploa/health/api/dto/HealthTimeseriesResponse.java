package com.ploa.health.api.dto;

import java.time.LocalDate;
import java.util.List;

public record HealthTimeseriesResponse(
        int days,
        Double baselineHrvMean,
        Double baselineHrvStd,
        Double baselineRhrMean,
        Double baselineRhrStd,
        double targetWeightKg,
        double sleepTargetHours,
        List<HealthDailyPoint> points
) {
    public record HealthDailyPoint(
            LocalDate date,
            Double hrvRmssd,
            Double hrvBaselineMean,
            Double hrvUpperThreshold,
            Double hrvLowerThreshold,
            Integer rhr,
            Double rhrBaselineMean,
            Integer sleepScore,
            Double sleepDurationHours,
            Double sleepDebtHours,
            Double deepSleepHours,
            Double remSleepHours,
            Double lightSleepHours,
            Double awakeHours,
            Integer bodyBatteryCharged,
            Integer bodyBatteryDrained,
            Integer bodyBatteryMax,
            Integer bodyBatteryMin,
            Integer stressAvg,
            Integer steps,
            Integer activeKcal,
            Integer totalKcal,
            Double weightKg,
            Double weightEmaKg,
            String recoveryState
    ) {}
}
