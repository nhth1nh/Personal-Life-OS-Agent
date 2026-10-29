package com.ploa.health.api;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ploa.health.api.dto.HealthBatchSyncResponse;
import com.ploa.health.api.dto.HealthSyncRequest;
import com.ploa.health.api.dto.HealthSyncResponse;
import com.ploa.health.api.dto.HealthTimeseriesResponse;
import com.ploa.health.api.dto.SyncRangeRequest;
import com.ploa.health.api.dto.SyncRangeResponse;
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
     * Idempotent upsert of daily physiological metrics for a single date.
     * Called by the Garmin puller sidecar or Health Auto Export webhook.
     */
    @PostMapping("/sync")
    public ResponseEntity<HealthSyncResponse> sync(@Valid @RequestBody HealthSyncRequest request) {
        log.info("Health sync received for date: {}", request.date());
        healthService.upsert(request);
        return ResponseEntity.ok(HealthSyncResponse.ok(request.date()));
    }

    /**
     * Batch idempotent upsert of daily metrics for a dynamic range of dates.
     */
    @PostMapping("/sync/batch")
    public ResponseEntity<HealthBatchSyncResponse> syncBatch(@Valid @RequestBody List<HealthSyncRequest> requests) {
        log.info("Batch health sync received with {} entries", requests != null ? requests.size() : 0);
        return ResponseEntity.ok(healthService.upsertBatch(requests));
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
     * Get multi-day historical timeseries with baselines and thresholds for frontend graphs.
     * Supports either rolling ?days=N or explicit dynamic ?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD.
     */
    @GetMapping("/timeseries")
    public ResponseEntity<HealthTimeseriesResponse> getTimeseries(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate != null || endDate != null) {
            return ResponseEntity.ok(healthService.getTimeseries(startDate, endDate));
        }
        return ResponseEntity.ok(healthService.getTimeseries(days));
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
     * Trigger an on-demand real-time or dynamic range pull from Garmin Connect via query parameters.
     * Accessible without body or Content-Type requirement (prevents 415 errors on simple POSTs).
     */
    @PostMapping({"/pull", "/sync/trigger"})
    public ResponseEntity<SyncRangeResponse> triggerGarminPull(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer days) {
        SyncRangeResponse res = healthService.triggerGarminPull(startDate, endDate, days);
        if ("success".equalsIgnoreCase(res.status())) {
            return ResponseEntity.ok(res);
        } else {
            return ResponseEntity.status(502).body(res);
        }
    }

    /**
     * Trigger an on-demand dynamic range pull from Garmin Connect via JSON body.
     */
    @PostMapping(value = {"/pull/range", "/sync/range"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SyncRangeResponse> triggerGarminPullRange(
            @RequestBody SyncRangeRequest body) {
        LocalDate start = body != null ? body.startDate() : null;
        LocalDate end = body != null ? body.endDate() : null;
        Integer days = body != null ? body.days() : null;
        SyncRangeResponse res = healthService.triggerGarminPull(start, end, days);
        if ("success".equalsIgnoreCase(res.status())) {
            return ResponseEntity.ok(res);
        } else {
            return ResponseEntity.status(502).body(res);
        }
    }
}
