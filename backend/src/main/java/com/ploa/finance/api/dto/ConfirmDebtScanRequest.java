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
        BigDecimal originalPrincipal,
        BigDecimal principal,
        BigDecimal apr,
        BigDecimal monthlyDue,
        Integer dueDayOfMonth,
        LocalDate dueDate,
        LocalDate maturityDate,
        BigDecimal paymentAmount,
        String notes
) {
    public ConfirmDebtScanRequest(
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
    ) {
        this(action, debtId, name, creditor, debtType, principal, principal, apr, monthlyDue, dueDayOfMonth, dueDate, maturityDate, paymentAmount, notes);
    }
}
