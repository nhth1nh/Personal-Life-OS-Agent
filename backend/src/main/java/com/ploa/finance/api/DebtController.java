package com.ploa.finance.api;

import com.ploa.finance.api.dto.CreateDebtRequest;
import com.ploa.finance.api.dto.RecordDebtPaymentRequest;
import com.ploa.finance.engine.dto.PayoffSimulationComparison;
import com.ploa.finance.model.Debt;
import com.ploa.finance.model.DebtPayment;
import com.ploa.finance.model.DebtSchedule;
import com.ploa.ai.VisionOcrService;
import com.ploa.ai.dto.DebtOcrResult;
import com.ploa.finance.api.dto.ConfirmDebtScanRequest;
import com.ploa.finance.api.dto.DebtScanPreviewResponse;
import com.ploa.finance.service.DebtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/debts")
public class DebtController {

    private static final long MAX_OCR_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private final DebtService debtService;
    private final VisionOcrService ocrService;

    public DebtController(DebtService debtService, VisionOcrService ocrService) {
        this.debtService = debtService;
        this.ocrService = ocrService;
    }

    @GetMapping
    public ResponseEntity<List<Debt>> getDebts() {
        return ResponseEntity.ok(debtService.getActiveDebts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Debt> getDebtById(@PathVariable UUID id) {
        return ResponseEntity.ok(debtService.getDebtById(id));
    }

    @PostMapping
    public ResponseEntity<Debt> createDebt(@RequestBody CreateDebtRequest req) {
        return ResponseEntity.ok(debtService.createDebt(req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDebt(@PathVariable UUID id) {
        debtService.deleteDebt(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/schedule")
    public ResponseEntity<List<DebtSchedule>> getDebtSchedule(@PathVariable UUID id) {
        return ResponseEntity.ok(debtService.getDebtSchedule(id));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<DebtPayment> recordPayment(
            @PathVariable UUID id,
            @RequestBody RecordDebtPaymentRequest req
    ) {
        return ResponseEntity.ok(debtService.recordPayment(id, req));
    }

    @GetMapping("/simulate")
    public ResponseEntity<PayoffSimulationComparison> simulatePayoff(
            @RequestParam(required = false, defaultValue = "0") BigDecimal surplus
    ) {
        return ResponseEntity.ok(debtService.simulatePayoff(surplus));
    }

    /**
     * Upload loan schedule, bank statement, or repayment slip for OCR extraction and reconciliation preview.
     */
    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DebtScanPreviewResponse> parseDebtDocument(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (file.getSize() > MAX_OCR_FILE_SIZE) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        DebtOcrResult ocr = ocrService.extractDebtFromImage(file.getBytes(), mimeType);
        DebtScanPreviewResponse preview = debtService.previewDebtScan(ocr);
        return ResponseEntity.ok(preview);
    }

    /**
     * Confirms and applies a user-reviewed debt action (Creates, Updates Balance, or Records Payment).
     */
    @PostMapping("/ocr/confirm")
    public ResponseEntity<Object> confirmDebtScan(@RequestBody ConfirmDebtScanRequest req) {
        return ResponseEntity.ok(debtService.confirmDebtScan(req));
    }

}
