package com.ploa.health.engine;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ploa.health.engine.dto.SleepResult;
import com.ploa.health.model.HealthMetric;
import com.ploa.health.repository.HealthMetricRepository;

@Component 
public class SleepEngine {
    
    private final HealthMetricRepository repo;

    public SleepEngine(HealthMetricRepository repo) {
        this.repo = repo;
    }

    public SleepResult compute() {
        List<HealthMetric> sleepEntries = repo.findLatestSleepEntries(7);
        // Compute sleep result based on the retrieved sleep entries
        HealthMetric latestSleepEntry = sleepEntries.isEmpty() ? null : sleepEntries.get(0);
        String weaklyTrend = null;
        if (!sleepEntries.isEmpty()) {
            // Compute weekly trend based on the last 7 sleep entries
            int totalMinutes = 0;
            for (HealthMetric entry : sleepEntries) {
                totalMinutes += entry.getSleepTotalMin();
            }
            int averageMinutes = totalMinutes / sleepEntries.size();
            weaklyTrend = formatSleepDuration(averageMinutes);
        }
        SleepResult result = new SleepResult(
            latestSleepEntry == null ? "00:00" : formatSleepDuration(latestSleepEntry.getSleepTotalMin()),
            latestSleepEntry == null ? "00:00" : formatSleepDuration(latestSleepEntry.getSleepDeepMin()),
            latestSleepEntry == null ? "00:00" : formatSleepDuration(latestSleepEntry.getSleepLightMin()),
            latestSleepEntry == null ? "00:00" : formatSleepDuration(latestSleepEntry.getSleepRemMin()),
            weaklyTrend
        );
        return result;
    }
    private String formatSleepDuration(Integer totalMin) {
        if (totalMin == null) return "00:00";
        int hours = totalMin / 60;
        int minutes = totalMin % 60;
        return String.format("%02d:%02d", hours, minutes);
    }
}
