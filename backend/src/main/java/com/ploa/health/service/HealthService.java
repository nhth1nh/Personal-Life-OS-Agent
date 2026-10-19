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
        if (req.sleepTotalMin() != null) metric.setSleepTotalMin(req.sleepTotalMin());
        if (req.sleepDeepMin() != null) metric.setSleepDeepMin(req.sleepDeepMin());
        if (req.sleepRemMin() != null)  metric.setSleepRemMin(req.sleepRemMin());
        if (req.sleepLightMin() != null) metric.setSleepLightMin(req.sleepLightMin());
        if (req.sleepAwakeMin() != null) metric.setSleepAwakeMin(req.sleepAwakeMin());
        if (req.steps() != null)        metric.setSteps(req.steps());
        if (req.activeKcal() != null)   metric.setActiveKcal(req.activeKcal());
        if (req.weightKg() != null)     metric.setWeightKg(req.weightKg());

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
}
