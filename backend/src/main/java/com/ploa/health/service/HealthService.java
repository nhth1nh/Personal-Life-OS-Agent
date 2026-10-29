package com.ploa.health.service;

import com.ploa.config.PloaProperties;
import com.ploa.health.api.dto.HealthSyncRequest;
import com.ploa.health.api.dto.HealthTimeseriesResponse;
import com.ploa.health.engine.ReadinessEngine;
import com.ploa.health.engine.SleepEngine;
import com.ploa.health.engine.WeightEmaEngine;
import com.ploa.health.engine.dto.ReadinessResult;
import com.ploa.health.engine.dto.SleepResult;
import com.ploa.health.engine.dto.WeightEmaResult;
import com.ploa.health.model.HealthMetric;
import com.ploa.health.repository.HealthMetricRepository;
import com.ploa.profile.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final HealthMetricRepository repo;
    private final ReadinessEngine readinessEngine;
    private final WeightEmaEngine weightEmaEngine;
    private final SleepEngine sleepEngine;
    private final PloaProperties properties;
    private final UserProfileService profileService;
    private final RestClient restClient;

    @Autowired
    public HealthService(HealthMetricRepository repo,
                         ReadinessEngine readinessEngine,
                         WeightEmaEngine weightEmaEngine,
                         SleepEngine sleepEngine,
                         PloaProperties properties,
                         UserProfileService profileService) {
        this.repo = repo;
        this.readinessEngine = readinessEngine;
        this.weightEmaEngine = weightEmaEngine;
        this.sleepEngine = sleepEngine;
        this.properties = properties;
        this.profileService = profileService;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public HealthService(HealthMetricRepository repo,
                         ReadinessEngine readinessEngine,
                         WeightEmaEngine weightEmaEngine,
                         SleepEngine sleepEngine,
                         PloaProperties properties) {
        this(repo, readinessEngine, weightEmaEngine, sleepEngine, properties, null);
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
     * Compute chronological daily points for charting with baselines and thresholds.
     */
    @Transactional(readOnly = true)
    public HealthTimeseriesResponse getTimeseries(int days) {
        int cappedDays = Math.max(7, Math.min(days, 365));
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(cappedDays - 1);

        double targetWeight = profileService != null ? profileService.getTargetWeightKg() : 68.0;
        double sleepTarget = profileService != null ? profileService.getSleepTargetHours() : 7.5;
        int baselineWindow = profileService != null ? profileService.getBaselineWindowDays() : 30;

        // Baseline HRV and RHR
        LocalDate baselineStart = today.minusDays(baselineWindow);
        List<HealthMetric> hrvBaselines = repo.findHrvMetricsSince(baselineStart);
        List<HealthMetric> rhrBaselines = repo.findRhrMetricsSince(baselineStart);

        List<Double> hrvValues = hrvBaselines.stream()
                .filter(m -> m.getHrvRmssd() != null)
                .map(HealthMetric::getHrvRmssd)
                .toList();
        Double hrvMean = hrvValues.isEmpty() ? null : hrvValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        Double hrvStd = null;
        if (hrvMean != null && hrvValues.size() > 1) {
            double var = hrvValues.stream().mapToDouble(v -> Math.pow(v - hrvMean, 2)).sum() / (hrvValues.size() - 1);
            hrvStd = Math.sqrt(var);
        }

        List<Integer> rhrValues = rhrBaselines.stream()
                .filter(m -> m.getRhr() != null)
                .map(HealthMetric::getRhr)
                .toList();
        Double rhrMean = rhrValues.isEmpty() ? null : rhrValues.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        Double rhrStd = null;
        if (rhrMean != null && rhrValues.size() > 1) {
            double var = rhrValues.stream().mapToDouble(v -> Math.pow(v - rhrMean, 2)).sum() / (rhrValues.size() - 1);
            rhrStd = Math.sqrt(var);
        }

        // Fetch metrics in range
        List<HealthMetric> rangeMetrics = repo.findByDateBetweenOrderByDateDesc(start, today);
        Map<LocalDate, HealthMetric> byDate = rangeMetrics.stream()
                .collect(Collectors.toMap(HealthMetric::getDate, m -> m, (m1, m2) -> m1));

        // Weight EMA chronological calculation
        List<HealthMetric> weightHistory = repo.findLatestWeightEntries(cappedDays + 15);
        Map<LocalDate, Double> weightEmaByDate = new HashMap<>();
        if (!weightHistory.isEmpty()) {
            List<HealthMetric> chronoWeights = weightHistory.reversed();
            double ema = chronoWeights.getFirst().getWeightKg();
            weightEmaByDate.put(chronoWeights.getFirst().getDate(), Math.round(ema * 100.0) / 100.0);
            for (int i = 1; i < chronoWeights.size(); i++) {
                double w = chronoWeights.get(i).getWeightKg();
                ema = 0.25 * w + 0.75 * ema;
                weightEmaByDate.put(chronoWeights.get(i).getDate(), Math.round(ema * 100.0) / 100.0);
            }
        }

        List<HealthTimeseriesResponse.HealthDailyPoint> points = new ArrayList<>();
        LocalDate cur = start;
        while (!cur.isAfter(today)) {
            HealthMetric m = byDate.get(cur);
            Double hrv = m != null ? m.getHrvRmssd() : null;
            Integer rhr = m != null ? m.getRhr() : null;
            Integer sleepScore = m != null ? m.getSleepScore() : null;
            Integer sleepMin = m != null ? m.getSleepTotalMin() : null;
            Double sleepHours = sleepMin != null ? Math.round((sleepMin / 60.0) * 10.0) / 10.0 : null;
            Double sleepDebt = sleepHours != null ? Math.max(0.0, Math.round((sleepTarget - sleepHours) * 10.0) / 10.0) : null;
            Double deepHours = (m != null && m.getSleepDeepMin() != null) ? Math.round((m.getSleepDeepMin() / 60.0) * 10.0) / 10.0 : null;
            Double remHours = (m != null && m.getSleepRemMin() != null) ? Math.round((m.getSleepRemMin() / 60.0) * 10.0) / 10.0 : null;
            Double lightHours = (m != null && m.getSleepLightMin() != null) ? Math.round((m.getSleepLightMin() / 60.0) * 10.0) / 10.0 : null;
            Double awakeHours = (m != null && m.getSleepAwakeMin() != null) ? Math.round((m.getSleepAwakeMin() / 60.0) * 10.0) / 10.0 : null;

            Integer charged = m != null ? m.getBodyBatteryCharged() : null;
            Integer drained = m != null ? m.getBodyBatteryDrained() : null;
            Integer bbMax = m != null ? m.getBodyBatteryHighest() : null;
            Integer bbMin = m != null ? m.getBodyBatteryLowest() : null;
            Integer stress = m != null ? m.getStressAvg() : null;
            Integer steps = m != null ? m.getSteps() : null;
            Integer activeKcal = m != null ? m.getActiveKcal() : null;
            Integer totalKcal = m != null ? m.getTotalKcal() : null;
            Double weight = m != null ? m.getWeightKg() : null;
            Double weightEma = weightEmaByDate.get(cur);

            Double hrvUpper = (hrvMean != null && hrvStd != null) ? Math.round((hrvMean + hrvStd) * 10.0) / 10.0 : null;
            Double hrvLower = (hrvMean != null && hrvStd != null) ? Math.max(0.0, Math.round((hrvMean - hrvStd) * 10.0) / 10.0) : null;

            String state = null;
            if (hrv != null && hrvMean != null && hrvStd != null && hrvStd > 0) {
                double z = (hrv - hrvMean) / hrvStd;
                if (z > 0.5) state = "PRIME";
                else if (z >= -0.5) state = "NORMAL";
                else if (z >= -1.5) state = "FATIGUED";
                else state = "STRESSED";
            }

            points.add(new HealthTimeseriesResponse.HealthDailyPoint(
                    cur,
                    hrv,
                    hrvMean != null ? Math.round(hrvMean * 10.0) / 10.0 : null,
                    hrvUpper,
                    hrvLower,
                    rhr,
                    rhrMean != null ? Math.round(rhrMean * 10.0) / 10.0 : null,
                    sleepScore,
                    sleepHours,
                    sleepDebt,
                    deepHours,
                    remHours,
                    lightHours,
                    awakeHours,
                    charged,
                    drained,
                    bbMax,
                    bbMin,
                    stress,
                    steps,
                    activeKcal,
                    totalKcal,
                    weight,
                    weightEma,
                    state
            ));
            cur = cur.plusDays(1);
        }

        return new HealthTimeseriesResponse(
                cappedDays,
                hrvMean != null ? Math.round(hrvMean * 10.0) / 10.0 : null,
                hrvStd != null ? Math.round(hrvStd * 10.0) / 10.0 : null,
                rhrMean != null ? Math.round(rhrMean * 10.0) / 10.0 : null,
                rhrStd != null ? Math.round(rhrStd * 10.0) / 10.0 : null,
                targetWeight,
                sleepTarget,
                points
        );
    }

    /**
     * Trigger an on-demand real-time pull from Garmin Connect via the puller sidecar.
     *
     * @return true if successful, false otherwise
     */
    public boolean triggerGarminPull() {
        try {
            String apiKey = (properties != null && properties.security() != null)
                    ? properties.security().apiKey()
                    : "";
            log.info("Triggering Garmin pull on :8081 (API key configured: {})", apiKey != null && !apiKey.isBlank());
            restClient.post()
                    .uri("http://garmin-puller:8081/pull")
                    .header("X-API-Key", apiKey != null ? apiKey.trim() : "")
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
