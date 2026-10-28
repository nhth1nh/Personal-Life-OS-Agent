package com.ploa.finance;

import com.ploa.finance.api.dto.FinanceAnalyticsResponse;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.repository.OcrAuditRepository;
import com.ploa.finance.repository.TransactionRepository;
import com.ploa.finance.service.FinanceService;
import com.ploa.profile.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class FinanceAnalyticsTest {

    private TransactionRepository repo;
    private UserProfileService profileService;
    private FinanceService financeService;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(TransactionRepository.class);
        OcrAuditRepository auditRepo = Mockito.mock(OcrAuditRepository.class);
        profileService = Mockito.mock(UserProfileService.class);

        when(profileService.getMonthlyBudgetVnd()).thenReturn(15_000_000L);

        financeService = new FinanceService(repo, auditRepo, null, profileService);
    }

    @Test
    @DisplayName("getAnalytics returns budget, burn rate, safe daily allowance, and category breakdown")
    void getAnalytics_success() {
        LocalDate day1 = LocalDate.of(2026, 10, 5);
        LocalDate day2 = LocalDate.of(2026, 10, 10);

        Transaction t1 = new Transaction();
        t1.setAmount(500_000L);
        t1.setSpendType(SpendType.FIXED);
        t1.setCategory("Housing");
        t1.setTxDate(day1);
        t1.setConfirmed(true);

        Transaction t2 = new Transaction();
        t2.setAmount(100_000L);
        t2.setSpendType(SpendType.DISCRETIONARY);
        t2.setCategory("Food & Dining");
        t2.setTxDate(day2);
        t2.setConfirmed(true);

        when(repo.sumConfirmedAmountBetween(any(), any())).thenReturn(600_000L);
        when(repo.sumBySpendTypeBetween(any(), any(), eq(SpendType.FIXED))).thenReturn(500_000L);
        when(repo.sumBySpendTypeBetween(any(), any(), eq(SpendType.DISCRETIONARY))).thenReturn(100_000L);

        List<Object[]> catSums = List.of(
                new Object[]{"Housing", 500_000L},
                new Object[]{"Food & Dining", 100_000L}
        );
        when(repo.sumByCategoryBetween(any(), any())).thenReturn(catSums);
        when(repo.findByTxDateBetweenOrderByTxDateDesc(any(), any())).thenReturn(List.of(t2, t1));

        FinanceAnalyticsResponse res = financeService.getAnalytics("2026-10");

        assertThat(res.month()).isEqualTo("2026-10");
        assertThat(res.monthlyBudgetVnd()).isEqualTo(15_000_000L);
        assertThat(res.totalSpendVnd()).isEqualTo(600_000L);
        assertThat(res.remainingBudgetVnd()).isEqualTo(14_400_000L);
        assertThat(res.fixedSpendVnd()).isEqualTo(500_000L);
        assertThat(res.discretionarySpendVnd()).isEqualTo(100_000L);

        // Check categories
        assertThat(res.categories()).hasSize(2);
        assertThat(res.categories().getFirst().category()).isEqualTo("Housing");
        assertThat(res.categories().getFirst().totalVnd()).isEqualTo(500_000L);
        assertThat(res.categories().getFirst().percentage()).isEqualTo(83.3);

        // Check daily list exists
        assertThat(res.dailySpending()).isNotEmpty();
    }
}
