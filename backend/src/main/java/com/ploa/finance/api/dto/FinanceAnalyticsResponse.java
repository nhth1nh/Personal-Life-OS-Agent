package com.ploa.finance.api.dto;

import java.time.LocalDate;
import java.util.List;

public record FinanceAnalyticsResponse(
        String month,
        long monthlyBudgetVnd,
        long totalSpendVnd,
        long remainingBudgetVnd,
        long fixedSpendVnd,
        long discretionarySpendVnd,
        long dailyBurnRateVnd,
        long safeDailyAllowanceVnd,
        long projectedMonthEndSpendVnd,
        int elapsedDays,
        int daysInMonth,
        List<CategorySpend> categories,
        List<DailySpend> dailySpending
) {
    public record CategorySpend(
            String category,
            long totalVnd,
            double percentage
    ) {}

    public record DailySpend(
            LocalDate date,
            long totalVnd,
            long fixedVnd,
            long discretionaryVnd
    ) {}
}
