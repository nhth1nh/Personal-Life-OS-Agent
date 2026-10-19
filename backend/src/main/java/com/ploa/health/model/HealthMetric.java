package com.ploa.health.model;

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

    @Column(name = "steps")
    private Integer steps;

    @Column(name = "active_kcal")
    private Integer activeKcal;

    @Column(name = "weight_kg")
    private Double weightKg;

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

    // Constructor
    public HealthMetric() {}

    // Getters and setters
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
    public Integer getSteps() { return steps; }
    public void setSteps(Integer steps) { this.steps = steps; }
    public Integer getActiveKcal() { return activeKcal; }
    public void setActiveKcal(Integer activeKcal) { this.activeKcal = activeKcal; }
    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
