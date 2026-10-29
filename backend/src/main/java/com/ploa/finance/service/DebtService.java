package com.ploa.finance.service;

import com.ploa.finance.api.dto.CreateDebtRequest;
import com.ploa.finance.api.dto.RecordDebtPaymentRequest;
import com.ploa.finance.engine.DebtAmortizationEngine;
import com.ploa.finance.engine.dto.DebtAmortizationResult;
import com.ploa.finance.engine.dto.PayoffSimulationComparison;
import com.ploa.finance.model.*;
import com.ploa.finance.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class DebtService {

    private static final Logger log = LoggerFactory.getLogger(DebtService.class);
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final UUID DEFAULT_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final DebtRepository debtRepo;
    private final DebtScheduleRepository scheduleRepo;
    private final DebtPaymentRepository paymentRepo;
    private final TransactionRepository transactionRepo;
    private final FinancialAccountRepository accountRepo;
    private final DebtAmortizationEngine amortizationEngine;

    public DebtService(
            DebtRepository debtRepo,
            DebtScheduleRepository scheduleRepo,
            DebtPaymentRepository paymentRepo,
            TransactionRepository transactionRepo,
            FinancialAccountRepository accountRepo,
            DebtAmortizationEngine amortizationEngine
    ) {
        this.debtRepo = debtRepo;
        this.scheduleRepo = scheduleRepo;
        this.paymentRepo = paymentRepo;
        this.transactionRepo = transactionRepo;
        this.accountRepo = accountRepo;
        this.amortizationEngine = amortizationEngine;
    }

    @Transactional
    public Debt createDebt(CreateDebtRequest req) {
        Debt debt = new Debt();
        debt.setName(req.name());
        debt.setCreditor(req.creditor());
        debt.setDebtType(req.debtType() != null ? req.debtType() : DebtType.AMORTIZING_LOAN);
        debt.setCurrency(req.currency() != null ? req.currency() : "VND");

        BigDecimal original = req.originalPrincipal() != null ? req.originalPrincipal() : BigDecimal.ZERO;
        debt.setOriginalPrincipal(original);
        debt.setCurrentPrincipal(req.currentPrincipal() != null ? req.currentPrincipal() : original);
        debt.setInterestRateApr(req.interestRateApr() != null ? req.interestRateApr() : BigDecimal.ZERO);
        debt.setInterestAccrualConvention(req.interestAccrualConvention() != null
                ? req.interestAccrualConvention()
                : InterestAccrualConvention.ACTUAL_365);
        debt.setDueDayOfMonth(req.dueDayOfMonth() != null ? req.dueDayOfMonth() : 1);
        debt.setOriginationDate(req.originationDate() != null ? req.originationDate() : LocalDate.now(ZONE));
        debt.setMaturityDate(req.maturityDate());
        debt.setStatus(DebtStatus.ACTIVE);
        debt.setNotes(req.notes());

        if (req.monthlyMinimumDue() != null && req.monthlyMinimumDue().compareTo(BigDecimal.ZERO) > 0) {
            debt.setMonthlyMinimumDue(req.monthlyMinimumDue());
        } else {
            int term = 12;
            if (req.maturityDate() != null) {
                long monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(debt.getOriginationDate(), req.maturityDate());
                term = Math.max(1, (int) monthsBetween);
            }
            debt.setMonthlyMinimumDue(amortizationEngine.calculateAnnuityMonthlyPayment(original, debt.getInterestRateApr(), term));
        }

        Debt saved = debtRepo.save(debt);

        // Pre-generate contractual schedule items
        DebtAmortizationResult scheduleResult = amortizationEngine.generateSchedule(saved, saved.getOriginationDate());
        if (scheduleResult.schedule() != null) {
            for (var item : scheduleResult.schedule()) {
                DebtSchedule schedule = new DebtSchedule();
                schedule.setDebt(saved);
                schedule.setInstallmentNumber(item.installmentNumber());
                schedule.setDueDate(item.dueDate());
                schedule.setExpectedPayment(item.payment());
                schedule.setExpectedPrincipal(item.principalPortion());
                schedule.setExpectedInterest(item.interestPortion());
                schedule.setRemainingPrincipalAfter(item.remainingPrincipal());
                schedule.setStatus(ScheduleStatus.UPCOMING);
                scheduleRepo.save(schedule);
            }
        }

        log.info("Debt created: id={}, name={}, principal={} VND, minDue={} VND",
                saved.getId(), saved.getName(), saved.getCurrentPrincipal(), saved.getMonthlyMinimumDue());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Debt> getActiveDebts() {
        return debtRepo.findByStatusOrderByMaturityDateAsc(DebtStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Debt getDebtById(UUID id) {
        return debtRepo.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Debt not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<DebtSchedule> getDebtSchedule(UUID debtId) {
        return scheduleRepo.findByDebtIdOrderByDueDateAsc(debtId);
    }

    @Transactional
    public DebtPayment recordPayment(UUID debtId, RecordDebtPaymentRequest req) {
        Debt debt = getDebtById(debtId);
        LocalDate paymentDate = req.paymentDate() != null ? req.paymentDate() : LocalDate.now(ZONE);
        BigDecimal feePortion = req.feePortion() != null ? req.feePortion() : BigDecimal.ZERO;
        BigDecimal principalPortion = req.principalPortion();
        BigDecimal interestPortion = req.interestPortion();
        BigDecimal computedTotal = req.totalPaid();
        if (computedTotal == null) {
            if (principalPortion != null) {
                computedTotal = principalPortion.add(interestPortion != null ? interestPortion : BigDecimal.ZERO).add(feePortion);
            } else {
                throw new IllegalArgumentException("totalPaid or principalPortion must be provided");
            }
        }
        final BigDecimal totalPaid = computedTotal;

        if (principalPortion == null || interestPortion == null) {
            BigDecimal effectiveApr = debt.getInterestRateApr().compareTo(BigDecimal.ONE) > 0
                    ? debt.getInterestRateApr().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                    : debt.getInterestRateApr();
            BigDecimal monthlyRate = effectiveApr.divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
            interestPortion = debt.getCurrentPrincipal().multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            principalPortion = totalPaid.subtract(interestPortion).subtract(feePortion).max(BigDecimal.ZERO);
            principalPortion = principalPortion.min(debt.getCurrentPrincipal());
        }

        BigDecimal remainingPrincipal = debt.getCurrentPrincipal().subtract(principalPortion).max(BigDecimal.ZERO);

        // 1. Create DebtPayment record
        DebtPayment payment = new DebtPayment();
        payment.setDebt(debt);
        payment.setPaymentDate(paymentDate);
        payment.setTotalPaid(totalPaid);
        payment.setPrincipalPortion(principalPortion);
        payment.setInterestPortion(interestPortion);
        payment.setFeePortion(feePortion);
        payment.setRemainingPrincipalSnapshot(remainingPrincipal);
        payment.setNote(req.note());
        DebtPayment savedPayment = paymentRepo.save(payment);

        // 2. Update Debt balance
        debt.setCurrentPrincipal(remainingPrincipal);
        if (remainingPrincipal.compareTo(BigDecimal.ZERO) == 0) {
            debt.setStatus(DebtStatus.PAID_OFF);
            log.info("Debt {} ({}) is fully PAID OFF!", debt.getId(), debt.getName());
        }
        debtRepo.save(debt);

        // 3. Mark earliest UPCOMING schedule row as PAID
        List<DebtSchedule> upcoming = scheduleRepo.findByDebtIdOrderByDueDateAsc(debtId).stream()
                .filter(s -> s.getStatus() == ScheduleStatus.UPCOMING)
                .toList();
        if (!upcoming.isEmpty()) {
            DebtSchedule nextDue = upcoming.get(0);
            nextDue.setStatus(ScheduleStatus.PAID);
            scheduleRepo.save(nextDue);
        }

        // 4. Update Cash Account
        UUID accountId = req.accountId() != null ? req.accountId() : DEFAULT_ACCOUNT_ID;
        accountRepo.findById(accountId).ifPresent(acc -> {
            acc.setCurrentBalance(acc.getCurrentBalance().subtract(totalPaid));
            accountRepo.save(acc);
        });

        // 5. Anti-Double-Counting Ledger Entries:
        // Principal Payment (Balance Sheet activity, NOT operating spend)
        if (principalPortion.compareTo(BigDecimal.ZERO) > 0) {
            Transaction pTx = new Transaction();
            pTx.setAccountId(accountId);
            pTx.setDebtId(debtId);
            pTx.setTransactionType(TransactionType.DEBT_PRINCIPAL_PAYMENT);
            pTx.setAmount(principalPortion.longValue());
            pTx.setSpendType(null); // Explicitly null for non-expense balance-sheet transfers
            pTx.setCategory("Debt Principal");
            pTx.setMerchant(debt.getCreditor());
            pTx.setTxDate(paymentDate);
            pTx.setNote("Principal payment for " + debt.getName());
            pTx.setConfirmed(true);
            Transaction savedPTx = transactionRepo.save(pTx);
            if (savedPTx != null) {
                savedPayment.setTransactionId(savedPTx.getId());
                paymentRepo.save(savedPayment);
            }
        }

        // Interest & Fees (Operating Expense)
        BigDecimal financingCost = interestPortion.add(feePortion);
        if (financingCost.compareTo(BigDecimal.ZERO) > 0) {
            Transaction iTx = new Transaction();
            iTx.setAccountId(accountId);
            iTx.setDebtId(debtId);
            iTx.setTransactionType(TransactionType.DEBT_INTEREST_AND_FEE);
            iTx.setAmount(financingCost.longValue());
            iTx.setSpendType(SpendType.FIXED);
            iTx.setCategory("Interest & Bank Fees");
            iTx.setMerchant(debt.getCreditor());
            iTx.setTxDate(paymentDate);
            iTx.setNote("Interest/Fee for " + debt.getName());
            iTx.setConfirmed(true);
            transactionRepo.save(iTx);
        }

        log.info("Payment recorded for debt {}: totalPaid={}, principal={}, interest={}, remainingPrincipal={}",
                debt.getName(), totalPaid, principalPortion, interestPortion, remainingPrincipal);

        return savedPayment;
    }

    @Transactional(readOnly = true)
    public PayoffSimulationComparison simulatePayoff(BigDecimal monthlySurplusCash) {
        List<Debt> activeDebts = getActiveDebts();
        return amortizationEngine.simulatePayoff(activeDebts, monthlySurplusCash, LocalDate.now(ZONE));
    }
}
