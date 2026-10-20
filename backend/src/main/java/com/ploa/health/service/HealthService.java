package com.ploa.health.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ploa.health.api.dto.HealthSyncRequest;
import com.ploa.health.engine.ReadinessEngine;
import com.ploa.health.engine.SleepEngine;
import com.ploa.health.engine.WeightEmaEngine;
import com.ploa.health.engine.dto.ReadinessResult;
import com.ploa.health.engine.dto.SleepResult;
import com.ploa.health.engine.dto.WeightEmaResult;
import com.ploa.health.model.HealthMetric;
import com.ploa.health.repository.HealthMetricRepository;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final HealthMetricRepository repo;
    private final ReadinessEngine readinessEngine;
    private final WeightEmaEngine weightEmaEngine;
    private final SleepEngine sleepEngine;

    public HealthService(HealthMetricRepository repo,
                         ReadinessEngine readinessEngine,
                         WeightEmaEngine weightEmaEngine,
                         SleepEngine sleepEngine) {
        this.repo = repo;
        this.readinessEngine = readinessEngine;
        this.weightEmaEngine = weightEmaEngine;
        this.sleepEngine = sleepEngine;
    }

    /**
     * Idempotent upsert: creates or updates the health_metrics row for the given date.
     */
    @Transactional
    public HealthMetric upsert(HealthSyncRequest req) {
        HealthMetric metric = repo.findById(req.date()).orElseGet(() -> {
            HealthMetric m = new HealthMetric();
            m.setDate(req.date());
            return m;
        });

        // Only update non-null fields (allows partial syncs)
        if (req.hrvRmssd() != null)     metric.setHrvRmssd(req.hrvRmssd());
        if (req.hrvWeeklyAvg() != null) metric.setHrvWeeklyAvg(req.hrvWeeklyAvg());
        if (req.rhr() != null)          metric.setRhr(req.rhr());
        if (req.minHr() != null)        metric.setMinHr(req.minHr());
        if (req.maxHr() != null)        metric.setMaxHr(req.maxHr());
        if (req.sleepTotalMin() != null) metric.setSleepTotalMin(req.sleepTotalMin());
        if (req.sleepDeepMin() != null) metric.setSleepDeepMin(req.sleepDeepMin());
        if (req.sleepRemMin() != null)  metric.setSleepRemMin(req.sleepRemMin());
        if (req.sleepLightMin() != null) metric.setSleepLightMin(req.sleepLightMin());
        if (req.sleepAwakeMin() != null) metric.setSleepAwakeMin(req.sleepAwakeMin());
        if (req.sleepScore() != null)   metric.setSleepScore(req.sleepScore());
        if (req.bodyBatteryCurrent() != null) metric.setBodyBatteryCurrent(req.bodyBatteryCurrent());
        if (req.bodyBatteryHighest() != null) metric.setBodyBatteryHighest(req.bodyBatteryHighest());
        if (req.bodyBatteryLowest() != null) metric.setBodyBatteryLowest(req.bodyBatteryLowest());
        if (req.bodyBatteryCharged() != null) metric.setBodyBatteryCharged(req.bodyBatteryCharged());
        if (req.bodyBatteryDrained() != null) metric.setBodyBatteryDrained(req.bodyBatteryDrained());
        if (req.stressAvg() != null)    metric.setStressAvg(req.stressAvg());
        if (req.stressMax() != null)    metric.setStressMax(req.stressMax());
        if (req.stressQualifier() != null) metric.setStressQualifier(req.stressQualifier());
        if (req.steps() != null)        metric.setSteps(req.steps());
        if (req.activeKcal() != null)   metric.setActiveKcal(req.activeKcal());
        if (req.totalKcal() != null)    metric.setTotalKcal(req.totalKcal());
        if (req.activeTimeMin() != null) metric.setActiveTimeMin(req.activeTimeMin());
        if (req.moderateMin() != null)  metric.setModerateMin(req.moderateMin());
        if (req.vigorousMin() != null)  metric.setVigorousMin(req.vigorousMin());
        if (req.distanceMeters() != null) metric.setDistanceMeters(req.distanceMeters());
        if (req.spo2Avg() != null)      metric.setSpo2Avg(req.spo2Avg());
        if (req.respirationAvg() != null) metric.setRespirationAvg(req.respirationAvg());
        if (req.weightKg() != null)     metric.setWeightKg(req.weightKg());
        if (req.rawDataJsonb() != null) metric.setRawDataJsonb(req.rawDataJsonb());

        HealthMetric saved = repo.save(metric);
        log.info("Health metrics upserted for date: {}", saved.getDate());
        return saved;
    }

    @Transactional(readOnly = true)
    public ReadinessResult computeReadiness(LocalDate date) {
        return readinessEngine.compute(date);
    }

    @Transactional(readOnly = true)
    public WeightEmaResult computeWeightEma() {
        return weightEmaEngine.compute();
    }

    @Transactional(readOnly = true)
    public SleepResult computeSleepTrend() {
        return sleepEngine.compute();
    }

    @Transactional(readOnly = true)
    public HealthMetric getMetricsForDate(LocalDate date) {
        return repo.findById(date != null ? date : LocalDate.now()).orElse(null);
    }

    /**
     * Trigger an on-demand real-time pull from Garmin Connect via the puller sidecar.
     *
     * @return true if successful, false otherwise
     */
    public boolean triggerGarminPull() {
        try {
            var rest = org.springframework.web.client.RestClient.create();
            rest.post()
                    .uri("http://garmin-puller:8081/pull")
                    .retrieve()
                    .toBodilessEntity();
            log.info("Garmin on-demand pull successfully completed");
            return true;
        } catch (Exception e) {
            log.error("Failed to trigger on-demand Garmin pull: {}", e.getMessage());
            return false;
        }
    }
}
