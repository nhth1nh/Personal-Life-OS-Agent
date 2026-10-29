package com.ploa.health;

import com.ploa.health.api.dto.HealthBatchSyncResponse;
import com.ploa.health.api.dto.HealthSyncRequest;
import com.ploa.health.api.dto.HealthTimeseriesResponse;
import com.ploa.health.model.HealthMetric;
import com.ploa.health.repository.HealthMetricRepository;
import com.ploa.health.service.HealthService;
import com.ploa.profile.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class HealthTimeseriesTest {

    private HealthMetricRepository repo;
    private UserProfileService profileService;
    private HealthService healthService;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(HealthMetricRepository.class);
        profileService = Mockito.mock(UserProfileService.class);

        when(profileService.getTargetWeightKg()).thenReturn(68.0);
        when(profileService.getSleepTargetHours()).thenReturn(8.0);
        when(profileService.getBaselineWindowDays()).thenReturn(30);

        healthService = new HealthService(repo, null, null, null, null, profileService);
    }

    @Test
    @DisplayName("getTimeseries returns chronological daily points with valid baselines and recovery states")
    void getTimeseries_success() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        HealthMetric m1 = new HealthMetric();
        m1.setDate(yesterday);
        m1.setHrvRmssd(65.0);
        m1.setRhr(52);
        m1.setSleepTotalMin(480);
        m1.setWeightKg(67.5);
        m1.setSteps(9500);

        HealthMetric m2 = new HealthMetric();
        m2.setDate(today);
        m2.setHrvRmssd(75.0);
        m2.setRhr(50);
        m2.setSleepTotalMin(450);
        m2.setWeightKg(67.2);
        m2.setSteps(11000);

        when(repo.findHrvMetricsSince(any())).thenReturn(List.of(m2, m1));
        when(repo.findRhrMetricsSince(any())).thenReturn(List.of(m2, m1));
        when(repo.findByDateBetweenOrderByDateDesc(any(), eq(today))).thenReturn(List.of(m2, m1));
        when(repo.findLatestWeightEntries(any(Integer.class))).thenReturn(List.of(m2, m1));

        HealthTimeseriesResponse res = healthService.getTimeseries(7);

        assertThat(res.days()).isEqualTo(7);
        assertThat(res.targetWeightKg()).isEqualTo(68.0);
        assertThat(res.sleepTargetHours()).isEqualTo(8.0);
        assertThat(res.baselineHrvMean()).isNotNull();
        assertThat(res.baselineRhrMean()).isNotNull();
        assertThat(res.points()).hasSize(7);

        // Check chronological order (oldest to newest)
        assertThat(res.points().getFirst().date()).isEqualTo(today.minusDays(6));
        assertThat(res.points().getLast().date()).isEqualTo(today);

        var todayPoint = res.points().getLast();
        assertThat(todayPoint.hrvRmssd()).isEqualTo(75.0);
        assertThat(todayPoint.rhr()).isEqualTo(50);
        assertThat(todayPoint.sleepDurationHours()).isEqualTo(7.5);
        assertThat(todayPoint.sleepDebtHours()).isEqualTo(0.5);
        assertThat(todayPoint.steps()).isEqualTo(11000);
    }

    @Test
    @DisplayName("getTimeseries supports custom dynamic date range")
    void getTimeseries_dynamicRange_success() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 5);

        when(repo.findHrvMetricsSince(any())).thenReturn(List.of());
        when(repo.findRhrMetricsSince(any())).thenReturn(List.of());
        when(repo.findByDateBetweenOrderByDateDesc(start, end)).thenReturn(List.of());
        when(repo.findLatestWeightEntries(any(Integer.class))).thenReturn(List.of());

        HealthTimeseriesResponse res = healthService.getTimeseries(start, end);

        assertThat(res.days()).isEqualTo(5);
        assertThat(res.points()).hasSize(5);
        assertThat(res.points().getFirst().date()).isEqualTo(start);
        assertThat(res.points().getLast().date()).isEqualTo(end);
    }

    @Test
    @DisplayName("upsertBatch persists dynamic range list and returns summary response")
    void upsertBatch_success() {
        LocalDate d1 = LocalDate.of(2026, 10, 10);
        LocalDate d2 = LocalDate.of(2026, 10, 11);

        HealthSyncRequest r1 = new HealthSyncRequest(
                d1, 70.0, null, 50, null, null, 480, 100, 100, 200, 80, 85,
                null, null, null, null, null, null, null, null, 8000, 500, 2200,
                null, null, null, null, null, null, 68.0, null
        );
        HealthSyncRequest r2 = new HealthSyncRequest(
                d2, 72.0, null, 49, null, null, 500, 120, 110, 220, 50, 90,
                null, null, null, null, null, null, null, null, 10000, 600, 2300,
                null, null, null, null, null, null, 67.8, null
        );

        when(repo.findById(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        HealthBatchSyncResponse batchRes = healthService.upsertBatch(List.of(r1, r2));

        assertThat(batchRes.status()).isEqualTo("OK");
        assertThat(batchRes.syncedCount()).isEqualTo(2);
        assertThat(batchRes.startDate()).isEqualTo(d1);
        assertThat(batchRes.endDate()).isEqualTo(d2);
    }
}
