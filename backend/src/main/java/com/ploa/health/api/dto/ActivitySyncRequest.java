package com.ploa.health.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ActivitySyncRequest(
        @NotBlank @JsonProperty("activityId") String activityId,
        @JsonProperty("name") String name,
        @JsonProperty("activityType") String activityType,
        @JsonProperty("startTime") OffsetDateTime startTime,
        @NotNull @JsonProperty("date") LocalDate date,
        @JsonProperty("durationSeconds") Double durationSeconds,
        @JsonProperty("movingDurationSeconds") Double movingDurationSeconds,
        @JsonProperty("distanceMeters") Double distanceMeters,
        @JsonProperty("calories") Integer calories,
        @JsonProperty("averageHr") Integer averageHr,
        @JsonProperty("maxHr") Integer maxHr,
        @JsonProperty("aerobicTrainingEffect") Double aerobicTrainingEffect,
        @JsonProperty("anaerobicTrainingEffect") Double anaerobicTrainingEffect,
        @JsonProperty("trainingEffectLabel") String trainingEffectLabel,
        @JsonProperty("trainingLoad") Double trainingLoad,
        @JsonProperty("averageSpeed") Double averageSpeed,
        @JsonProperty("maxSpeed") Double maxSpeed,
        @JsonProperty("elevationGainMeters") Double elevationGainMeters,
        @JsonProperty("elevationLossMeters") Double elevationLossMeters,
        @JsonProperty("steps") Integer steps,
        @JsonProperty("moderateMinutes") Integer moderateMinutes,
        @JsonProperty("vigorousMinutes") Integer vigorousMinutes,
        @JsonProperty("differenceBodyBattery") Integer differenceBodyBattery,
        @JsonProperty("hrZonesJsonb") JsonNode hrZonesJsonb,
        @JsonProperty("splitsJsonb") JsonNode splitsJsonb,
        @JsonProperty("rawDataJsonb") JsonNode rawDataJsonb
) {}
