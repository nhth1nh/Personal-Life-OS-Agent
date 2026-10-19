package com.ploa.health.api.dto;

import java.time.LocalDate;

public record HealthSyncResponse(
        String status,
        LocalDate date,
        String message
) {
    public static HealthSyncResponse ok(LocalDate date) {
        return new HealthSyncResponse("OK", date, "Health metrics synced successfully");
    }
}
