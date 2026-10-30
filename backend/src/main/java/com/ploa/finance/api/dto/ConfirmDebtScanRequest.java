package com.ploa.finance.api.dto;

import com.ploa.finance.model.DebtType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ConfirmDebtScanRequest(
        ReconciliationAction action,
        UUID debtId,
        String name,
        String creditor,
        DebtType debtType,
        BigDecimal principal,
        BigDecimal apr,
        BigDecimal monthlyDue,
        Integer dueDayOfMonth,
        LocalDate dueDate,
        LocalDate maturityDate,
        BigDecimal paymentAmount,
        String notes
) {}
