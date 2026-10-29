package com.ploa.health.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id; // Garmin activityId

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "activity_type", nullable = false, length = 64)
    private String activityType;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "moving_duration_seconds")
    private Double movingDurationSeconds;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Column(name = "calories")
    private Integer calories;

    @Column(name = "average_hr")
    private Integer averageHr;

    @Column(name = "max_hr")
    private Integer maxHr;

    @Column(name = "aerobic_training_effect")
    private Double aerobicTrainingEffect;

    @Column(name = "anaerobic_training_effect")
    private Double anaerobicTrainingEffect;

    @Column(name = "training_effect_label", length = 64)
    private String trainingEffectLabel;

    @Column(name = "training_load")
    private Double trainingLoad;

    @Column(name = "average_speed")
    private Double averageSpeed;

    @Column(name = "max_speed")
    private Double maxSpeed;

    @Column(name = "elevation_gain_meters")
    private Double elevationGainMeters;

    @Column(name = "elevation_loss_meters")
    private Double elevationLossMeters;

    @Column(name = "steps")
    private Integer steps;

    @Column(name = "moderate_minutes")
    private Integer moderateMinutes;

    @Column(name = "vigorous_minutes")
    private Integer vigorousMinutes;

    @Column(name = "difference_body_battery")
    private Integer differenceBodyBattery;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hr_zones_jsonb", columnDefinition = "jsonb")
    private JsonNode hrZonesJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "splits_jsonb", columnDefinition = "jsonb")
    private JsonNode splitsJsonb;

    @JdbcTypeCode(SqlTypes.JSON)
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
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }

    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }

    public Double getMovingDurationSeconds() { return movingDurationSeconds; }
    public void setMovingDurationSeconds(Double movingDurationSeconds) { this.movingDurationSeconds = movingDurationSeconds; }

    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }

    public Integer getCalories() { return calories; }
    public void setCalories(Integer calories) { this.calories = calories; }

    public Integer getAverageHr() { return averageHr; }
    public void setAverageHr(Integer averageHr) { this.averageHr = averageHr; }

    public Integer getMaxHr() { return maxHr; }
    public void setMaxHr(Integer maxHr) { this.maxHr = maxHr; }

    public Double getAerobicTrainingEffect() { return aerobicTrainingEffect; }
    public void setAerobicTrainingEffect(Double aerobicTrainingEffect) { this.aerobicTrainingEffect = aerobicTrainingEffect; }

    public Double getAnaerobicTrainingEffect() { return anaerobicTrainingEffect; }
    public void setAnaerobicTrainingEffect(Double anaerobicTrainingEffect) { this.anaerobicTrainingEffect = anaerobicTrainingEffect; }

    public String getTrainingEffectLabel() { return trainingEffectLabel; }
    public void setTrainingEffectLabel(String trainingEffectLabel) { this.trainingEffectLabel = trainingEffectLabel; }

    public Double getTrainingLoad() { return trainingLoad; }
    public void setTrainingLoad(Double trainingLoad) { this.trainingLoad = trainingLoad; }

    public Double getAverageSpeed() { return averageSpeed; }
    public void setAverageSpeed(Double averageSpeed) { this.averageSpeed = averageSpeed; }

    public Double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(Double maxSpeed) { this.maxSpeed = maxSpeed; }

    public Double getElevationGainMeters() { return elevationGainMeters; }
    public void setElevationGainMeters(Double elevationGainMeters) { this.elevationGainMeters = elevationGainMeters; }

    public Double getElevationLossMeters() { return elevationLossMeters; }
    public void setElevationLossMeters(Double elevationLossMeters) { this.elevationLossMeters = elevationLossMeters; }

    public Integer getSteps() { return steps; }
    public void setSteps(Integer steps) { this.steps = steps; }

    public Integer getModerateMinutes() { return moderateMinutes; }
    public void setModerateMinutes(Integer moderateMinutes) { this.moderateMinutes = moderateMinutes; }

    public Integer getVigorousMinutes() { return vigorousMinutes; }
    public void setVigorousMinutes(Integer vigorousMinutes) { this.vigorousMinutes = vigorousMinutes; }

    public Integer getDifferenceBodyBattery() { return differenceBodyBattery; }
    public void setDifferenceBodyBattery(Integer differenceBodyBattery) { this.differenceBodyBattery = differenceBodyBattery; }

    public JsonNode getHrZonesJsonb() { return hrZonesJsonb; }
    public void setHrZonesJsonb(JsonNode hrZonesJsonb) { this.hrZonesJsonb = hrZonesJsonb; }

    public JsonNode getSplitsJsonb() { return splitsJsonb; }
    public void setSplitsJsonb(JsonNode splitsJsonb) { this.splitsJsonb = splitsJsonb; }

    public JsonNode getRawDataJsonb() { return rawDataJsonb; }
    public void setRawDataJsonb(JsonNode rawDataJsonb) { this.rawDataJsonb = rawDataJsonb; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
