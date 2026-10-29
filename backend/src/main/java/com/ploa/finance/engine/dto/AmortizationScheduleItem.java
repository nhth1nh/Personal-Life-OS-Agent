package com.ploa.finance.engine.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single scheduled installment row in a contractual debt amortization schedule.
 */
public record AmortizationScheduleItem(
        int installmentNumber,
        LocalDate dueDate,
        BigDecimal payment,
        BigDecimal principalPortion,
        BigDecimal interestPortion,
        BigDecimal remainingPrincipal
) {}
