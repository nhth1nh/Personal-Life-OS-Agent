package com.ploa.health.api.dto;

import java.time.LocalDate;

public record HealthBatchSyncResponse(
        String status,
        int syncedCount,
        LocalDate startDate,
        LocalDate endDate,
        String message
) {}
