package com.ploa.finance;

import com.ploa.assistant.memory.UserMemoryRepository;
import com.ploa.finance.engine.CashAwareAllowanceEngine;
import com.ploa.finance.engine.dto.CashFlowSummary;
import com.ploa.finance.engine.dto.LiquidityStatus;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.repository.DebtScheduleRepository;
import com.ploa.finance.repository.FinancialAccountRepository;
import com.ploa.finance.repository.IncomeRepository;
import com.ploa.finance.repository.TransactionRepository;
import com.ploa.profile.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class CashAwareAllowanceEngineTest {

    private FinancialAccountRepository accountRepo;
    private IncomeRepository incomeRepo;
    private DebtScheduleRepository debtScheduleRepo;
    private TransactionRepository transactionRepo;
    private UserProfileService profileService;
    private CashAwareAllowanceEngine engine;

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @BeforeEach
    void setUp() {
        accountRepo = Mockito.mock(FinancialAccountRepository.class);
        incomeRepo = Mockito.mock(IncomeRepository.class);
        debtScheduleRepo = Mockito.mock(DebtScheduleRepository.class);
        transactionRepo = Mockito.mock(TransactionRepository.class);
        UserMemoryRepository memoryRepo = Mockito.mock(UserMemoryRepository.class);
        profileService = new UserProfileService(memoryRepo);

        engine = new CashAwareAllowanceEngine(
                accountRepo,
                incomeRepo,
                debtScheduleRepo,
                transactionRepo,
                profileService
        );
    }

    @Test
    @DisplayName("Should evaluate SOLVENT state and calculate safe daily allowance accurately")
    void testSolventState() {
        // Evaluate on 15th of month at 10:00 (before 18:00, in a 30-day month -> 16 days remaining)
        ZonedDateTime evalTime = ZonedDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZONE);

        when(accountRepo.sumLiquidCashBalances()).thenReturn(BigDecimal.valueOf(10_000_000));
        when(incomeRepo.sumExpectedInflowsBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(40_000_000)); // Expected salary before month-end
        when(debtScheduleRepo.sumUpcomingObligationsBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(5_000_000)); // Unpaid loan payment
        when(incomeRepo.sumReceivedIncomeBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.valueOf(5_000_000));
        when(transactionRepo.sumOperatingSpendBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(3_000_000L);
        when(transactionRepo.sumDebtPrincipalPaidBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(2_000_000L);

        BigDecimal unpaidFixedBills = BigDecimal.valueOf(5_000_000);
        BigDecimal emergencyReserve = BigDecimal.valueOf(5_000_000);

        // Spendable = 10M + 40M - 5M (debt) - 5M (fixed) - 5M (reserve) = 35,000,000 VND
        // Days remaining = 30 - 15 + 1 = 16 days
        // Safe daily allowance = 35,000,000 / 16 = 2,187,500 VND/day
        CashFlowSummary summary = engine.evaluate(evalTime, unpaidFixedBills, emergencyReserve);

        assertThat(summary.liquidityStatus()).isEqualTo(LiquidityStatus.SOLVENT);
        assertThat(summary.isSolvent()).isTrue();
        assertThat(summary.isDeficit()).isFalse();
        assertThat(summary.spendableCash()).isEqualByComparingTo(BigDecimal.valueOf(35_000_000));
        assertThat(summary.daysRemainingInMonth()).isEqualTo(16);
        assertThat(summary.safeDailyAllowance()).isEqualByComparingTo(BigDecimal.valueOf(2_187_500.00));

        // Invariant: Net Cash Flow = Income (5M) - Operating (3M) - Principal (2M) = 0
        assertThat(summary.netCashFlowMtd()).isEqualByComparingTo(BigDecimal.ZERO);
        // Invariant: Net Savings = Income (5M) - Operating (3M) = 2,000,000 VND
        assertThat(summary.netSavingsMtd()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
    }

    @Test
    @DisplayName("Should detect PROJECTED_SHORTFALL when solvent today but future obligations exceed cash")
    void testProjectedShortfall() {
        ZonedDateTime evalTime = ZonedDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZONE);

        // Liquid cash = 10M, no further inflows expected, unpaid debt before month-end = 15M
        // Spendable = 10M - 15M = -5,000,000 VND
        when(accountRepo.sumLiquidCashBalances()).thenReturn(BigDecimal.valueOf(10_000_000));
        when(incomeRepo.sumExpectedInflowsBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
        when(debtScheduleRepo.sumUpcomingObligationsBetween(eq(LocalDate.of(2026, 4, 15)), eq(LocalDate.of(2026, 4, 30))))
                .thenReturn(BigDecimal.valueOf(15_000_000));
        // Next 7 days obligations: only 2M due this week -> liquid cash 10M covers it!
        when(debtScheduleRepo.sumUpcomingObligationsBetween(eq(LocalDate.of(2026, 4, 15)), eq(LocalDate.of(2026, 4, 22))))
                .thenReturn(BigDecimal.valueOf(2_000_000));

        CashFlowSummary summary = engine.evaluate(evalTime, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(summary.liquidityStatus()).isEqualTo(LiquidityStatus.PROJECTED_SHORTFALL);
        assertThat(summary.safeDailyAllowance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.spendableCash()).isEqualByComparingTo(BigDecimal.valueOf(-5_000_000));
        assertThat(summary.recommendation()).contains("PROJECTED SHORTFALL");
    }

    @Test
    @DisplayName("Should raise LIQUIDITY_DEFICIT when liquid cash cannot cover next 7 days of obligations")
    void testLiquidityDeficit() {
        ZonedDateTime evalTime = ZonedDateTime.of(2026, 4, 15, 10, 0, 0, 0, ZONE);

        // Liquid cash = 1M, upcoming 7-day debt = 5M -> immediate crisis!
        when(accountRepo.sumLiquidCashBalances()).thenReturn(BigDecimal.valueOf(1_000_000));
        when(incomeRepo.sumExpectedInflowsBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(BigDecimal.ZERO);
        when(debtScheduleRepo.sumUpcomingObligationsBetween(eq(LocalDate.of(2026, 4, 15)), eq(LocalDate.of(2026, 4, 30))))
                .thenReturn(BigDecimal.valueOf(15_000_000));
        when(debtScheduleRepo.sumUpcomingObligationsBetween(eq(LocalDate.of(2026, 4, 15)), eq(LocalDate.of(2026, 4, 22))))
                .thenReturn(BigDecimal.valueOf(5_000_000));

        CashFlowSummary summary = engine.evaluate(evalTime, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(summary.liquidityStatus()).isEqualTo(LiquidityStatus.LIQUIDITY_DEFICIT);
        assertThat(summary.isDeficit()).isTrue();
        assertThat(summary.safeDailyAllowance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.recommendation()).contains("CRITICAL LIQUIDITY DEFICIT");
    }

    @Test
    @DisplayName("Temporal Grounding: evaluation after 18:00 should exclude current day from remaining days")
    void testTemporalGroundingAfter18h() {
        // Evaluated at 19:30 on day 15 of a 30-day month -> 30 - 15 = 15 days remaining
        ZonedDateTime evalTime = ZonedDateTime.of(2026, 4, 15, 19, 30, 0, 0, ZONE);

        when(accountRepo.sumLiquidCashBalances()).thenReturn(BigDecimal.valueOf(15_000_000));
        when(incomeRepo.sumExpectedInflowsBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(BigDecimal.ZERO);
        when(debtScheduleRepo.sumUpcomingObligationsBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(BigDecimal.ZERO);

        CashFlowSummary summary = engine.evaluate(evalTime, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(summary.daysRemainingInMonth()).isEqualTo(15);
        assertThat(summary.safeDailyAllowance()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000.00));
    }
}
