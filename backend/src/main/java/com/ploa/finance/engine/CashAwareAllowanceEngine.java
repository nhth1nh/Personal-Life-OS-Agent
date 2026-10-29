package com.ploa.finance.engine;

import com.ploa.finance.engine.dto.CashFlowSummary;
import com.ploa.finance.engine.dto.LiquidityStatus;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.repository.DebtScheduleRepository;
import com.ploa.finance.repository.FinancialAccountRepository;
import com.ploa.finance.repository.IncomeRepository;
import com.ploa.finance.repository.TransactionRepository;
import com.ploa.profile.UserProfileService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Deterministic cash-aware daily allowance and liquidity engine.
 * Computes Spendable Cash Now from the empirical cash timeline:
 * Spendable Cash = Liquid Cash + Risk-Weighted Inflows - Unpaid Debt - Unpaid Fixed - Emergency Reserve
 * Invariant: Never reports a casual 0 VND allowance on deficit; raises structured LiquidityStatus alerts.
 * Temporal Grounding: Asia/Ho_Chi_Minh (UTC+7).
 */
@Component
public class CashAwareAllowanceEngine {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final FinancialAccountRepository accountRepo;
    private final IncomeRepository incomeRepo;
    private final DebtScheduleRepository debtScheduleRepo;
    private final TransactionRepository transactionRepo;
    private final UserProfileService profileService;

    public CashAwareAllowanceEngine(
            FinancialAccountRepository accountRepo,
            IncomeRepository incomeRepo,
            DebtScheduleRepository debtScheduleRepo,
            TransactionRepository transactionRepo,
            UserProfileService profileService
    ) {
        this.accountRepo = accountRepo;
        this.incomeRepo = incomeRepo;
        this.debtScheduleRepo = debtScheduleRepo;
        this.transactionRepo = transactionRepo;
        this.profileService = profileService;
    }

    public CashFlowSummary evaluate() {
        return evaluate(ZonedDateTime.now(ZONE), null, null);
    }

    public CashFlowSummary evaluate(ZonedDateTime asOfDateTime) {
        return evaluate(asOfDateTime, null, null);
    }

    public CashFlowSummary evaluate(
            ZonedDateTime asOfDateTime,
            BigDecimal unpaidFixedBillsOverride,
            BigDecimal emergencyReserveOverride
    ) {
        ZonedDateTime effectiveTime = asOfDateTime != null ? asOfDateTime : ZonedDateTime.now(ZONE);
        LocalDate today = effectiveTime.toLocalDate();
        YearMonth currentMonth = YearMonth.of(today.getYear(), today.getMonth());
        LocalDate monthStart = currentMonth.atDay(1);
        LocalDate monthEnd = currentMonth.atEndOfMonth();

        // 1. Calculate remaining days (includes today if before 18:00 local time)
        int daysRemaining = monthEnd.getDayOfMonth() - today.getDayOfMonth();
        if (effectiveTime.getHour() < 18) {
            daysRemaining += 1;
        }
        daysRemaining = Math.max(1, daysRemaining);

        // 2. Liquid Cash Available Now (excluding credit cards)
        BigDecimal liquidCashNow = accountRepo.sumLiquidCashBalances();
        if (liquidCashNow == null) liquidCashNow = BigDecimal.ZERO;
        liquidCashNow = liquidCashNow.setScale(2, RoundingMode.HALF_UP);

        // 3. Expected Inflows Before Month-End (Risk-weighted by confidence factor)
        BigDecimal expectedInflows = incomeRepo.sumExpectedInflowsBetween(today, monthEnd);
        if (expectedInflows == null) expectedInflows = BigDecimal.ZERO;
        expectedInflows = expectedInflows.setScale(2, RoundingMode.HALF_UP);

        // 4. Committed Unpaid Debt Obligations Before Month-End
        BigDecimal unpaidDebt = debtScheduleRepo.sumUpcomingObligationsBetween(today, monthEnd);
        if (unpaidDebt == null) unpaidDebt = BigDecimal.ZERO;
        unpaidDebt = unpaidDebt.setScale(2, RoundingMode.HALF_UP);

        // 5. Committed Unpaid Fixed Bills Before Month-End
        BigDecimal unpaidFixed = unpaidFixedBillsOverride != null
                ? unpaidFixedBillsOverride.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 6. Mandatory Emergency Reserve Floor
        BigDecimal emergencyReserve = emergencyReserveOverride != null
                ? emergencyReserveOverride.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(profileService.getEmergencyReserveFloorVnd()).setScale(2, RoundingMode.HALF_UP);

        // 7. Spendable Cash = Liquid + Inflows - Debt - Fixed - Reserve
        BigDecimal spendableCash = liquidCashNow
                .add(expectedInflows)
                .subtract(unpaidDebt)
                .subtract(unpaidFixed)
                .subtract(emergencyReserve)
                .setScale(2, RoundingMode.HALF_UP);

        // 8. Daily Allowance & Liquidity Status
        BigDecimal safeDailyAllowance;
        LiquidityStatus liquidityStatus;
        String recommendation;

        if (spendableCash.compareTo(BigDecimal.ZERO) > 0) {
            safeDailyAllowance = spendableCash.divide(BigDecimal.valueOf(daysRemaining), 2, RoundingMode.HALF_UP);
            liquidityStatus = LiquidityStatus.SOLVENT;
            recommendation = "Solvent. Safe daily discretionary spending allowance is "
                    + safeDailyAllowance.setScale(0, RoundingMode.HALF_UP) + " VND.";
        } else {
            safeDailyAllowance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            LocalDate sevenDaysOut = today.plusDays(7);
            BigDecimal upcoming7dDebt = debtScheduleRepo.sumUpcomingObligationsBetween(today, sevenDaysOut);
            if (upcoming7dDebt == null) upcoming7dDebt = BigDecimal.ZERO;

            if (liquidCashNow.compareTo(upcoming7dDebt) < 0) {
                liquidityStatus = LiquidityStatus.LIQUIDITY_DEFICIT;
                recommendation = "CRITICAL LIQUIDITY DEFICIT: Liquid cash ("
                        + liquidCashNow.setScale(0, RoundingMode.HALF_UP)
                        + " VND) is insufficient for upcoming 7-day obligations ("
                        + upcoming7dDebt.setScale(0, RoundingMode.HALF_UP)
                        + " VND). Freeze discretionary spend.";
            } else {
                liquidityStatus = LiquidityStatus.PROJECTED_SHORTFALL;
                recommendation = "PROJECTED SHORTFALL: Solvent today, but a cash shortfall of "
                        + spendableCash.abs().setScale(0, RoundingMode.HALF_UP)
                        + " VND is projected before month-end. Restrict spending.";
            }
        }

        // 9. Accounting Invariant MTD Metrics
        BigDecimal mtdIncomeReceived = incomeRepo.sumReceivedIncomeBetween(monthStart, today);
        if (mtdIncomeReceived == null) mtdIncomeReceived = BigDecimal.ZERO;
        mtdIncomeReceived = mtdIncomeReceived.setScale(2, RoundingMode.HALF_UP);

        Long operatingSpendLong = transactionRepo.sumOperatingSpendBetween(monthStart, today);
        if (operatingSpendLong == null || operatingSpendLong == 0) {
            // Fallback for legacy transactions without transaction_type populated
            Long disc = transactionRepo.sumBySpendTypeBetween(monthStart, today, SpendType.DISCRETIONARY);
            Long fixed = transactionRepo.sumBySpendTypeBetween(monthStart, today, SpendType.FIXED);
            operatingSpendLong = (disc != null ? disc : 0L) + (fixed != null ? fixed : 0L);
        }
        BigDecimal mtdOperatingSpend = BigDecimal.valueOf(operatingSpendLong).setScale(2, RoundingMode.HALF_UP);

        Long debtPrincipalPaidLong = transactionRepo.sumDebtPrincipalPaidBetween(monthStart, today);
        BigDecimal mtdDebtPrincipalPaid = BigDecimal.valueOf(debtPrincipalPaidLong != null ? debtPrincipalPaidLong : 0L)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netCashFlowMtd = mtdIncomeReceived
                .subtract(mtdOperatingSpend)
                .subtract(mtdDebtPrincipalPaid)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal netSavingsMtd = mtdIncomeReceived
                .subtract(mtdOperatingSpend)
                .setScale(2, RoundingMode.HALF_UP);

        return new CashFlowSummary(
                today,
                liquidCashNow,
                expectedInflows,
                unpaidDebt,
                unpaidFixed,
                emergencyReserve,
                spendableCash,
                daysRemaining,
                safeDailyAllowance,
                liquidityStatus,
                mtdIncomeReceived,
                mtdOperatingSpend,
                mtdDebtPrincipalPaid,
                netCashFlowMtd,
                netSavingsMtd,
                recommendation
        );
    }
}
