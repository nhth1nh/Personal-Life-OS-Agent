package com.ploa.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploa.ai.dto.OcrResult;
import com.ploa.finance.api.dto.OcrMetricsResponse;
import com.ploa.finance.engine.BurnRateEngine;
import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.model.OcrAudit;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.repository.OcrAuditRepository;
import com.ploa.finance.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FinanceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TransactionRepository repo;
    private final OcrAuditRepository ocrAuditRepo;
    private final BurnRateEngine burnRateEngine;

    public record DuplicateCheckResult(boolean hasDuplicate, String warningMessage, Transaction matchedTransaction) {}

    public FinanceService(TransactionRepository repo,
                          OcrAuditRepository ocrAuditRepo,
                          BurnRateEngine burnRateEngine) {
        this.repo = repo;
        this.ocrAuditRepo = ocrAuditRepo;
        this.burnRateEngine = burnRateEngine;
    }

    /**
     * Check if a transaction is a potential duplicate across 3 tiers:
     * 1. Exact image SHA-256 match
     * 2. Invoice / banking reference number match
     * 3. Fuzzy match: same amount + date + merchant
     */
    @Transactional(readOnly = true)
    public DuplicateCheckResult checkDuplicate(OcrResult ocr, String imageSha256) {
        if (imageSha256 != null && !imageSha256.isBlank()) {
            Optional<Transaction> exactImg = repo.findFirstByImageSha256(imageSha256);
            if (exactImg.isPresent()) {
                Transaction m = exactImg.get();
                return new DuplicateCheckResult(true,
                        "Exact image already scanned on " + m.getTxDate() + " (" + m.getMerchant() + ", " + m.getAmount() + " VND)",
                        m);
            }
        }

        if (ocr != null && ocr.invoiceNumber() != null && !ocr.invoiceNumber().isBlank()
                && ocr.merchant() != null && !ocr.merchant().isBlank()) {
            List<Transaction> invMatches = repo.findByInvoiceNumberIgnoreCaseAndMerchantIgnoreCase(ocr.invoiceNumber(), ocr.merchant());
            if (!invMatches.isEmpty()) {
                Transaction m = invMatches.get(0);
                return new DuplicateCheckResult(true,
                        "Invoice/Order ID (" + ocr.invoiceNumber() + ") already logged on " + m.getTxDate(),
                        m);
            }
        }

        if (ocr != null && ocr.totalAmount() != null && ocr.transactionDate() != null) {
            if (ocr.merchant() != null && !ocr.merchant().isBlank()) {
                List<Transaction> tripletMatches = repo.findByAmountAndTxDateAndMerchantIgnoreCase(
                        ocr.totalAmount(), ocr.transactionDate(), ocr.merchant());
                if (!tripletMatches.isEmpty()) {
                    Transaction m = tripletMatches.get(0);
                    return new DuplicateCheckResult(true,
                        "Identical expense (" + m.getAmount() + " VND at " + m.getMerchant() + ") already recorded on " + m.getTxDate(),
                        m);
                }
            } else {
                List<Transaction> amountDateMatches = repo.findByAmountAndTxDate(ocr.totalAmount(), ocr.transactionDate());
                if (!amountDateMatches.isEmpty()) {
                    Transaction m = amountDateMatches.get(0);
                    return new DuplicateCheckResult(true,
                        "Identical amount (" + m.getAmount() + " VND) on " + m.getTxDate() + " already exists (" + m.getCategory() + ")",
                        m);
                }
            }
        }

        return new DuplicateCheckResult(false, null, null);
    }

    /**
     * Record an initial OCR scan in the ocr_audits table.
     */
    @Transactional
    public OcrAudit recordOcrScan(Transaction tx, OcrResult ocr, String imageSha256) {
        OcrAudit audit = new OcrAudit();
        audit.setTransactionId(tx != null ? tx.getId() : null);
        audit.setImageSha256(imageSha256);
        audit.setInvoiceNumber(ocr != null ? ocr.invoiceNumber() : null);
        audit.setStatus("PENDING");
        audit.setInitialAmount(ocr != null ? ocr.totalAmount() : null);
        audit.setInitialMerchant(ocr != null ? ocr.merchant() : null);
        audit.setInitialCategory(ocr != null ? ocr.category() : null);
        audit.setInitialSpendType(ocr != null ? ocr.spendType() : null);
        audit.setConfidenceScore(ocr != null ? ocr.confidence() : null);
        audit.setDocumentType(ocr != null ? ocr.documentType() : null);
        audit.setSourcePlatform(ocr != null ? ocr.sourcePlatform() : null);
        if (ocr != null) {
            try {
                audit.setRawOcrJson(MAPPER.valueToTree(ocr));
            } catch (Exception ignored) {}
        }
        return ocrAuditRepo.save(audit);
    }

    @Transactional
    public Transaction save(Transaction tx) {
        Transaction saved = repo.save(tx);
        log.info("Transaction saved: id={}, amount={} VND, category={}, type={}",
                saved.getId(), saved.getAmount(), saved.getCategory(), saved.getSpendType());
        return saved;
    }

    @Transactional
    public Transaction confirm(UUID id) {
        Transaction tx = repo.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Transaction not found: " + id));
        tx.setConfirmed(true);
        Transaction saved = repo.save(tx);

        ocrAuditRepo.findByTransactionId(id).ifPresent(audit -> {
            audit.setStatus(audit.getEditCount() == 0 ? "CONFIRMED_AS_IS" : "CONFIRMED_MODIFIED");
            audit.setFinalAmount(saved.getAmount());
            audit.setFinalMerchant(saved.getMerchant());
            audit.setFinalCategory(saved.getCategory());
            audit.setFinalSpendType(saved.getSpendType().name());
            audit.setResolvedAt(OffsetDateTime.now());
            ocrAuditRepo.save(audit);
        });

        return saved;
    }

    @Transactional
    public Transaction applyCorrection(UUID txId, OcrResult refined, String userInstruction) {
        Transaction tx = repo.findById(txId).orElseThrow(() ->
                new IllegalArgumentException("Transaction not found: " + txId));

        if (refined.totalAmount() != null) tx.setAmount(refined.totalAmount());
        if (refined.merchant() != null) tx.setMerchant(refined.merchant());
        if (refined.category() != null) tx.setCategory(refined.category());
        if (refined.spendType() != null) {
            try {
                tx.setSpendType(SpendType.valueOf(refined.spendType()));
            } catch (Exception ignored) {}
        }
        if (refined.transactionDate() != null) tx.setTxDate(refined.transactionDate());
        if (refined.invoiceNumber() != null) tx.setInvoiceNumber(refined.invoiceNumber());
        if (refined.note() != null) tx.setNote(refined.note());

        Transaction saved = repo.save(tx);

        ocrAuditRepo.findByTransactionId(txId).ifPresent(audit -> {
            audit.setEditCount(audit.getEditCount() + 1);
            String existingNotes = audit.getUserNotes();
            audit.setUserNotes((existingNotes != null && !existingNotes.isBlank())
                    ? existingNotes + " | " + userInstruction
                    : userInstruction);
            ocrAuditRepo.save(audit);
        });

        log.info("Transaction {} updated via refinement: amount={}, merchant={}, category={}",
                txId, saved.getAmount(), saved.getMerchant(), saved.getCategory());
        return saved;
    }

    @Transactional
    public Transaction updateCategory(UUID id, String newCategory, SpendType newSpendType) {
        Transaction tx = repo.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Transaction not found: " + id));
        tx.setCategory(newCategory);
        tx.setSpendType(newSpendType);
        Transaction saved = repo.save(tx);

        ocrAuditRepo.findByTransactionId(id).ifPresent(audit -> {
            audit.setEditCount(audit.getEditCount() + 1);
            ocrAuditRepo.save(audit);
        });

        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        ocrAuditRepo.findByTransactionId(id).ifPresent(audit -> {
            audit.setStatus("DISCARDED");
            audit.setResolvedAt(OffsetDateTime.now());
            ocrAuditRepo.save(audit);
        });

        repo.deleteById(id);
        log.info("Transaction deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public Optional<Transaction> findByTelegramMsgId(Long telegramMsgId) {
        return repo.findFirstByTelegramMsgId(telegramMsgId);
    }

    @Transactional(readOnly = true)
    public Optional<Transaction> findById(UUID id) {
        return repo.findById(id);
    }

    @Transactional(readOnly = true)
    public OcrMetricsResponse getOcrMetrics() {
        long total = ocrAuditRepo.count();
        long asIs = ocrAuditRepo.countByStatus("CONFIRMED_AS_IS");
        long modified = ocrAuditRepo.countByStatus("CONFIRMED_MODIFIED");
        long discarded = ocrAuditRepo.countByStatus("DISCARDED");
        long pending = ocrAuditRepo.countByStatus("PENDING");
        Double avgConf = ocrAuditRepo.getAverageConfidence();

        double asIsPct = total > 0 ? (asIs * 100.0 / total) : 0.0;
        double modPct = total > 0 ? (modified * 100.0 / total) : 0.0;
        double discardPct = total > 0 ? (discarded * 100.0 / total) : 0.0;

        return new OcrMetricsResponse(
                total,
                asIs,
                modified,
                discarded,
                pending,
                Math.round(asIsPct * 100.0) / 100.0,
                Math.round(modPct * 100.0) / 100.0,
                Math.round(discardPct * 100.0) / 100.0,
                avgConf != null ? Math.round(avgConf * 100.0) / 100.0 : 0.0
        );
    }

    @Transactional(readOnly = true)
    public BurnRateResult getBurnRate() {
        return burnRateEngine.compute();
    }

    @Transactional(readOnly = true)
    public List<Transaction> getRecentTransactions(int limit) {
        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate today = LocalDate.now();
        List<Transaction> list = repo.findByTxDateBetweenOrderByTxDateDesc(startOfMonth, today);
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsForDate(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return repo.findByTxDateBetweenOrderByTxDateDesc(target, target);
    }

    @Transactional(readOnly = true)
    public Long getSpentForDate(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        return repo.sumConfirmedAmountBetween(target, target);
    }
}
