package com.ploa.finance.engine;

import com.ploa.config.PloaProperties;
import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic financial burn rate engine.
 * Computes MTD spend, daily burn rate, safe daily allowance, and category breakdown.
 * No LLM involvement.
 */
@Component
public class BurnRateEngine {

    private final TransactionRepository repo;
    private final PloaProperties properties;

    public BurnRateEngine(TransactionRepository repo, PloaProperties properties) {
        this.repo = repo;
        this.properties = properties;
    }

    public BurnRateResult compute() {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.of(today.getYear(), today.getMonth());
        LocalDate monthStart = currentMonth.atDay(1);
        int daysInMonth = currentMonth.lengthOfMonth();
        int daysElapsed = today.getDayOfMonth();
        int daysRemaining = daysInMonth - daysElapsed + 1; // include today

        long budgetVnd = properties.finance().monthlyBudgetVnd();

        long mtdTotal = repo.sumConfirmedAmountBetween(monthStart, today);
        long mtdDiscretionary = repo.sumBySpendTypeBetween(monthStart, today, SpendType.DISCRETIONARY);
        long mtdFixed = repo.sumBySpendTypeBetween(monthStart, today, SpendType.FIXED);
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

        // Category breakdown
        List<Object[]> rows = repo.sumByCategoryBetween(monthStart, today);
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
