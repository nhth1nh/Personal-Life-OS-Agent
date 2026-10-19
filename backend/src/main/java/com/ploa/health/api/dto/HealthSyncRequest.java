package com.ploa.health.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
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

        // Heart Rate
        @JsonProperty("rhr") Integer rhr,
        @JsonProperty("minHr") Integer minHr,
        @JsonProperty("maxHr") Integer maxHr,

        // Sleep
        @JsonProperty("sleepTotalMin") Integer sleepTotalMin,
        @JsonProperty("sleepDeepMin") Integer sleepDeepMin,
        @JsonProperty("sleepRemMin") Integer sleepRemMin,
        @JsonProperty("sleepLightMin") Integer sleepLightMin,
        @JsonProperty("sleepAwakeMin") Integer sleepAwakeMin,
        @JsonProperty("sleepScore") Integer sleepScore,

        // Body Battery & Stress
        @JsonProperty("bodyBatteryCurrent") Integer bodyBatteryCurrent,
        @JsonProperty("bodyBatteryHighest") Integer bodyBatteryHighest,
        @JsonProperty("bodyBatteryLowest") Integer bodyBatteryLowest,
        @JsonProperty("bodyBatteryCharged") Integer bodyBatteryCharged,
        @JsonProperty("bodyBatteryDrained") Integer bodyBatteryDrained,
        @JsonProperty("stressAvg") Integer stressAvg,
        @JsonProperty("stressMax") Integer stressMax,
        @JsonProperty("stressQualifier") String stressQualifier,

        // Activity & Energy
        @JsonProperty("steps") Integer steps,
        @JsonProperty("activeKcal") Integer activeKcal,
        @JsonProperty("totalKcal") Integer totalKcal,
        @JsonProperty("activeTimeMin") Integer activeTimeMin,
        @JsonProperty("moderateMin") Integer moderateMin,
        @JsonProperty("vigorousMin") Integer vigorousMin,
        @JsonProperty("distanceMeters") Integer distanceMeters,

        // SpO2 & Respiration
        @JsonProperty("spo2Avg") Double spo2Avg,
        @JsonProperty("respirationAvg") Double respirationAvg,

        // Body composition
        @JsonProperty("weightKg") Double weightKg,

        // Raw Extensible JSON payload from Garmin
        @JsonProperty("rawDataJsonb") JsonNode rawDataJsonb
) {}
