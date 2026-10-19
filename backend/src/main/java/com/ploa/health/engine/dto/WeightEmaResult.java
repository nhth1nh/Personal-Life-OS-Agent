package com.ploa.health.engine.dto;

import java.time.LocalDate;

public record WeightEmaResult(
        LocalDate lastMeasurementDate,
        Double latestWeightKg,
        Double ema7Kg,              // 7-day EMA smoothed weight
        Double targetWeightKg,
        Double deltaToTargetKg,     // ema7 - target (negative = progress)
        Double weeklyTrendKg        // kg change per week (negative = losing)
) {}
