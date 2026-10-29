package com.ploa.finance.service;

import com.ploa.finance.api.dto.CreateIncomeRequest;
import com.ploa.finance.model.*;
import com.ploa.finance.repository.FinancialAccountRepository;
import com.ploa.finance.repository.IncomeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class IncomeService {

    private static final Logger log = LoggerFactory.getLogger(IncomeService.class);
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final IncomeRepository incomeRepo;
    private final FinancialAccountRepository accountRepo;

    public IncomeService(IncomeRepository incomeRepo, FinancialAccountRepository accountRepo) {
        this.incomeRepo = incomeRepo;
        this.accountRepo = accountRepo;
    }

    @Transactional
    public Income createIncome(CreateIncomeRequest req) {
        Income income = new Income();
        income.setSource(req.source());
        income.setExpectedAmount(req.expectedAmount() != null ? req.expectedAmount() : BigDecimal.ZERO);
        income.setActualAmount(req.actualAmount());
        income.setExpectedDate(req.expectedDate() != null ? req.expectedDate() : LocalDate.now(ZONE));
        income.setReceivedDate(req.receivedDate());
        income.setIncomeType(req.incomeType() != null ? req.incomeType() : IncomeType.SALARY);
        income.setConfidenceFactor(req.confidenceFactor() != null ? req.confidenceFactor() : 1.0);
        income.setRecurring(req.isRecurring() != null ? req.isRecurring() : false);
        income.setFrequency(req.frequency() != null ? req.frequency() : IncomeFrequency.MONTHLY);
        income.setStatus(req.status() != null ? req.status() : (req.actualAmount() != null ? IncomeStatus.RECEIVED : IncomeStatus.EXPECTED));
        income.setNote(req.notes());

        Income saved = incomeRepo.save(income);
        log.info("Income recorded: id={}, source={}, expected={}, status={}",
                saved.getId(), saved.getSource(), saved.getExpectedAmount(), saved.getStatus());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Income> getIncomes(LocalDate from, LocalDate to, IncomeStatus status) {
        LocalDate startDate = from != null ? from : LocalDate.now(ZONE).withDayOfMonth(1);
        LocalDate endDate = to != null ? to : startDate.plusMonths(1);

        if (status == IncomeStatus.RECEIVED) {
            return incomeRepo.findByReceivedDateBetweenOrderByReceivedDateDesc(startDate, endDate);
        } else if (status != null) {
            return incomeRepo.findByExpectedDateBetweenAndStatus(startDate, endDate, status);
        } else {
            return incomeRepo.findAll();
        }
    }

    @Transactional
    public Income markAsReceived(UUID id, BigDecimal actualAmount, LocalDate receivedDate, UUID accountId) {
        Income income = incomeRepo.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Income record not found: " + id));

        LocalDate date = receivedDate != null ? receivedDate : LocalDate.now(ZONE);
        BigDecimal amount = actualAmount != null ? actualAmount : income.getExpectedAmount();

        income.setStatus(IncomeStatus.RECEIVED);
        income.setActualAmount(amount);
        income.setReceivedDate(date);

        if (accountId != null) {
            accountRepo.findById(accountId).ifPresent(acc -> {
                acc.setCurrentBalance(acc.getCurrentBalance().add(amount));
                accountRepo.save(acc);
                log.info("Credited {} VND to account {}", amount, acc.getName());
            });
        }

        Income saved = incomeRepo.save(income);
        log.info("Income {} marked as received: amount={} on {}", id, amount, date);
        return saved;
    }

    @Transactional
    public void deleteIncome(UUID id) {
        incomeRepo.deleteById(id);
        log.info("Income deleted: {}", id);
    }
}
