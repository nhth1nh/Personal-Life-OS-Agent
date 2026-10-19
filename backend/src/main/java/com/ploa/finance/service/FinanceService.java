package com.ploa.finance.service;

import com.ploa.finance.engine.BurnRateEngine;
import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class FinanceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

    private final TransactionRepository repo;
    private final BurnRateEngine burnRateEngine;

    public FinanceService(TransactionRepository repo, BurnRateEngine burnRateEngine) {
        this.repo = repo;
        this.burnRateEngine = burnRateEngine;
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
        return repo.save(tx);
    }

    @Transactional
    public Transaction updateCategory(UUID id, String newCategory, SpendType newSpendType) {
        Transaction tx = repo.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Transaction not found: " + id));
        tx.setCategory(newCategory);
        tx.setSpendType(newSpendType);
        return repo.save(tx);
    }

    @Transactional
    public void delete(UUID id) {
        repo.deleteById(id);
        log.info("Transaction deleted: id={}", id);
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
