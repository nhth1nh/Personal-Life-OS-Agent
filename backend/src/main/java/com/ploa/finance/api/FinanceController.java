package com.ploa.finance.api;

import com.ploa.ai.VisionOcrService;
import com.ploa.ai.dto.OcrResult;
import com.ploa.finance.api.dto.CreateTransactionRequest;
import com.ploa.finance.api.dto.FinanceAnalyticsResponse;
import com.ploa.finance.api.dto.OcrMetricsResponse;
import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.engine.dto.CashFlowSummary;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.service.FinanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

    private static final Logger log = LoggerFactory.getLogger(FinanceController.class);
    private static final long MAX_OCR_FILE_SIZE = 20 * 1024 * 1024; // 20 MB cap

    private final FinanceService financeService;
    private final VisionOcrService ocrService;

    public FinanceController(FinanceService financeService, VisionOcrService ocrService) {
        this.financeService = financeService;
        this.ocrService = ocrService;
    }

    /**
     * Get month-to-date burn rate, remaining budget, and safe daily allowance.
     */
    @GetMapping("/burn-rate")
    public ResponseEntity<BurnRateResult> getBurnRate() {
        return ResponseEntity.ok(financeService.getBurnRate());
    }

    /**
     * Get deterministic cash-flow waterfall, spendable cash, safe daily allowance, and liquidity status.
     */
    @GetMapping("/cashflow")
    public ResponseEntity<CashFlowSummary> getCashFlow() {
        return ResponseEntity.ok(financeService.getCashFlow());
    }

    /**
     * Get comprehensive financial analytics and category breakdown for a given month.
     */
    @GetMapping("/analytics")
    public ResponseEntity<FinanceAnalyticsResponse> getAnalytics(
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(financeService.getAnalytics(month));
    }

    /**
     * List recent transactions for the current month.
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getRecentTransactions(
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(financeService.getRecentTransactions(limit));
    }

    /**
     * Directly create a manual transaction (discretionary, fixed, or adjustment).
     */
    @PostMapping("/transactions")
    public ResponseEntity<Transaction> createDirectTransaction(@RequestBody CreateTransactionRequest req) {
        return ResponseEntity.ok(financeService.createDirectExpense(req));
    }

    /**
     * Get OCR stability and quality tracking metrics.
     */
    @GetMapping("/ocr/metrics")
    public ResponseEntity<OcrMetricsResponse> getOcrMetrics() {
        return ResponseEntity.ok(financeService.getOcrMetrics());
    }

    /**
     * Upload and extract structured receipt data from an image.
     */
    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrResult> parseReceiptImage(
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (file.getSize() > MAX_OCR_FILE_SIZE) {
            log.warn("Rejected OCR upload: size {} bytes exceeds limit of {} bytes", file.getSize(), MAX_OCR_FILE_SIZE);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        OcrResult result = ocrService.extractFromImage(file.getBytes(), mimeType);
        return ResponseEntity.ok(result);
    }
}
