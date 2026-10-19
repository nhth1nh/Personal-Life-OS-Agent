package com.ploa.finance.engine.dto;

import java.time.LocalDate;
import java.util.Map;

public record BurnRateResult(
        LocalDate monthStart,
        LocalDate today,
        int daysElapsed,
        int daysRemaining,

        long monthlyBudgetVnd,
        long mtdTotalSpendVnd,       // month-to-date total
        long mtdDiscretionaryVnd,
        long mtdFixedVnd,
        long remainingBudgetVnd,

        double dailyBurnRateVnd,     // avg daily discretionary spend so far
        double safeDailyAllowanceVnd, // remaining budget / days remaining
        double monthlySavingsRatePct, // 1 - (mtdTotal / (budget * daysElapsed/daysInMonth))

        Map<String, Long> categoryBreakdown
) {
    public boolean isOverBudget() {
        return remainingBudgetVnd < 0;
    }
}
