package com.ploa.finance.api.dto;

public record OcrMetricsResponse(
        long totalScans,
        long confirmedAsIs,
        long confirmedModified,
        long discarded,
        long pending,
        double firstShotAcceptanceRatePct,
        double userCorrectionRatePct,
        double discardRatePct,
        double averageConfidenceScore
) {}
