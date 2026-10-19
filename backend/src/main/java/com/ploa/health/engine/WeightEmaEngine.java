package com.ploa.health.engine;

import com.ploa.config.PloaProperties;
import com.ploa.health.engine.dto.WeightEmaResult;
import com.ploa.health.model.HealthMetric;
import com.ploa.health.repository.HealthMetricRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic Weight EMA-7 engine.
 * EMA_t = α * W_t + (1 - α) * EMA_(t-1), where α = 2 / (N + 1), N = 7
 * Filters out daily water/glycogen fluctuations to show real body mass trend.
 */
@Component
public class WeightEmaEngine {

    private static final int EMA_PERIOD = 7;
    private static final double ALPHA = 2.0 / (EMA_PERIOD + 1); // 0.25

    private final HealthMetricRepository repo;
    private final PloaProperties properties;

    public WeightEmaEngine(HealthMetricRepository repo, PloaProperties properties) {
        this.repo = repo;
        this.properties = properties;
    }

    public WeightEmaResult compute() {
        // Fetch last 30 entries with weight (newest first)
        List<HealthMetric> entries = repo.findLatestWeightEntries(30);

        if (entries.isEmpty()) {
            return new WeightEmaResult(null, null, null,
                    properties.health().targetWeightKg(), null, null);
        }

        // Reverse to chronological order for EMA calculation
        List<HealthMetric> chronological = entries.reversed();

        // Seed EMA with first weight value
        double ema = chronological.getFirst().getWeightKg();

        for (int i = 1; i < chronological.size(); i++) {
            double w = chronological.get(i).getWeightKg();
            ema = ALPHA * w + (1 - ALPHA) * ema;
        }

        // Compute weekly trend: compare current EMA to EMA from 7 entries ago
        Double weeklyTrend = null;
        if (chronological.size() >= 8) {
            double oldEma = chronological.get(chronological.size() - 8).getWeightKg();
            weeklyTrend = round2dp(ema - oldEma);
        }

        HealthMetric latest = entries.getFirst(); // newest
        double targetKg = properties.health().targetWeightKg();

        return new WeightEmaResult(
                latest.getDate(),
                round2dp(latest.getWeightKg()),
                round2dp(ema),
                targetKg,
                round2dp(ema - targetKg),
                weeklyTrend
        );
    }

    private double round2dp(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
