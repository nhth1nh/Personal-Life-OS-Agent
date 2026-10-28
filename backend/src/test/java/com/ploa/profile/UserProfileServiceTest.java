package com.ploa.profile;

import com.ploa.assistant.memory.UserMemory;
import com.ploa.assistant.memory.UserMemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserProfileServiceTest {

    private UserMemoryRepository repo;
    private UserProfileService service;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(UserMemoryRepository.class);
        service = new UserProfileService(repo);
    }

    @Test
    @DisplayName("Should return default values when user_memories is empty")
    void testDefaultsWhenEmpty() {
        when(repo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase(any(), any())).thenReturn(Optional.empty());

        assertThat(service.getTargetWeightKg()).isEqualTo(UserProfileService.DEFAULT_TARGET_WEIGHT_KG);
        assertThat(service.getSleepTargetHours()).isEqualTo(UserProfileService.DEFAULT_SLEEP_TARGET_HOURS);
        assertThat(service.getBaselineWindowDays()).isEqualTo(UserProfileService.DEFAULT_BASELINE_WINDOW_DAYS);
        assertThat(service.getMonthlyBudgetVnd()).isEqualTo(UserProfileService.DEFAULT_MONTHLY_BUDGET_VND);
    }

    @Test
    @DisplayName("Should return stored values when present in database")
    void testStoredValues() {
        when(repo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase("FINANCE", "monthly_budget_vnd"))
                .thenReturn(Optional.of(new UserMemory("FINANCE", "monthly_budget_vnd", "15000000", "CONVERSATION")));
        when(repo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase("HEALTH", "target_weight_kg"))
                .thenReturn(Optional.of(new UserMemory("HEALTH", "target_weight_kg", "64.5", "CONVERSATION")));
        when(repo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase("HEALTH", "sleep_target_hours"))
                .thenReturn(Optional.of(new UserMemory("HEALTH", "sleep_target_hours", "8.0", "CONVERSATION")));

        assertThat(service.getMonthlyBudgetVnd()).isEqualTo(15_000_000L);
        assertThat(service.getTargetWeightKg()).isEqualTo(64.5);
        assertThat(service.getSleepTargetHours()).isEqualTo(8.0);
    }

    @Test
    @DisplayName("Should parse various currency and shorthand formats for /init args")
    void testParseArgsKeywords() {
        UserProfileService.ParsedProfileUpdate parsed = service.parseArgs("budget 12M weight 66.5kg sleep 8h");

        assertThat(parsed.monthlyBudgetVnd()).isEqualTo(12_000_000L);
        assertThat(parsed.targetWeightKg()).isEqualTo(66.5);
        assertThat(parsed.sleepTargetHours()).isEqualTo(8.0);
        assertThat(parsed.hasAny()).isTrue();
    }

    @Test
    @DisplayName("Should parse Vietnamese phrasing in /init args")
    void testParseArgsVietnamese() {
        UserProfileService.ParsedProfileUpdate parsed = service.parseArgs("ngân sách: 15tr cân nặng: 65 ngủ: 7.5 tiếng");

        assertThat(parsed.monthlyBudgetVnd()).isEqualTo(15_000_000L);
        assertThat(parsed.targetWeightKg()).isEqualTo(65.0);
        assertThat(parsed.sleepTargetHours()).isEqualTo(7.5);
    }

    @Test
    @DisplayName("Should parse token heuristics without explicit keywords")
    void testParseArgsTokens() {
        UserProfileService.ParsedProfileUpdate parsed = service.parseArgs("20M 63kg 8h");

        assertThat(parsed.monthlyBudgetVnd()).isEqualTo(20_000_000L);
        assertThat(parsed.targetWeightKg()).isEqualTo(63.0);
        assertThat(parsed.sleepTargetHours()).isEqualTo(8.0);
    }
}
