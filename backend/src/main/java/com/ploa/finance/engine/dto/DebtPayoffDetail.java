package com.ploa.finance.engine.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Summary metrics for an individual debt under an acceleration simulation.
 */
public record DebtPayoffDetail(
        UUID debtId,
        String debtName,
        BigDecimal startingPrincipal,
        BigDecimal totalInterestPaid,
        BigDecimal totalPrincipalPaid,
        int monthsToPayoff,
        LocalDate payoffDate
) {}
