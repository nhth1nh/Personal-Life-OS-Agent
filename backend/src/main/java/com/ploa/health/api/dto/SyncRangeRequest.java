package com.ploa.health.api.dto;

import java.time.LocalDate;

public record SyncRangeRequest(
        LocalDate startDate,
        LocalDate endDate,
        Integer days
) {}
