package com.ploa.finance.api;

import com.ploa.ai.VisionOcrService;
import com.ploa.ai.dto.OcrResult;
import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.service.FinanceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {

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
     * List recent transactions for the current month.
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getRecentTransactions(
            @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(financeService.getRecentTransactions(limit));
    }

    /**
     * Get OCR stability and quality tracking metrics.
     */
    @GetMapping("/ocr/metrics")
    public ResponseEntity<com.ploa.finance.api.dto.OcrMetricsResponse> getOcrMetrics() {
        return ResponseEntity.ok(financeService.getOcrMetrics());
    }

    /**
     * Upload and extract structured receipt data from an image.
     */
    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrResult> parseReceiptImage(
            @RequestParam("file") MultipartFile file) throws IOException {
        String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        OcrResult result = ocrService.extractFromImage(file.getBytes(), mimeType);
        return ResponseEntity.ok(result);
    }
}
