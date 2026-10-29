package com.ploa.finance.engine.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of simulating a complete debt payoff strategy across a portfolio.
 */
public record PayoffStrategyResult(
        PayoffStrategy strategy,
        LocalDate debtFreeDate,
        int totalMonths,
        BigDecimal totalInterestPaid,
        BigDecimal totalPrincipalPaid,
        BigDecimal totalPayments,
        List<DebtPayoffDetail> debtDetails
) {}
