package com.ploa.health.api.dto;

import java.time.LocalDate;

public record SyncRangeResponse(
        String status,
        String message,
        LocalDate startDate,
        LocalDate endDate,
        Integer syncedDays,
        Integer failedDays
) {
    public static SyncRangeResponse success(String message, LocalDate start, LocalDate end, int synced, int failed) {
        return new SyncRangeResponse("success", message, start, end, synced, failed);
    }

    public static SyncRangeResponse error(String message) {
        return new SyncRangeResponse("error", message, null, null, 0, 0);
    }
}
