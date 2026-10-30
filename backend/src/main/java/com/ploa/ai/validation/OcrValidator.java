package com.ploa.ai.validation;

import com.ploa.ai.TemporalContext;
import com.ploa.ai.dto.DebtOcrResult;
import com.ploa.ai.dto.OcrResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic quality gate for raw OCR extraction.
 * Cross-checks extracted financial figures against mathematical invariants and temporal bounds.
 * Prevents hallucinations or corrupted numbers from silently poisoning the user's ledger.
 */
@Component
public class OcrValidator {

    private static final Logger log = LoggerFactory.getLogger(OcrValidator.class);
    private static final long MAX_REASONABLE_EXPENSE_VND = 1_000_000_000L; // 1 billion VND

    private final TemporalContext temporalContext;

    public OcrValidator(TemporalContext temporalContext) {
        this.temporalContext = temporalContext;
    }

    /**
     * Validates an expense, receipt, or bank transfer OCR result and attaches validation metadata.
     */
    public OcrResult applyValidation(OcrResult ocr) {
        if (ocr == null) {
            return null;
        }
        OcrValidationResult vr = validateReceipt(ocr);
        return ocr.withValidation(vr.status().name(), vr.warnings());
    }

    /**
     * Evaluates a receipt OCR result against mathematical and temporal invariants.
     */
    public OcrValidationResult validateReceipt(OcrResult ocr) {
        if (ocr == null) {
            return OcrValidationResult.rejected(List.of("No OCR data provided"));
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();

        // 1. Amount Sanity Invariants
        Long amount = ocr.totalAmount();
        if (amount == null) {
            errors.add("Total transaction amount is missing");
        } else if (amount <= 0) {
            errors.add("Total transaction amount must be positive (got " + amount + " VND)");
        } else if (amount > MAX_REASONABLE_EXPENSE_VND) {
            warnings.add(String.format("Unusually large expense amount: %,d VND. Please verify.", amount));
            details.put("large_amount", amount);
        }

        if (!errors.isEmpty()) {
            return OcrValidationResult.rejected(errors);
        }

        // 2. Temporal Bounds
        LocalDate today = temporalContext != null ? temporalContext.currentDate() : LocalDate.now();
        if (ocr.transactionDate() != null) {
            if (ocr.transactionDate().isAfter(today.plusDays(1))) {
                warnings.add("Transaction date is in the future: " + ocr.transactionDate());
            } else if (ocr.transactionDate().isBefore(today.minusYears(2))) {
                warnings.add("Transaction date is more than 2 years old: " + ocr.transactionDate());
            }
        }

        // 3. Line-Item Sum Invariant: sum(items) ≈ totalAmount
        if (ocr.lineItems() != null && !ocr.lineItems().isEmpty() && amount != null) {
            long lineItemSum = 0;
            boolean hasPricedItems = false;

            for (OcrResult.LineItem item : ocr.lineItems()) {
                if (item.total() != null && item.total() > 0) {
                    lineItemSum += item.total();
                    hasPricedItems = true;
                } else if (item.unitPrice() != null && item.quantity() != null && item.unitPrice() > 0 && item.quantity() > 0) {
                    lineItemSum += (long) item.unitPrice() * item.quantity();
                    hasPricedItems = true;
                }
            }

            if (hasPricedItems && lineItemSum > 0) {
                long diff = Math.abs(lineItemSum - amount);
                long tolerance = Math.max(2000L, (long) (amount * 0.05)); // 5% or 2000 VND for tax/rounding
                if (diff > tolerance) {
                    warnings.add(String.format("Line items sum (%,d VND) differs from receipt total (%,d VND). Discrepancy: %,d VND.",
                            lineItemSum, amount, diff));
                    details.put("line_item_sum", lineItemSum);
                    details.put("discrepancy", diff);
                }
            }
        }

        // 4. Entity Identification Quality
        if (ocr.merchant() == null || ocr.merchant().isBlank()) {
            warnings.add("Merchant name could not be identified with certainty");
        }

        // 5. Visual OCR Confidence Threshold
        if (ocr.confidence() != null && ocr.confidence() < 0.65) {
            warnings.add(String.format("Low visual extraction confidence: %.0f%%", ocr.confidence() * 100));
        }

        if (!warnings.isEmpty()) {
            return OcrValidationResult.needsReview(warnings, details);
        }

        return OcrValidationResult.verified();
    }

    /**
     * Evaluates a debt contract, statement, or repayment slip against financial invariants.
     */
    public OcrValidationResult validateDebt(DebtOcrResult ocr) {
        if (ocr == null) {
            return OcrValidationResult.rejected(List.of("No debt OCR data provided"));
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> details = new HashMap<>();
        boolean isReconstructed = false;

        // 1. Presence of Core Liabilities Terms
        boolean hasPaymentAmount = Boolean.TRUE.equals(ocr.isPaymentSlip()) && ocr.paymentAmount() != null && ocr.paymentAmount() > 0;
        boolean hasBalance = (ocr.currentPrincipal() != null && ocr.currentPrincipal() > 0)
                || (ocr.originalPrincipal() != null && ocr.originalPrincipal() > 0);

        if (!hasPaymentAmount && !hasBalance) {
            errors.add("Document does not contain any readable debt balance, original loan amount, or repayment figure.");
            return OcrValidationResult.rejected(errors);
        }

        // 2. Balance Invariant: current_principal <= original_principal
        if (ocr.originalPrincipal() != null && ocr.currentPrincipal() != null) {
            if (ocr.currentPrincipal() > ocr.originalPrincipal()) {
                warnings.add(String.format("Current outstanding balance (%,d VND) exceeds original principal (%,d VND).",
                        ocr.currentPrincipal(), ocr.originalPrincipal()));
                details.put("balance_overflow", true);
            }
        }

        // 3. Annuity / Cash-Flow Repayment Invariant: Total payments >= Principal when APR > 0
        if (ocr.monthlyPayment() != null && ocr.termMonths() != null && ocr.termMonths() > 0) {
            long totalPayments = ocr.monthlyPayment() * ocr.termMonths();
            Long refPrincipal = ocr.originalPrincipal() != null ? ocr.originalPrincipal() : ocr.currentPrincipal();

            if (refPrincipal != null && refPrincipal > 0) {
                if (ocr.statedApr() != null && ocr.statedApr() > 0.0) {
                    if (totalPayments < refPrincipal) {
                        warnings.add(String.format("Repayment contradiction: Total scheduled payments (%,d VND) is less than principal (%,d VND) at stated APR of %.2f%%.",
                                totalPayments, refPrincipal, ocr.statedApr()));
                        details.put("repayment_deficit", refPrincipal - totalPayments);
                    }
                }
            }
        }

        // 4. Calendar & Due Day Invariants
        if (ocr.dueDayOfMonth() != null && (ocr.dueDayOfMonth() < 1 || ocr.dueDayOfMonth() > 31)) {
            warnings.add("Invalid payment due day of month: " + ocr.dueDayOfMonth());
        }

        if (ocr.originationDate() != null && ocr.dueDate() != null && ocr.originationDate().isAfter(ocr.dueDate())) {
            warnings.add("Origination date (" + ocr.originationDate() + ") occurs after due date (" + ocr.dueDate() + ")");
        }

        // 5. Reconstructed Terms Detection (Mid-Tenure statements)
        if (ocr.originalPrincipal() == null && ocr.currentPrincipal() != null && ocr.monthlyPayment() != null) {
            isReconstructed = true;
            details.put("reconstructed_original_principal", true);
        }

        if (!warnings.isEmpty()) {
            return OcrValidationResult.needsReview(warnings, details);
        }

        if (isReconstructed) {
            return OcrValidationResult.reconstructed(List.of("Mid-tenure statement: original loan amount reconstructed from payment schedule"), details);
        }

        return OcrValidationResult.verified();
    }
}
