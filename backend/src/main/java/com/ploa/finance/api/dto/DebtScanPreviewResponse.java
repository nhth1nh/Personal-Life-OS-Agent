package com.ploa.finance.api.dto;

import com.ploa.ai.dto.DebtOcrResult;
import com.ploa.finance.engine.dto.AprAnalysisResult;
import com.ploa.finance.model.DebtType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DebtScanPreviewResponse(
        DebtOcrResult rawOcr,
        AprAnalysisResult aprAnalysis,
        UUID matchedDebtId,
        String matchedDebtName,
        ReconciliationAction action,
        String recommendedName,
        String recommendedCreditor,
        String recommendedAccountNumber,
        DebtType recommendedDebtType,
        BigDecimal recommendedOriginalPrincipal,
        BigDecimal recommendedPrincipal,
        BigDecimal recommendedApr,
        BigDecimal recommendedMonthlyDue,
        Integer recommendedDueDay,
        LocalDate recommendedDueDate,
        LocalDate recommendedMaturityDate,
        String validationStatus,
        String message
) {
    public DebtScanPreviewResponse(
            DebtOcrResult rawOcr,
            AprAnalysisResult aprAnalysis,
            UUID matchedDebtId,
            String matchedDebtName,
            ReconciliationAction action,
            String recommendedName,
            String recommendedCreditor,
            DebtType recommendedDebtType,
            BigDecimal recommendedOriginalPrincipal,
            BigDecimal recommendedPrincipal,
            BigDecimal recommendedApr,
            BigDecimal recommendedMonthlyDue,
            Integer recommendedDueDay,
            LocalDate recommendedDueDate,
            LocalDate recommendedMaturityDate,
            String validationStatus,
            String message
    ) {
        this(rawOcr, aprAnalysis, matchedDebtId, matchedDebtName, action, recommendedName, recommendedCreditor,
                null, recommendedDebtType, recommendedOriginalPrincipal, recommendedPrincipal,
                recommendedApr, recommendedMonthlyDue, recommendedDueDay, recommendedDueDate,
                recommendedMaturityDate, validationStatus, message);
    }
}
