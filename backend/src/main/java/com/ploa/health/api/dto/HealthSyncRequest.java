package com.ploa.health.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Payload accepted by POST /api/v1/health/sync
 * All fields except 'date' are optional — allows partial syncs.
 */
public record HealthSyncRequest(
        @NotNull LocalDate date,

        // HRV
        @JsonProperty("hrvRmssd") Double hrvRmssd,
        @JsonProperty("hrvWeeklyAvg") Double hrvWeeklyAvg,

        // Resting Heart Rate
        @JsonProperty("rhr") Integer rhr,

        // Sleep (in minutes)
        @JsonProperty("sleepTotalMin") Integer sleepTotalMin,
        @JsonProperty("sleepDeepMin") Integer sleepDeepMin,
        @JsonProperty("sleepRemMin") Integer sleepRemMin,
        @JsonProperty("sleepLightMin") Integer sleepLightMin,
        @JsonProperty("sleepAwakeMin") Integer sleepAwakeMin,

        // Activity
        @JsonProperty("steps") Integer steps,
        @JsonProperty("activeKcal") Integer activeKcal,

        // Body composition
        @JsonProperty("weightKg") Double weightKg
) {}
