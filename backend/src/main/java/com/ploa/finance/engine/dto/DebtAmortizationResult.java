package com.ploa.finance.engine.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Result of a deterministic loan amortization calculation.
 */
public record DebtAmortizationResult(
        UUID debtId,
        String debtName,
        BigDecimal initialPrincipal,
        BigDecimal monthlyPayment,
        BigDecimal totalInterest,
        LocalDate maturityDate,
        List<AmortizationScheduleItem> schedule
) {}
