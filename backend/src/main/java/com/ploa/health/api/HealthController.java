package com.ploa.health.api;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ploa.health.api.dto.HealthSyncRequest;
import com.ploa.health.api.dto.HealthSyncResponse;
import com.ploa.health.engine.dto.ReadinessResult;
import com.ploa.health.engine.dto.SleepResult;
import com.ploa.health.engine.dto.WeightEmaResult;
import com.ploa.health.service.HealthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * Idempotent upsert of daily physiological metrics.
     * Called by the Garmin puller sidecar or Health Auto Export webhook.
     */
    @PostMapping("/sync")
    public ResponseEntity<HealthSyncResponse> sync(@Valid @RequestBody HealthSyncRequest request) {
        log.info("Health sync received for date: {}", request.date());
        healthService.upsert(request);
        return ResponseEntity.ok(HealthSyncResponse.ok(request.date()));
    }

    /**
     * Get today's readiness report (deterministic Z-scores + sleep).
     */
    @GetMapping("/readiness")
    public ResponseEntity<ReadinessResult> getReadiness(
            @RequestParam(defaultValue = "today") String date) {
        LocalDate targetDate = "today".equals(date) ? LocalDate.now() : LocalDate.parse(date);
        return ResponseEntity.ok(healthService.computeReadiness(targetDate));
    }

    /**
     * Get weight EMA-7 trend.
     */
    @GetMapping("/weight")
    public ResponseEntity<WeightEmaResult> getWeightTrend() {
        return ResponseEntity.ok(healthService.computeWeightEma());
    }

    @GetMapping("/sleep")
    public ResponseEntity<SleepResult> getSleepTrend() {
        return ResponseEntity.ok(healthService.computeSleepTrend());
    }

    /**
     * Get full Garmin biometrics for a specific date (supports "today", "yesterday", or "YYYY-MM-DD").
     */
    @GetMapping("/daily")
    public ResponseEntity<com.ploa.health.model.HealthMetric> getDailyMetrics(
            @RequestParam(defaultValue = "today") String date) {
        LocalDate target = parseTargetDate(date);
        return ResponseEntity.ok(healthService.getMetricsForDate(target));
    }

    /**
     * Get full live Garmin biometrics for today (Body Battery, Stress, Calories, Steps, SpO2, Heart Rate).
     */
    @GetMapping("/today")
    public ResponseEntity<com.ploa.health.model.HealthMetric> getTodayMetrics() {
        return ResponseEntity.ok(healthService.getMetricsForDate(LocalDate.now()));
    }

    private LocalDate parseTargetDate(String date) {
        if (date == null || date.isBlank() || "today".equalsIgnoreCase(date)) {
            return LocalDate.now();
        }
        if ("yesterday".equalsIgnoreCase(date)) {
            return LocalDate.now().minusDays(1);
        }
        return LocalDate.parse(date.trim());
    }

    /**
     * Trigger an on-demand real-time pull from Garmin Connect via the puller sidecar.
     */
    @PostMapping("/pull")
    public ResponseEntity<String> triggerGarminPull() {
        boolean ok = healthService.triggerGarminPull();
        if (ok) {
            return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"Garmin live sync completed\"}");
        } else {
            return ResponseEntity.status(500).body("{\"status\":\"error\",\"message\":\"Failed to trigger Garmin pull\"}");
        }
    }
}
