package com.ploa.health.engine;

import com.ploa.health.engine.dto.ReadinessLevel;
import com.ploa.health.engine.dto.ReadinessResult;
import com.ploa.health.model.HealthMetric;
import com.ploa.health.repository.HealthMetricRepository;
import com.ploa.profile.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Deterministic readiness engine.
 * Computes 30-day rolling Z-scores for HRV rMSSD and RHR,
 * classifies nervous system state, and evaluates sleep architecture.
 * The LLM never touches these calculations.
 */
@Component
public class ReadinessEngine {

    private static final Logger log = LoggerFactory.getLogger(ReadinessEngine.class);

    private final HealthMetricRepository repo;
    private final UserProfileService profileService;

    public ReadinessEngine(HealthMetricRepository repo, UserProfileService profileService) {
        this.repo = repo;
        this.profileService = profileService;
    }

    public ReadinessResult compute(LocalDate targetDate) {
        int windowDays = profileService.getBaselineWindowDays();
        LocalDate windowStart = targetDate.minusDays(windowDays);

        // ── Fetch baseline data ───────────────────────────────────────────────
        List<HealthMetric> hrvData = repo.findHrvMetricsSince(windowStart);
        List<HealthMetric> rhrData = repo.findRhrMetricsSince(windowStart);
        List<HealthMetric> sleepData = repo.findByDateBetweenOrderByDateDesc(
                targetDate.minusDays(7), targetDate);

        // ── Today's values ────────────────────────────────────────────────────
        HealthMetric today = repo.findById(targetDate).orElse(null);
        Double todayHrv = today != null ? today.getHrvRmssd() : null;
        Integer todayRhr = today != null ? today.getRhr() : null;
        Integer sleepTotalMin = today != null ? today.getSleepTotalMin() : null;
        Integer sleepDeepMin = today != null ? today.getSleepDeepMin() : null;
        Integer sleepRemMin = today != null ? today.getSleepRemMin() : null;

        // ── Z-Score: HRV ──────────────────────────────────────────────────────
        List<Double> hrvValues = hrvData.stream()
                .filter(m -> m.getHrvRmssd() != null)
                .map(HealthMetric::getHrvRmssd)
                .toList();
        Double hrv30dMean = mean(hrvValues);
        Double hrv30dStd = stdDev(hrvValues, hrv30dMean);
        Double hrvZ = zScore(todayHrv, hrv30dMean, hrv30dStd);

        // ── Z-Score: RHR ──────────────────────────────────────────────────────
        List<Double> rhrValues = rhrData.stream()
                .filter(m -> m.getRhr() != null)
                .map(m -> (double) m.getRhr())
                .toList();
        Double rhr30dMean = mean(rhrValues);
        Double rhr30dStd = stdDev(rhrValues, rhr30dMean);
        Double rhrZ = zScore(todayRhr != null ? todayRhr.doubleValue() : null, rhr30dMean, rhr30dStd);

        // ── Sleep Architecture ────────────────────────────────────────────────
        int sleepTargetMin = (int) (profileService.getSleepTargetHours() * 60);
        int sleepDebt = computeSleepDebt(sleepData, sleepTargetMin);
        Double restorativeRatio = computeRestorativeRatio(sleepTotalMin, sleepDeepMin, sleepRemMin);

        // ── Classify readiness ────────────────────────────────────────────────
        ReadinessLevel level = classify(hrvZ, rhrZ);
        String summary = buildSummary(level, hrvZ, rhrZ, sleepDebt, sleepTotalMin, sleepTargetMin);

        log.debug("Readiness for {}: hrv_z={}, rhr_z={}, level={}", targetDate, hrvZ, rhrZ, level);

        return new ReadinessResult(
                targetDate,
                todayHrv, hrv30dMean, hrv30dStd, hrvZ,
                todayRhr, rhr30dMean, rhr30dStd, rhrZ,
                sleepTotalMin, sleepDeepMin, sleepRemMin,
                sleepDebt, restorativeRatio,
                level, summary
        );
    }

    // ── Private math helpers ─────────────────────────────────────────────────

    private Double mean(List<Double> values) {
        if (values.isEmpty()) return null;
        OptionalDouble avg = values.stream().mapToDouble(Double::doubleValue).average();
        return avg.isPresent() ? avg.getAsDouble() : null;
    }

    private Double stdDev(List<Double> values, Double mean) {
        if (values.size() < 2 || mean == null) return null;
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    private Double zScore(Double value, Double mean, Double std) {
        if (value == null || mean == null || std == null || std == 0.0) return null;
        return (value - mean) / std;
    }

    private ReadinessLevel classify(Double hrvZ, Double rhrZ) {
        // Use combined Z (HRV positive = good, RHR positive = bad, so invert RHR)
        double combined = 0.0;
        int count = 0;
        if (hrvZ != null) { combined += hrvZ; count++; }
        if (rhrZ != null) { combined -= rhrZ; count++; }  // invert RHR
        if (count == 0) return ReadinessLevel.NORMAL;
        combined /= count;

        if (combined > 0.5) return ReadinessLevel.OPTIMAL;
        if (combined >= -0.5) return ReadinessLevel.NORMAL;
        if (combined >= -1.0) return ReadinessLevel.FATIGUED;
        return ReadinessLevel.STRESSED;
    }

    private int computeSleepDebt(List<HealthMetric> last7days, int targetMin) {
        return last7days.stream()
                .filter(m -> m.getSleepTotalMin() != null)
                .mapToInt(m -> Math.max(0, targetMin - m.getSleepTotalMin()))
                .sum();
    }

    private Double computeRestorativeRatio(Integer total, Integer deep, Integer rem) {
        if (total == null || total == 0 || (deep == null && rem == null)) return null;
        int restorative = (deep != null ? deep : 0) + (rem != null ? rem : 0);
        return (restorative * 100.0) / total;
    }

    private String buildSummary(ReadinessLevel level, Double hrvZ, Double rhrZ,
                                int sleepDebt, Integer sleepTotalMin, int sleepTargetMin) {
        return switch (level) {
            case OPTIMAL -> "Nervous system well-recovered. High-intensity training recommended.";
            case NORMAL -> "Adequate recovery. Moderate intensity training appropriate.";
            case FATIGUED -> "Signs of fatigue detected. Reduce training intensity.";
            case STRESSED -> "High physiological stress. Prioritize rest and recovery.";
        };
    }
}
