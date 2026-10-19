package com.ploa.health.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "health_metrics")
public class HealthMetric {

    @Id
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "hrv_rmssd")
    private Double hrvRmssd;

    @Column(name = "hrv_weekly_avg")
    private Double hrvWeeklyAvg;

    @Column(name = "rhr")
    private Integer rhr;

    @Column(name = "sleep_total_min")
    private Integer sleepTotalMin;

    @Column(name = "sleep_deep_min")
    private Integer sleepDeepMin;

    @Column(name = "sleep_rem_min")
    private Integer sleepRemMin;

    @Column(name = "sleep_light_min")
    private Integer sleepLightMin;

    @Column(name = "sleep_awake_min")
    private Integer sleepAwakeMin;

    @Column(name = "sleep_score")
    private Integer sleepScore;

    @Column(name = "steps")
    private Integer steps;

    @Column(name = "active_kcal")
    private Integer activeKcal;

    @Column(name = "total_kcal")
    private Integer totalKcal;

    @Column(name = "active_time_min")
    private Integer activeTimeMin;

    @Column(name = "moderate_min")
    private Integer moderateMin;

    @Column(name = "vigorous_min")
    private Integer vigorousMin;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Column(name = "body_battery_current")
    private Integer bodyBatteryCurrent;

    @Column(name = "body_battery_highest")
    private Integer bodyBatteryHighest;

    @Column(name = "body_battery_lowest")
    private Integer bodyBatteryLowest;

    @Column(name = "body_battery_charged")
    private Integer bodyBatteryCharged;

    @Column(name = "body_battery_drained")
    private Integer bodyBatteryDrained;

    @Column(name = "stress_avg")
    private Integer stressAvg;

    @Column(name = "stress_max")
    private Integer stressMax;

    @Column(name = "stress_qualifier", length = 50)
    private String stressQualifier;

    @Column(name = "min_hr")
    private Integer minHr;

    @Column(name = "max_hr")
    private Integer maxHr;

    @Column(name = "spo2_avg")
    private Double spo2Avg;

    @Column(name = "respiration_avg")
    private Double respirationAvg;

    @Column(name = "weight_kg")
    private Double weightKg;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "raw_data_jsonb", columnDefinition = "jsonb")
    private JsonNode rawDataJsonb;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Double getHrvRmssd() { return hrvRmssd; }
    public void setHrvRmssd(Double hrvRmssd) { this.hrvRmssd = hrvRmssd; }

    public Double getHrvWeeklyAvg() { return hrvWeeklyAvg; }
    public void setHrvWeeklyAvg(Double hrvWeeklyAvg) { this.hrvWeeklyAvg = hrvWeeklyAvg; }

    public Integer getRhr() { return rhr; }
    public void setRhr(Integer rhr) { this.rhr = rhr; }

    public Integer getSleepTotalMin() { return sleepTotalMin; }
    public void setSleepTotalMin(Integer sleepTotalMin) { this.sleepTotalMin = sleepTotalMin; }

    public Integer getSleepDeepMin() { return sleepDeepMin; }
    public void setSleepDeepMin(Integer sleepDeepMin) { this.sleepDeepMin = sleepDeepMin; }

    public Integer getSleepRemMin() { return sleepRemMin; }
    public void setSleepRemMin(Integer sleepRemMin) { this.sleepRemMin = sleepRemMin; }

    public Integer getSleepLightMin() { return sleepLightMin; }
    public void setSleepLightMin(Integer sleepLightMin) { this.sleepLightMin = sleepLightMin; }

    public Integer getSleepAwakeMin() { return sleepAwakeMin; }
    public void setSleepAwakeMin(Integer sleepAwakeMin) { this.sleepAwakeMin = sleepAwakeMin; }

    public Integer getSleepScore() { return sleepScore; }
    public void setSleepScore(Integer sleepScore) { this.sleepScore = sleepScore; }

    public Integer getSteps() { return steps; }
    public void setSteps(Integer steps) { this.steps = steps; }

    public Integer getActiveKcal() { return activeKcal; }
    public void setActiveKcal(Integer activeKcal) { this.activeKcal = activeKcal; }

    public Integer getTotalKcal() { return totalKcal; }
    public void setTotalKcal(Integer totalKcal) { this.totalKcal = totalKcal; }

    public Integer getActiveTimeMin() { return activeTimeMin; }
    public void setActiveTimeMin(Integer activeTimeMin) { this.activeTimeMin = activeTimeMin; }

    public Integer getModerateMin() { return moderateMin; }
    public void setModerateMin(Integer moderateMin) { this.moderateMin = moderateMin; }

    public Integer getVigorousMin() { return vigorousMin; }
    public void setVigorousMin(Integer vigorousMin) { this.vigorousMin = vigorousMin; }

    public Integer getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Integer distanceMeters) { this.distanceMeters = distanceMeters; }

    public Integer getBodyBatteryCurrent() { return bodyBatteryCurrent; }
    public void setBodyBatteryCurrent(Integer bodyBatteryCurrent) { this.bodyBatteryCurrent = bodyBatteryCurrent; }

    public Integer getBodyBatteryHighest() { return bodyBatteryHighest; }
    public void setBodyBatteryHighest(Integer bodyBatteryHighest) { this.bodyBatteryHighest = bodyBatteryHighest; }

    public Integer getBodyBatteryLowest() { return bodyBatteryLowest; }
    public void setBodyBatteryLowest(Integer bodyBatteryLowest) { this.bodyBatteryLowest = bodyBatteryLowest; }

    public Integer getBodyBatteryCharged() { return bodyBatteryCharged; }
    public void setBodyBatteryCharged(Integer bodyBatteryCharged) { this.bodyBatteryCharged = bodyBatteryCharged; }

    public Integer getBodyBatteryDrained() { return bodyBatteryDrained; }
    public void setBodyBatteryDrained(Integer bodyBatteryDrained) { this.bodyBatteryDrained = bodyBatteryDrained; }

    public Integer getStressAvg() { return stressAvg; }
    public void setStressAvg(Integer stressAvg) { this.stressAvg = stressAvg; }

    public Integer getStressMax() { return stressMax; }
    public void setStressMax(Integer stressMax) { this.stressMax = stressMax; }

    public String getStressQualifier() { return stressQualifier; }
    public void setStressQualifier(String stressQualifier) { this.stressQualifier = stressQualifier; }

    public Integer getMinHr() { return minHr; }
    public void setMinHr(Integer minHr) { this.minHr = minHr; }

    public Integer getMaxHr() { return maxHr; }
    public void setMaxHr(Integer maxHr) { this.maxHr = maxHr; }

    public Double getSpo2Avg() { return spo2Avg; }
    public void setSpo2Avg(Double spo2Avg) { this.spo2Avg = spo2Avg; }

    public Double getRespirationAvg() { return respirationAvg; }
    public void setRespirationAvg(Double respirationAvg) { this.respirationAvg = respirationAvg; }

    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }

    public JsonNode getRawDataJsonb() { return rawDataJsonb; }
    public void setRawDataJsonb(JsonNode rawDataJsonb) { this.rawDataJsonb = rawDataJsonb; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
