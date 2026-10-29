package com.ploa.finance.engine;

import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.repository.TransactionRepository;
import com.ploa.profile.UserProfileService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic financial burn rate engine.
 * Computes MTD operating spend, daily burn rate, safe daily allowance, and category breakdown.
 * Invariant: Debt principal repayment and internal transfers are strictly excluded from operating spend.
 * Temporal Grounding: Evaluates against Asia/Ho_Chi_Minh (UTC+7).
 */
@Component
public class BurnRateEngine {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TransactionRepository repo;
    private final UserProfileService profileService;

    public BurnRateEngine(TransactionRepository repo, UserProfileService profileService) {
        this.repo = repo;
        this.profileService = profileService;
    }

    public BurnRateResult compute() {
        return computeForDate(LocalDate.now(ZONE));
    }

    public BurnRateResult computeForDate(LocalDate today) {
        YearMonth currentMonth = YearMonth.of(today.getYear(), today.getMonth());
        LocalDate monthStart = currentMonth.atDay(1);
        int daysInMonth = currentMonth.lengthOfMonth();
        int daysElapsed = today.getDayOfMonth();
        int daysRemaining = daysInMonth - daysElapsed + 1; // include today

        long budgetVnd = profileService.getMonthlyBudgetVnd();

        Long operatingTotal = repo.sumOperatingSpendBetween(monthStart, today);
        long mtdDiscretionary = repo.sumBySpendTypeBetween(monthStart, today, SpendType.DISCRETIONARY);
        long mtdFixed = repo.sumBySpendTypeBetween(monthStart, today, SpendType.FIXED);

        // Fallback to spendType sum if operating spend query yields 0 for legacy records
        long mtdTotal = (operatingTotal != null && operatingTotal > 0)
                ? operatingTotal
                : (mtdDiscretionary + mtdFixed);

        long remaining = budgetVnd - mtdTotal;

        double dailyBurnRate = daysElapsed > 0
                ? (double) mtdDiscretionary / daysElapsed
                : 0.0;

        double safeDailyAllowance = daysRemaining > 0 && remaining > 0
                ? (double) remaining / daysRemaining
                : 0.0;

        // Savings rate: how much of the proportional budget we haven't spent
        double proportionalBudget = budgetVnd * ((double) daysElapsed / daysInMonth);
        double savingsRate = proportionalBudget > 0
                ? Math.max(0, (1.0 - mtdTotal / proportionalBudget) * 100)
                : 0.0;

        // Category breakdown (operating spend only)
        List<Object[]> rows = repo.sumOperatingSpendByCategoryBetween(monthStart, today);
        if (rows.isEmpty()) {
            rows = repo.sumByCategoryBetween(monthStart, today);
        }

        Map<String, Long> breakdown = new LinkedHashMap<>();
        for (Object[] row : rows) {
            breakdown.put((String) row[0], (Long) row[1]);
        }

        return new BurnRateResult(
                monthStart, today, daysElapsed, daysRemaining,
                budgetVnd, mtdTotal, mtdDiscretionary, mtdFixed, remaining,
                dailyBurnRate, safeDailyAllowance,
                Math.round(savingsRate * 10.0) / 10.0,
                breakdown
        );
    }
}
