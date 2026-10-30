package com.ploa.finance;

import com.ploa.finance.engine.DebtAmortizationEngine;
import com.ploa.finance.engine.dto.DebtAmortizationResult;
import com.ploa.finance.engine.dto.PayoffSimulationComparison;
import com.ploa.finance.engine.dto.AprAnalysisResult;
import com.ploa.finance.engine.dto.PayoffStrategy;
import com.ploa.finance.model.Debt;
import com.ploa.finance.model.DebtStatus;
import com.ploa.finance.model.DebtType;
import com.ploa.finance.model.InterestAccrualConvention;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DebtAmortizationEngineTest {

    private DebtAmortizationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DebtAmortizationEngine();
    }

    @Test
    @DisplayName("Should compute 0% interest loan payment as simple division")
    void testZeroInterestLoanPayment() {
        BigDecimal principal = BigDecimal.valueOf(12_000_000);
        BigDecimal apr = BigDecimal.ZERO;
        int months = 12;

        BigDecimal payment = engine.calculateAnnuityMonthlyPayment(principal, apr, months);
        assertThat(payment).isEqualByComparingTo(BigDecimal.valueOf(1_000_000.00));
    }

    @Test
    @DisplayName("Should compute annuity monthly payment accurately for positive APR")
    void testAnnuityMonthlyPaymentPositiveApr() {
        // Loan: 100,000,000 VND at 12% APR for 12 months
        // Standard formula: M = 100M * (0.01 * 1.01^12) / (1.01^12 - 1) ≈ 8,884,878.87 VND
        BigDecimal principal = BigDecimal.valueOf(100_000_000);
        BigDecimal apr = BigDecimal.valueOf(0.12); // 12%
        int months = 12;

        BigDecimal payment = engine.calculateAnnuityMonthlyPayment(principal, apr, months);
        assertThat(payment).isBetween(
                BigDecimal.valueOf(8_884_000),
                BigDecimal.valueOf(8_885_000)
        );
    }

    @Test
    @DisplayName("Should generate complete contractual schedule amortizing balance to exactly zero")
    void testGenerateContractualSchedule() {
        Debt debt = new Debt();
        debt.setId(UUID.randomUUID());
        debt.setName("Mac Mini BNPL");
        debt.setCreditor("Home Credit");
        debt.setDebtType(DebtType.INSTALLMENT_BNPL);
        debt.setOriginalPrincipal(BigDecimal.valueOf(24_000_000));
        debt.setCurrentPrincipal(BigDecimal.valueOf(24_000_000));
        debt.setInterestRateApr(BigDecimal.valueOf(0.06)); // 6%
        debt.setInterestAccrualConvention(InterestAccrualConvention.THIRTY_360);
        debt.setMonthlyMinimumDue(BigDecimal.valueOf(2_065_620)); // ~12 month annuity
        debt.setDueDayOfMonth(15);
        debt.setOriginationDate(LocalDate.of(2026, 1, 15));
        debt.setMaturityDate(LocalDate.of(2027, 1, 15));
        debt.setStatus(DebtStatus.ACTIVE);

        DebtAmortizationResult result = engine.generateSchedule(debt, LocalDate.of(2026, 1, 15));

        assertThat(result.schedule()).isNotEmpty();
        assertThat(result.schedule()).hasSize(12);

        // Ending balance of final installment must be exactly 0
        var lastItem = result.schedule().get(result.schedule().size() - 1);
        assertThat(lastItem.remainingPrincipal()).isEqualByComparingTo(BigDecimal.ZERO);

        // Sum of all principal portions must equal starting principal
        BigDecimal totalPrincipalPaid = result.schedule().stream()
                .map(item -> item.principalPortion())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalPrincipalPaid).isEqualByComparingTo(BigDecimal.valueOf(24_000_000));

        // Interest paid must be positive
        assertThat(result.totalInterest()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Avalanche strategy should prioritize highest APR and save more interest than baseline")
    void testAvalancheSimulation() {
        // Debt A: 20M at 24% APR (Credit Card), min 1.5M
        Debt card = new Debt();
        card.setId(UUID.randomUUID());
        card.setName("VPBank Credit Card");
        card.setCreditor("VPBank");
        card.setDebtType(DebtType.REVOLVING_CREDIT);
        card.setOriginalPrincipal(BigDecimal.valueOf(20_000_000));
        card.setCurrentPrincipal(BigDecimal.valueOf(20_000_000));
        card.setInterestRateApr(BigDecimal.valueOf(24.0)); // 24% (passed as percentage)
        card.setMonthlyMinimumDue(BigDecimal.valueOf(1_500_000));
        card.setDueDayOfMonth(10);
        card.setOriginationDate(LocalDate.of(2026, 1, 1));
        card.setStatus(DebtStatus.ACTIVE);

        // Debt B: 50M at 8% APR (Bank Loan), min 2.0M
        Debt loan = new Debt();
        loan.setId(UUID.randomUUID());
        loan.setName("Techcombank Cash Loan");
        loan.setCreditor("Techcombank");
        loan.setDebtType(DebtType.AMORTIZING_LOAN);
        loan.setOriginalPrincipal(BigDecimal.valueOf(50_000_000));
        loan.setCurrentPrincipal(BigDecimal.valueOf(50_000_000));
        loan.setInterestRateApr(BigDecimal.valueOf(8.0)); // 8%
        loan.setMonthlyMinimumDue(BigDecimal.valueOf(2_000_000));
        loan.setDueDayOfMonth(25);
        loan.setOriginationDate(LocalDate.of(2026, 1, 1));
        loan.setStatus(DebtStatus.ACTIVE);

        List<Debt> debts = List.of(card, loan);
        BigDecimal monthlySurplus = BigDecimal.valueOf(3_000_000); // 3M surplus / month

        PayoffSimulationComparison comparison = engine.simulatePayoff(debts, monthlySurplus, LocalDate.of(2026, 1, 1));

        assertThat(comparison.avalanche()).isNotNull();
        assertThat(comparison.snowball()).isNotNull();
        assertThat(comparison.baseline()).isNotNull();

        // Avalanche should finish earlier and pay less interest than baseline
        assertThat(comparison.avalanche().totalInterestPaid())
                .isLessThan(comparison.baseline().totalInterestPaid());
        assertThat(comparison.avalanche().totalMonths())
                .isLessThan(comparison.baseline().totalMonths());

        assertThat(comparison.avalancheInterestSaved()).isGreaterThan(BigDecimal.ZERO);
        assertThat(comparison.avalancheMonthsSaved()).isGreaterThan(0);

        // Verify entities were not mutated
        assertThat(card.getCurrentPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(20_000_000));
        assertThat(loan.getCurrentPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(50_000_000));
    }

    @Test
    @DisplayName("Snowball strategy should prioritize smallest balance first")
    void testSnowballTargetSelection() {
        // Debt 1: 5M at 5% APR (Small balance, low APR)
        Debt smallDebt = new Debt();
        smallDebt.setId(UUID.randomUUID());
        smallDebt.setName("Peer Loan");
        smallDebt.setCurrentPrincipal(BigDecimal.valueOf(5_000_000));
        smallDebt.setInterestRateApr(BigDecimal.valueOf(0.05));
        smallDebt.setMonthlyMinimumDue(BigDecimal.valueOf(1_000_000));
        smallDebt.setStatus(DebtStatus.ACTIVE);

        // Debt 2: 80M at 20% APR (Large balance, high APR)
        Debt largeDebt = new Debt();
        largeDebt.setId(UUID.randomUUID());
        largeDebt.setName("Credit Card");
        largeDebt.setCurrentPrincipal(BigDecimal.valueOf(80_000_000));
        largeDebt.setInterestRateApr(BigDecimal.valueOf(0.20));
        largeDebt.setMonthlyMinimumDue(BigDecimal.valueOf(3_000_000));
        largeDebt.setStatus(DebtStatus.ACTIVE);

        List<Debt> debts = List.of(smallDebt, largeDebt);
        PayoffSimulationComparison comparison = engine.simulatePayoff(debts, BigDecimal.valueOf(2_000_000), LocalDate.of(2026, 1, 1));

        // In Snowball, the small 5M debt should be eliminated in month 2 or 3
        var smallDebtDetail = comparison.snowball().debtDetails().stream()
                .filter(d -> d.debtId().equals(smallDebt.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(smallDebtDetail.monthsToPayoff()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should derive 0% effective APR for 0% BNPL installment")
    void testDeriveZeroAprForBnpl() {
        BigDecimal principal = BigDecimal.valueOf(24_000_000);
        BigDecimal monthlyPayment = BigDecimal.valueOf(2_000_000);
        int months = 12;

        BigDecimal apr = engine.calculateEffectiveApr(principal, monthlyPayment, months);
        assertThat(apr).isEqualByComparingTo(BigDecimal.ZERO);

        AprAnalysisResult analysis = engine.analyzeApr(principal, monthlyPayment, months, null);
        assertThat(analysis.isZeroInterest()).isTrue();
        assertThat(analysis.method()).isEqualTo("ZERO_BNPL");
    }

    @Test
    @DisplayName("Should derive accurate 12.0% APR from annuity monthly payment")
    void testDerive12PercentAprFromAnnuity() {
        BigDecimal principal = BigDecimal.valueOf(36_000_000);
        BigDecimal monthlyPayment = BigDecimal.valueOf(3_198_556.39);
        int months = 12;

        BigDecimal apr = engine.calculateEffectiveApr(principal, monthlyPayment, months);
        assertThat(apr).isBetween(BigDecimal.valueOf(11.95), BigDecimal.valueOf(12.05));

        AprAnalysisResult analysis = engine.analyzeApr(principal, monthlyPayment, months, BigDecimal.valueOf(12.0));
        assertThat(analysis.method()).isEqualTo("EXPLICIT_VERIFIED");
    }

    @Test
    @DisplayName("Should expose deceptive flat rate and compute true effective declining APR")
    void testExposeFlatRateTrap() {
        BigDecimal principal = BigDecimal.valueOf(24_000_000);
        BigDecimal monthlyPayment = BigDecimal.valueOf(2_200_000);
        int months = 12;

        BigDecimal flatApr = engine.calculateFlatRateApr(principal, monthlyPayment, months);
        assertThat(flatApr).isEqualByComparingTo(BigDecimal.valueOf(10.00));

        BigDecimal effectiveApr = engine.calculateEffectiveApr(principal, monthlyPayment, months);
        assertThat(effectiveApr).isBetween(BigDecimal.valueOf(17.8), BigDecimal.valueOf(18.2));

        AprAnalysisResult analysis = engine.analyzeApr(principal, monthlyPayment, months, BigDecimal.valueOf(10.0));
        assertThat(analysis.method()).isEqualTo("FLAT_RATE_EXPOSED");
        assertThat(analysis.explanation()).contains("FLAT interest");
    }

}
