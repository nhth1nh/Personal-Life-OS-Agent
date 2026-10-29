package com.ploa.finance.api.dto;

import com.ploa.finance.model.DebtType;
import com.ploa.finance.model.InterestAccrualConvention;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateDebtRequest(
        String name,
        String creditor,
        DebtType debtType,
        String currency,
        BigDecimal originalPrincipal,
        BigDecimal currentPrincipal,
        BigDecimal interestRateApr,
        InterestAccrualConvention interestAccrualConvention,
        BigDecimal monthlyMinimumDue,
        Integer dueDayOfMonth,
        LocalDate originationDate,
        LocalDate maturityDate,
        String notes
) {}
