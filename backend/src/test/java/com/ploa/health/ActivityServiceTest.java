package com.ploa.health;

import com.ploa.health.api.dto.ActivitySummaryResponse;
import com.ploa.health.api.dto.ActivitySyncRequest;
import com.ploa.health.model.Activity;
import com.ploa.health.repository.ActivityRepository;
import com.ploa.health.service.ActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ActivityServiceTest {

    private ActivityRepository repo;
    private ActivityService activityService;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(ActivityRepository.class);
        activityService = new ActivityService(repo);
    }

    @Test
    @DisplayName("upsert should populate activity attributes and persist via repository")
    void testUpsertActivity() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        ActivitySyncRequest req = new ActivitySyncRequest(
                "garmin-12345",
                "Badminton Match",
                "badminton",
                OffsetDateTime.parse("2026-10-28T19:30:00+07:00"),
                date,
                3600.0,
                3400.0,
                null,
                450,
                135,
                175,
                3.2,
                2.1,
                "TEMPO",
                85.0,
                null,
                null,
                null,
                null,
                4200,
                25,
                35,
                -18,
                null,
                null,
                null
        );

        when(repo.findById("garmin-12345")).thenReturn(Optional.empty());
        when(repo.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        Activity saved = activityService.upsert(req);

        assertThat(saved.getId()).isEqualTo("garmin-12345");
        assertThat(saved.getName()).isEqualTo("Badminton Match");
        assertThat(saved.getActivityType()).isEqualTo("badminton");
        assertThat(saved.getDurationSeconds()).isEqualTo(3600.0);
        assertThat(saved.getCalories()).isEqualTo(450);
        assertThat(saved.getAverageHr()).isEqualTo(135);
        assertThat(saved.getMaxHr()).isEqualTo(175);
        assertThat(saved.getAerobicTrainingEffect()).isEqualTo(3.2);
        assertThat(saved.getAnaerobicTrainingEffect()).isEqualTo(2.1);
        assertThat(saved.getTrainingEffectLabel()).isEqualTo("TEMPO");
    }

    @Test
    @DisplayName("upsertBatch should process all valid requests")
    void testUpsertBatch() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        ActivitySyncRequest req1 = new ActivitySyncRequest(
                "act-1", "Morning Run", "running", OffsetDateTime.now(), date,
                1800.0, 1800.0, 4000.0, 320, 142, 168, 2.8, 0.5, "AEROBIC_BASE",
                45.0, 2.22, 3.1, 20.0, 20.0, 3500, 10, 20, -10, null, null, null
        );
        ActivitySyncRequest req2 = new ActivitySyncRequest(
                "act-2", "Evening Badminton", "badminton", OffsetDateTime.now(), date,
                3600.0, 3400.0, null, 500, 130, 172, 3.0, 1.8, "TEMPO",
                70.0, null, null, null, null, 4000, 20, 40, -15, null, null, null
        );

        when(repo.findById(any())).thenReturn(Optional.empty());
        when(repo.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = activityService.upsertBatch(List.of(req1, req2));
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("getWorkoutSummaryText formats multi-workout deterministic text")
    void testWorkoutSummaryText() {
        LocalDate date = LocalDate.of(2026, 10, 28);

        Activity a1 = new Activity();
        a1.setId("act-1");
        a1.setName("Interval Run");
        a1.setDurationSeconds(1800.0);
        a1.setCalories(280);
        a1.setAverageHr(150);
        a1.setMaxHr(178);
        a1.setAerobicTrainingEffect(3.4);
        a1.setAnaerobicTrainingEffect(2.2);

        when(repo.findByDateOrderByStartTimeDesc(date)).thenReturn(List.of(a1));

        String summary = activityService.getWorkoutSummaryText(date);
        assertThat(summary).contains("1 workout(s)");
        assertThat(summary).contains("Interval Run");
        assertThat(summary).contains("30 min");
        assertThat(summary).contains("280 kcal");
        assertThat(summary).contains("avg HR 150 bpm");
        assertThat(summary).contains("max HR 178 bpm");
        assertThat(summary).contains("TE: 3.4 aero/2.2 anaero");
    }

    @Test
    @DisplayName("getWorkoutSummaryText handles empty workouts")
    void testWorkoutSummaryText_empty() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        when(repo.findByDateOrderByStartTimeDesc(date)).thenReturn(List.of());

        String summary = activityService.getWorkoutSummaryText(date);
        assertThat(summary).isEqualTo("No recorded workouts.");
    }
}
