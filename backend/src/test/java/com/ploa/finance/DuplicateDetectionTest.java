package com.ploa.finance;

import com.ploa.ai.dto.OcrResult;
import com.ploa.finance.engine.BurnRateEngine;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.repository.OcrAuditRepository;
import com.ploa.finance.repository.TransactionRepository;
import com.ploa.finance.service.FinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DuplicateDetectionTest {

    private TransactionRepository txRepo;
    private OcrAuditRepository auditRepo;
    private BurnRateEngine burnRateEngine;
    private FinanceService financeService;

    @BeforeEach
    void setUp() {
        txRepo = mock(TransactionRepository.class);
        auditRepo = mock(OcrAuditRepository.class);
        financeService = new FinanceService(txRepo, auditRepo, null);
    }

    @Test
    void testExactImageSha256Duplicate() {
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        Transaction existing = new Transaction();
        existing.setTxDate(LocalDate.of(2026, 10, 20));
        existing.setMerchant("Highlands Coffee");
        existing.setAmount(55000L);
        existing.setImageSha256(sha256);

        when(txRepo.findFirstByImageSha256(sha256)).thenReturn(Optional.of(existing));

        OcrResult ocr = new OcrResult("RECEIPT", "manual", "INV-101", LocalDate.of(2026, 10, 20),
                "08:30:00", 55000L, "VND", "Food", "DISCRETIONARY", "Highlands Coffee", null, 0.95, null);

        var result = financeService.checkDuplicate(ocr, sha256);
        assertTrue(result.hasDuplicate());
        assertTrue(result.warningMessage().contains("Exact image already scanned"));
    }

    @Test
    void testInvoiceNumberDuplicate() {
        String invoice = "FT2610192837";
        Transaction existing = new Transaction();
        existing.setTxDate(LocalDate.of(2026, 10, 19));
        existing.setMerchant("Shopee");
        existing.setAmount(120000L);
        existing.setInvoiceNumber(invoice);

        when(txRepo.findFirstByImageSha256("different-sha256")).thenReturn(Optional.empty());
        when(txRepo.findByInvoiceNumberIgnoreCaseAndMerchantIgnoreCase(invoice, "Shopee"))
                .thenReturn(List.of(existing));

        OcrResult ocr = new OcrResult("BANK_TRANSFER", "VietQR", invoice, LocalDate.of(2026, 10, 19),
                "14:15:00", 120000L, "VND", "Shopping", "DISCRETIONARY", "Shopee", null, 0.98, null);

        var result = financeService.checkDuplicate(ocr, "different-sha256");
        assertTrue(result.hasDuplicate());
        assertTrue(result.warningMessage().contains("Invoice/Order ID (" + invoice + ") already logged"));
    }

    @Test
    void testFuzzyAmountDateMerchantDuplicate() {
        LocalDate date = LocalDate.of(2026, 10, 20);
        Transaction existing = new Transaction();
        existing.setTxDate(date);
        existing.setMerchant("Starbucks");
        existing.setAmount(95000L);

        when(txRepo.findFirstByImageSha256(null)).thenReturn(Optional.empty());
        when(txRepo.findByAmountAndTxDateAndMerchantIgnoreCase(95000L, date, "Starbucks"))
                .thenReturn(List.of(existing));

        OcrResult ocr = new OcrResult("RECEIPT", "manual", null, date,
                "09:00:00", 95000L, "VND", "Food", "DISCRETIONARY", "Starbucks", null, 0.90, null);

        var result = financeService.checkDuplicate(ocr, null);
        assertTrue(result.hasDuplicate());
        assertTrue(result.warningMessage().contains("Identical expense"));
    }

    @Test
    void testCleanTransactionNoDuplicate() {
        LocalDate date = LocalDate.of(2026, 10, 20);
        when(txRepo.findFirstByImageSha256("sha-clean")).thenReturn(Optional.empty());
        when(txRepo.findByAmountAndTxDateAndMerchantIgnoreCase(35000L, date, "Bakery"))
                .thenReturn(List.of());

        OcrResult ocr = new OcrResult("RECEIPT", "manual", null, date,
                "07:30:00", 35000L, "VND", "Food", "DISCRETIONARY", "Bakery", null, 0.92, null);

        var result = financeService.checkDuplicate(ocr, "sha-clean");
        assertFalse(result.hasDuplicate());
        assertNull(result.warningMessage());
    }
}
