package com.ploa.finance;

import com.ploa.assistant.memory.UserMemory;
import com.ploa.assistant.memory.UserMemoryRepository;
import com.ploa.finance.engine.BurnRateEngine;
import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.repository.TransactionRepository;
import com.ploa.profile.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class BurnRateEngineTest {

    private TransactionRepository repo;
    private UserMemoryRepository memoryRepo;
    private UserProfileService profileService;
    private BurnRateEngine engine;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(TransactionRepository.class);
        memoryRepo = Mockito.mock(UserMemoryRepository.class);
        profileService = new UserProfileService(memoryRepo);
        engine = new BurnRateEngine(repo, profileService);
    }

    @Test
    @DisplayName("Should compute burn rate result using dynamic monthly budget from UserProfileService")
    void testComputeBurnRate() {
        when(memoryRepo.findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase("FINANCE", "monthly_budget_vnd"))
                .thenReturn(Optional.of(new UserMemory("FINANCE", "monthly_budget_vnd", "15000000", "CONVERSATION")));
        when(repo.sumConfirmedAmountBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(3_000_000L);
        when(repo.sumBySpendTypeBetween(any(LocalDate.class), any(LocalDate.class), eq(SpendType.DISCRETIONARY))).thenReturn(2_000_000L);
        when(repo.sumBySpendTypeBetween(any(LocalDate.class), any(LocalDate.class), eq(SpendType.FIXED))).thenReturn(1_000_000L);
        when(repo.sumByCategoryBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(Collections.emptyList());

        BurnRateResult result = engine.compute();

        assertThat(result.monthlyBudgetVnd()).isEqualTo(15_000_000L);
        assertThat(result.mtdTotalSpendVnd()).isEqualTo(3_000_000L);
        assertThat(result.mtdDiscretionaryVnd()).isEqualTo(2_000_000L);
        assertThat(result.mtdFixedVnd()).isEqualTo(1_000_000L);
        assertThat(result.remainingBudgetVnd()).isEqualTo(12_000_000L);
        assertThat(result.isOverBudget()).isFalse();
        assertThat(result.monthStart()).isNotNull();
        assertThat(result.today()).isNotNull();
        assertThat(result.daysElapsed()).isGreaterThan(0);
        assertThat(result.daysRemaining()).isGreaterThan(0);
    }
}
