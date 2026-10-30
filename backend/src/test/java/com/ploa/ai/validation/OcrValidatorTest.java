package com.ploa.ai.validation;

import com.ploa.ai.TemporalContext;
import com.ploa.ai.dto.DebtOcrResult;
import com.ploa.ai.dto.OcrResult;
import com.ploa.finance.model.DebtType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OcrValidatorTest {

    private OcrValidator validator;
    private final ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

    @BeforeEach
    void setUp() {
        TemporalContext temporalContext = new TemporalContext(zone);
        validator = new OcrValidator(temporalContext);
    }

    @Test
    @DisplayName("Clean receipt should pass validation as VERIFIED")
    void testCleanReceiptVerified() {
        OcrResult ocr = new OcrResult(
                "RECEIPT", "manual", "INV-001",
                LocalDate.now(zone), "12:30",
                65000L, "VND", "Food", "DISCRETIONARY",
                "Highlands Coffee", "Cà phê sữa đá", 0.95,
                List.of(new OcrResult.LineItem("Cà phê sữa đá", 1, 65000L, 65000L))
        );

        OcrValidationResult result = validator.validateReceipt(ocr);
        assertThat(result.status()).isEqualTo(ValidationStatus.VERIFIED);
        assertThat(result.isAcceptable()).isTrue();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("Receipt missing amount or non-positive should be REJECTED")
    void testReceiptMissingAmountRejected() {
        OcrResult noAmount = new OcrResult("RECEIPT", "manual", null, null, null, null, "VND", "Food", "DISCRETIONARY", "Cafe", null, 0.9, null);
        assertThat(validator.validateReceipt(noAmount).status()).isEqualTo(ValidationStatus.REJECTED);

        OcrResult zeroAmount = new OcrResult("RECEIPT", "manual", null, null, null, 0L, "VND", "Food", "DISCRETIONARY", "Cafe", null, 0.9, null);
        assertThat(validator.validateReceipt(zeroAmount).status()).isEqualTo(ValidationStatus.REJECTED);
    }

    @Test
    @DisplayName("Receipt with future date should trigger NEEDS_REVIEW")
    void testReceiptFutureDateNeedsReview() {
        OcrResult futureDate = new OcrResult(
                "RECEIPT", "manual", null,
                LocalDate.now(zone).plusDays(15), null,
                50000L, "VND", "Food", "DISCRETIONARY",
                "Highlands Coffee", null, 0.9, null
        );

        OcrValidationResult result = validator.validateReceipt(futureDate);
        assertThat(result.status()).isEqualTo(ValidationStatus.NEEDS_REVIEW);
        assertThat(result.warnings()).anyMatch(w -> w.contains("future"));
    }

    @Test
    @DisplayName("Receipt with line items mismatch should trigger NEEDS_REVIEW")
    void testReceiptLineItemsMismatch() {
        OcrResult mismatch = new OcrResult(
                "RECEIPT", "manual", null,
                LocalDate.now(zone), null,
                100000L, "VND", "Food", "DISCRETIONARY",
                "Cơm Tấm", null, 0.9,
                List.of(
                        new OcrResult.LineItem("Cơm sườn", 1, 80000L, 80000L),
                        new OcrResult.LineItem("Canh chua", 1, 70000L, 70000L) // Total items = 150,000 != 100,000
                )
        );

        OcrValidationResult result = validator.validateReceipt(mismatch);
        assertThat(result.status()).isEqualTo(ValidationStatus.NEEDS_REVIEW);
        assertThat(result.warnings()).anyMatch(w -> w.contains("Line items sum"));
    }

    @Test
    @DisplayName("applyValidation should populate validation_status on OcrResult")
    void testApplyValidationOnOcrResult() {
        OcrResult raw = new OcrResult(
                "RECEIPT", "manual", null,
                LocalDate.now(zone), null,
                50000L, "VND", "Food", "DISCRETIONARY",
                "Quán Phở", null, 0.95, null
        );

        OcrResult validated = validator.applyValidation(raw);
        assertThat(validated.validationStatus()).isEqualTo("VERIFIED");
        assertThat(validated.validationWarnings()).isEmpty();
    }

    @Test
    @DisplayName("Clean amortizing debt should pass validation as VERIFIED")
    void testCleanDebtVerified() {
        DebtOcrResult ocr = new DebtOcrResult(
                "AMORTIZING_LOAN", "VPBank", "Vay tiêu dùng", DebtType.AMORTIZING_LOAN,
                40000000L, 40000000L, 2500000L, 18, 18,
                "1.72%/tháng", 20.64, 5, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 10, 1), false, null, "Disbursed", 0.95
        );

        OcrValidationResult result = validator.validateDebt(ocr);
        assertThat(result.status()).isEqualTo(ValidationStatus.VERIFIED);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("Debt with current balance exceeding original principal should trigger NEEDS_REVIEW")
    void testDebtCurrentBalanceExceedsOriginal() {
        DebtOcrResult ocr = new DebtOcrResult(
                "AMORTIZING_LOAN", "VPBank", "Loan", DebtType.AMORTIZING_LOAN,
                30000000L, 50000000L, 2000000L, 18, 18,
                "12%/năm", 12.0, 5, null, null, null, false, null, null, 0.9
        );

        OcrValidationResult result = validator.validateDebt(ocr);
        assertThat(result.status()).isEqualTo(ValidationStatus.NEEDS_REVIEW);
        assertThat(result.warnings()).anyMatch(w -> w.contains("exceeds original"));
    }

    @Test
    @DisplayName("Debt with repayment contradiction (total payments < principal at positive APR) should trigger NEEDS_REVIEW")
    void testDebtRepaymentContradiction() {
        // 40M borrowed at 20% APR, but only 10 payments of 2M (20M total < 40M)
        DebtOcrResult ocr = new DebtOcrResult(
                "AMORTIZING_LOAN", "VPBank", "Loan", DebtType.AMORTIZING_LOAN,
                40000000L, 40000000L, 2000000L, 10, 10,
                "20%/năm", 20.0, 5, null, null, null, false, null, null, 0.9
        );

        OcrValidationResult result = validator.validateDebt(ocr);
        assertThat(result.status()).isEqualTo(ValidationStatus.NEEDS_REVIEW);
        assertThat(result.warnings()).anyMatch(w -> w.contains("Repayment contradiction"));
    }

    @Test
    @DisplayName("Debt with missing original principal (mid-tenure) should be marked RECONSTRUCTED")
    void testMidTenureDebtMarkedReconstructed() {
        DebtOcrResult midTenure = new DebtOcrResult(
                "AMORTIZING_LOAN", "Standard Chartered", "Personal Loan", DebtType.AMORTIZING_LOAN,
                null, 28200641L, 4919756L, 24, 7,
                "16.49%", 16.49, 30, null, null, null, false, null, null, 0.95
        );

        OcrValidationResult result = validator.validateDebt(midTenure);
        assertThat(result.status()).isEqualTo(ValidationStatus.RECONSTRUCTED);
    }
}
