package com.ploa.health.service;

import com.ploa.health.api.dto.ActivitySummaryResponse;
import com.ploa.health.api.dto.ActivitySyncRequest;
import com.ploa.health.model.Activity;
import com.ploa.health.repository.ActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityRepository repo;

    public ActivityService(ActivityRepository repo) {
        this.repo = repo;
    }

    /**
     * Idempotent upsert of a single activity by activityId.
     */
    @Transactional
    public Activity upsert(ActivitySyncRequest req) {
        Activity act = repo.findById(req.activityId()).orElseGet(() -> {
            Activity a = new Activity();
            a.setId(req.activityId());
            return a;
        });

        act.setName(req.name() != null ? req.name() : "Workout");
        act.setActivityType(req.activityType() != null ? req.activityType() : "other");
        act.setStartTime(req.startTime() != null ? req.startTime() : OffsetDateTime.now());
        act.setDate(req.date() != null ? req.date() : act.getStartTime().toLocalDate());
        act.setDurationSeconds(req.durationSeconds());
        act.setMovingDurationSeconds(req.movingDurationSeconds());
        act.setDistanceMeters(req.distanceMeters());
        act.setCalories(req.calories());
        act.setAverageHr(req.averageHr());
        act.setMaxHr(req.maxHr());
        act.setAerobicTrainingEffect(req.aerobicTrainingEffect());
        act.setAnaerobicTrainingEffect(req.anaerobicTrainingEffect());
        act.setTrainingEffectLabel(req.trainingEffectLabel());
        act.setTrainingLoad(req.trainingLoad());
        act.setAverageSpeed(req.averageSpeed());
        act.setMaxSpeed(req.maxSpeed());
        act.setElevationGainMeters(req.elevationGainMeters());
        act.setElevationLossMeters(req.elevationLossMeters());
        act.setSteps(req.steps());
        act.setModerateMinutes(req.moderateMinutes());
        act.setVigorousMinutes(req.vigorousMinutes());
        act.setDifferenceBodyBattery(req.differenceBodyBattery());
        if (req.hrZonesJsonb() != null) act.setHrZonesJsonb(req.hrZonesJsonb());
        if (req.splitsJsonb() != null) act.setSplitsJsonb(req.splitsJsonb());
        if (req.rawDataJsonb() != null) act.setRawDataJsonb(req.rawDataJsonb());

        Activity saved = repo.save(act);
        log.info("Activity upserted: {} - {} (ID: {})", saved.getDate(), saved.getName(), saved.getId());
        return saved;
    }

    /**
     * Batch upsert of activities.
     */
    @Transactional
    public int upsertBatch(List<ActivitySyncRequest> requests) {
        if (requests == null || requests.isEmpty()) return 0;
        int count = 0;
        for (ActivitySyncRequest req : requests) {
            if (req.activityId() == null || req.activityId().isBlank()) continue;
            upsert(req);
            count++;
        }
        return count;
    }

    @Transactional(readOnly = true)
    public List<ActivitySummaryResponse> getActivitiesForDate(LocalDate date) {
        return repo.findByDateOrderByStartTimeDesc(date).stream()
                .map(ActivitySummaryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivitySummaryResponse> getActivitiesBetween(LocalDate start, LocalDate end) {
        return repo.findByDateBetweenOrderByStartTimeDesc(start, end).stream()
                .map(ActivitySummaryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivitySummaryResponse> getRecentActivities(int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        return repo.findRecentActivities(PageRequest.of(0, cappedLimit)).stream()
                .map(ActivitySummaryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ActivitySummaryResponse> getActivityById(String id) {
        return repo.findById(id).map(ActivitySummaryResponse::fromEntity);
    }

    /**
     * Aggregate workout summary for a given day (for briefings & context).
     */
    @Transactional(readOnly = true)
    public String getWorkoutSummaryText(LocalDate date) {
        List<Activity> list = repo.findByDateOrderByStartTimeDesc(date);
        if (list.isEmpty()) {
            return "No recorded workouts.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(list.size()).append(" workout(s): ");
        for (int i = 0; i < list.size(); i++) {
            Activity a = list.get(i);
            if (i > 0) sb.append("; ");
            double min = a.getDurationSeconds() != null ? Math.round(a.getDurationSeconds() / 60.0) : 0;
            sb.append(a.getName()).append(" (").append((int) min).append(" min");
            if (a.getCalories() != null && a.getCalories() > 0) {
                sb.append(", ").append(a.getCalories()).append(" kcal");
            }
            if (a.getAverageHr() != null && a.getAverageHr() > 0) {
                sb.append(", avg HR ").append(a.getAverageHr()).append(" bpm");
            }
            if (a.getMaxHr() != null && a.getMaxHr() > 0) {
                sb.append(", max HR ").append(a.getMaxHr()).append(" bpm");
            }
            if (a.getAerobicTrainingEffect() != null || a.getAnaerobicTrainingEffect() != null) {
                sb.append(", TE: ");
                if (a.getAerobicTrainingEffect() != null) sb.append(a.getAerobicTrainingEffect()).append(" aero");
                if (a.getAnaerobicTrainingEffect() != null) sb.append("/").append(a.getAnaerobicTrainingEffect()).append(" anaero");
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
