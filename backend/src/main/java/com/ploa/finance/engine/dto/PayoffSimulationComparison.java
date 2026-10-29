package com.ploa.finance.engine.dto;

import java.math.BigDecimal;

/**
 * Side-by-side comparison of Baseline, Avalanche, and Snowball payoff scenarios.
 */
public record PayoffSimulationComparison(
        BigDecimal monthlySurplusApplied,
        PayoffStrategyResult baseline,
        PayoffStrategyResult avalanche,
        PayoffStrategyResult snowball,
        BigDecimal avalancheInterestSaved,
        int avalancheMonthsSaved,
        BigDecimal snowballInterestSaved,
        int snowballMonthsSaved
) {}
