package com.ploa.finance.service;

import com.ploa.finance.api.dto.CreateDebtRequest;
import com.ploa.finance.api.dto.RecordDebtPaymentRequest;
import com.ploa.finance.engine.DebtAmortizationEngine;
import com.ploa.finance.engine.dto.DebtAmortizationResult;
import com.ploa.finance.engine.dto.PayoffSimulationComparison;
import com.ploa.ai.dto.DebtOcrResult;
import com.ploa.finance.api.dto.*;
import com.ploa.finance.engine.dto.AprAnalysisResult;
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
    private final com.ploa.ai.validation.OcrValidator ocrValidator;

    @org.springframework.beans.factory.annotation.Autowired
    public DebtService(
            DebtRepository debtRepo,
            DebtScheduleRepository scheduleRepo,
            DebtPaymentRepository paymentRepo,
            TransactionRepository transactionRepo,
            FinancialAccountRepository accountRepo,
            DebtAmortizationEngine amortizationEngine,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.ploa.ai.validation.OcrValidator ocrValidator
    ) {
        this.debtRepo = debtRepo;
        this.scheduleRepo = scheduleRepo;
        this.paymentRepo = paymentRepo;
        this.transactionRepo = transactionRepo;
        this.accountRepo = accountRepo;
        this.amortizationEngine = amortizationEngine;
        this.ocrValidator = ocrValidator;
    }

    public DebtService(
            DebtRepository debtRepo,
            DebtScheduleRepository scheduleRepo,
            DebtPaymentRepository paymentRepo,
            TransactionRepository transactionRepo,
            FinancialAccountRepository accountRepo,
            DebtAmortizationEngine amortizationEngine
    ) {
        this(debtRepo, scheduleRepo, paymentRepo, transactionRepo, accountRepo, amortizationEngine, null);
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

    /**
     * Previews and reconciles an extracted Debt OCR scan.
     * Evaluates true effective APR via IRR, matches existing liabilities, and derives recommended actions.
     */
    public static BigDecimal parseStatedApr(String rateStr, Double statedAprVal) {
        if (statedAprVal != null && statedAprVal > 0) {
            return BigDecimal.valueOf(statedAprVal).setScale(2, RoundingMode.HALF_UP);
        }
        if (rateStr == null || rateStr.isBlank()) {
            return null;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)([0-9]+(?:[.,][0-9]+)?)\\s*%\\s*(?:/\\s*(tháng|month|năm|year|m|y))?");
        java.util.regex.Matcher m = pattern.matcher(rateStr.trim());
        if (m.find()) {
            String numStr = m.group(1).replace(',', '.');
            double val = Double.parseDouble(numStr);
            String unit = m.group(2);
            if (unit != null && (unit.equalsIgnoreCase("tháng") || unit.equalsIgnoreCase("month") || unit.equalsIgnoreCase("m"))) {
                val = val * 12.0;
            }
            return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    /**
     * Attempts to reconstruct the original loan principal if only current remaining balance is shown,
     * using the origination/disbursement date, total term, and elapsed cycles.
     */
    public static BigDecimal deriveOriginalPrincipal(BigDecimal currentPrincipal, int termMonths, LocalDate originationDate, LocalDate statementDate, String note) {
        if (currentPrincipal == null || currentPrincipal.compareTo(BigDecimal.ZERO) <= 0 || termMonths <= 1) {
            return currentPrincipal;
        }

        LocalDate origDate = originationDate;
        if (origDate == null && note != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})").matcher(note);
            if (m.find()) {
                int d = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                int y = Integer.parseInt(m.group(3));
                origDate = LocalDate.of(y, mo, d);
            }
        }

        if (origDate == null) {
            return currentPrincipal;
        }

        LocalDate refDate = statementDate != null ? statementDate : LocalDate.now(ZONE);
        long elapsed = java.time.temporal.ChronoUnit.MONTHS.between(
                java.time.YearMonth.from(origDate),
                java.time.YearMonth.from(refDate)
        );

        if (elapsed <= 0 || elapsed >= termMonths) {
            return currentPrincipal;
        }

        // Evaluate candidate elapsed cycles (e.g. elapsed or elapsed - 1 due to 1st payment delay)
        for (long k : new long[]{elapsed - 1, elapsed, elapsed + 1}) {
            if (k <= 0 || k >= termMonths) continue;
            double remainingRatio = (double) (termMonths - k) / (double) termMonths;
            double estL0 = currentPrincipal.doubleValue() / remainingRatio;
            long roundedMillion = Math.round(estL0 / 1_000_000.0) * 1_000_000L;
            if (Math.abs(estL0 - roundedMillion) <= 400_000.0) {
                return BigDecimal.valueOf(roundedMillion);
            }
        }

        return currentPrincipal;
    }

    public record ReconstructedLoanTerms(BigDecimal originalPrincipal, int totalTermMonths) {}

    /**
     * Reconstructs original loan principal and tenure from regular annuity or flat monthly payment
     * and stated APR when evaluating an ongoing loan in its tail months.
     */
    public static ReconstructedLoanTerms deriveOriginalPrincipalFromPayment(BigDecimal currentPrincipal, BigDecimal monthlyPayment, BigDecimal statedApr, Integer remainingMonths) {
        if (currentPrincipal == null || monthlyPayment == null || statedApr == null || statedApr.compareTo(BigDecimal.ZERO) <= 0 || monthlyPayment.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        double P = monthlyPayment.doubleValue();
        double r = statedApr.doubleValue() / 12.0 / 100.0;
        int minMonths = (remainingMonths != null && remainingMonths > 0) ? remainingMonths : 1;

        // Test standard consumer loan tenures in months: 12, 18, 24, 30, 36, 48, 60
        int[] standardTenures = {12, 18, 24, 30, 36, 48, 60};
        for (int n : standardTenures) {
            if (n < minMonths) continue;

            // Annuity loan test: L0 = P * [1 - (1+r)^(-n)] / r
            double factor = Math.pow(1.0 + r, -n);
            double estL0Annuity = (P / r) * (1.0 - factor);
            long roundedTrancheAnnuity = Math.round(estL0Annuity / 5_000_000.0) * 5_000_000L;
            if (roundedTrancheAnnuity >= currentPrincipal.doubleValue() && Math.abs(estL0Annuity - roundedTrancheAnnuity) <= 50_000.0) {
                return new ReconstructedLoanTerms(BigDecimal.valueOf(roundedTrancheAnnuity), n);
            }

            // Flat loan test: P = L0 * (1/n + r_monthly) => L0 = P / (1/n + r_monthly)
            double estL0Flat = P / ((1.0 / n) + r);
            long roundedTrancheFlat = Math.round(estL0Flat / 5_000_000.0) * 5_000_000L;
            if (roundedTrancheFlat >= currentPrincipal.doubleValue() && Math.abs(estL0Flat - roundedTrancheFlat) <= 50_000.0) {
                return new ReconstructedLoanTerms(BigDecimal.valueOf(roundedTrancheFlat), n);
            }
        }

        return null;
    }

    /**
     * Resolves bank or lender name from creditor string, product name, or statement notes.
     */
    public static String detectCreditor(String creditor, String debtName, String note) {
        if (creditor != null && !creditor.isBlank() && !creditor.equalsIgnoreCase("Unknown Creditor")) {
            return creditor.trim();
        }
        String combined = ((debtName != null ? debtName : "") + " " + (note != null ? note : "")).toLowerCase();
        if (combined.contains("smartcredit")) return "SmartCredit";
        if (combined.contains("vpbank") || combined.contains("vpb")) return "VPBank";
        if (combined.contains("standard chartered") || combined.contains("001-786")) return "Standard Chartered";
        if (combined.contains("shinhan")) return "Shinhan Bank";
        if (combined.contains("techcombank") || combined.contains("tcb")) return "Techcombank";
        if (combined.contains("fe credit")) return "FE Credit";
        if (combined.contains("home credit")) return "Home Credit";
        if (combined.contains("mbbank") || combined.contains("mb bank")) return "MBBank";
        if (combined.contains("vietcombank") || combined.contains("vcb")) return "Vietcombank";
        if (combined.contains("tpbank")) return "TPBank";
        if (combined.contains("bidv")) return "BIDV";
        if (combined.contains("acb")) return "ACB";
        if (combined.contains("shopee") || combined.contains("spaylater")) return "Shopee SPayLater";
        if (combined.contains("momo")) return "MoMo";
        return "Unknown Creditor";
    }

    @Transactional(readOnly = true)
    public DebtScanPreviewResponse previewDebtScan(DebtOcrResult ocr) {
        if (ocr == null) {
            throw new IllegalArgumentException("OCR result must not be null");
        }

        List<Debt> activeDebts = getActiveDebts();
        Debt matchedDebt = findMatchingDebt(ocr, activeDebts);

        BigDecimal originalPrincipal = ocr.originalPrincipal() != null && ocr.originalPrincipal() > 0
                ? BigDecimal.valueOf(ocr.originalPrincipal())
                : null;

        BigDecimal currentPrincipal = ocr.currentPrincipal() != null && ocr.currentPrincipal() > 0
                ? BigDecimal.valueOf(ocr.currentPrincipal())
                : null;

        BigDecimal principal = currentPrincipal != null
                ? currentPrincipal
                : (originalPrincipal != null ? originalPrincipal : BigDecimal.ZERO);

        int termMonths = ocr.termMonths() != null && ocr.termMonths() > 0
                ? ocr.termMonths()
                : (ocr.remainingMonths() != null && ocr.remainingMonths() > 0 ? ocr.remainingMonths() : 12);

        BigDecimal monthlyDue = ocr.monthlyPayment() != null
                ? BigDecimal.valueOf(ocr.monthlyPayment())
                : BigDecimal.ZERO;

        BigDecimal statedApr = parseStatedApr(ocr.statedInterestRate(), ocr.statedApr());

        if (originalPrincipal == null || originalPrincipal.compareTo(principal) <= 0) {
            BigDecimal dateDerived = deriveOriginalPrincipal(principal, termMonths, ocr.originationDate(), ocr.statementDate(), ocr.note());
            if (dateDerived != null && dateDerived.compareTo(principal) > 0) {
                originalPrincipal = dateDerived;
            } else {
                ReconstructedLoanTerms paymentDerived = deriveOriginalPrincipalFromPayment(principal, monthlyDue, statedApr, ocr.remainingMonths());
                if (paymentDerived != null && paymentDerived.originalPrincipal().compareTo(principal) > 0) {
                    originalPrincipal = paymentDerived.originalPrincipal();
                    if (ocr.termMonths() == null || ocr.termMonths() < paymentDerived.totalTermMonths()) {
                        termMonths = paymentDerived.totalTermMonths();
                    }
                }
            }
        }

        if (originalPrincipal == null) {
            originalPrincipal = principal;
        }

        // Base calculation on original borrowing principal for contract terms
        BigDecimal calculationPrincipal = originalPrincipal.compareTo(BigDecimal.ZERO) > 0 ? originalPrincipal : principal;

        // Guard against hallucinated principal-only division (P = L / n) when a positive interest rate is stated
        boolean isHallucinatedZeroPayment = statedApr != null && statedApr.compareTo(BigDecimal.ZERO) > 0
                && monthlyDue.compareTo(BigDecimal.ZERO) > 0
                && (Math.abs(monthlyDue.doubleValue() * termMonths - principal.doubleValue()) <= 100.0
                    || Math.abs(monthlyDue.doubleValue() * termMonths - calculationPrincipal.doubleValue()) <= 100.0);

        // Auto-calculate regular monthly installment if missing, settled, or hallucinated as 0% interest
        if ((monthlyDue.compareTo(BigDecimal.ZERO) <= 0 || isHallucinatedZeroPayment)
                && calculationPrincipal.compareTo(BigDecimal.ZERO) > 0
                && statedApr != null && statedApr.compareTo(BigDecimal.ZERO) > 0) {
            boolean isMonthlyQuoted = ocr.statedInterestRate() != null && ocr.statedInterestRate().toLowerCase().matches(".*(tháng|month|/m).*");
            if (isMonthlyQuoted) {
                double monthlyRate = statedApr.doubleValue() / 12.0 / 100.0;
                double flatMonthlyPayment = (calculationPrincipal.doubleValue() / termMonths) + (calculationPrincipal.doubleValue() * monthlyRate);
                monthlyDue = BigDecimal.valueOf(flatMonthlyPayment).setScale(0, RoundingMode.HALF_UP);
            } else {
                monthlyDue = amortizationEngine.calculateAnnuityMonthlyPayment(calculationPrincipal, statedApr, termMonths);
            }
        }

        // Deterministic APR Analysis & IRR Derivation
        AprAnalysisResult aprAnalysis = amortizationEngine.analyzeApr(calculationPrincipal, monthlyDue, termMonths, statedApr);
        BigDecimal recommendedApr = aprAnalysis.effectiveApr();

        if (monthlyDue.compareTo(BigDecimal.ZERO) <= 0 && calculationPrincipal.compareTo(BigDecimal.ZERO) > 0) {
            monthlyDue = amortizationEngine.calculateAnnuityMonthlyPayment(calculationPrincipal, recommendedApr, termMonths);
        }

        // Determine Action and Matching semantics
        ReconciliationAction action;
        String validationStatus;
        String message;

        var valResult = ocrValidator != null ? ocrValidator.validateDebt(ocr) : null;
        String valNotice = (valResult != null && valResult.hasWarnings())
                ? " [⚠️ Review: " + String.join("; ", valResult.warnings()) + "]"
                : "";

        if (Boolean.TRUE.equals(ocr.isPaymentSlip())) {
            action = ReconciliationAction.RECORD_DEBT_PAYMENT;
            validationStatus = valResult != null && valResult.hasWarnings() ? "NEEDS_REVIEW" : "MATCHED_PAYMENT";
            message = (matchedDebt != null
                    ? String.format("Repayment slip matched to active liability '%s'.", matchedDebt.getName())
                    : "Repayment slip detected. Select an existing debt to apply payment.") + valNotice;
        } else if (matchedDebt != null) {
            if (ocr.debtType() == DebtType.REVOLVING_CREDIT || (ocr.currentPrincipal() != null && ocr.currentPrincipal() > 0)) {
                action = ReconciliationAction.UPDATE_DEBT_BALANCE;
                validationStatus = valResult != null && valResult.hasWarnings() ? "NEEDS_REVIEW" : "MATCHED_EXISTING_BALANCE";
                message = String.format("Matched existing liability '%s' (%s). Update balance to %s VND.",
                        matchedDebt.getName(), matchedDebt.getCreditor(), principal) + valNotice;
            } else {
                action = ReconciliationAction.CREATE_NEW_DEBT;
                validationStatus = valResult != null && valResult.hasWarnings() ? "NEEDS_REVIEW" : aprAnalysis.method();
                message = String.format("Similar creditor '%s' found, but terms indicate a new loan contract.", matchedDebt.getCreditor()) + valNotice;
            }
        } else {
            action = ReconciliationAction.CREATE_NEW_DEBT;
            if (valResult != null && valResult.hasWarnings()) {
                validationStatus = "NEEDS_REVIEW";
            } else if (valResult != null && valResult.status() == com.ploa.ai.validation.ValidationStatus.RECONSTRUCTED) {
                validationStatus = "RECONSTRUCTED";
            } else {
                validationStatus = aprAnalysis.method();
            }
            message = String.format("New debt detected: %s. Effective APR derived as %s%%.",
                    ocr.debtName() != null ? ocr.debtName() : "Loan", recommendedApr) + valNotice;
        }

        Integer dueDay = ocr.dueDayOfMonth() != null
                ? ocr.dueDayOfMonth()
                : (ocr.dueDate() != null ? ocr.dueDate().getDayOfMonth() : 5);

        LocalDate dueDate = ocr.dueDate();
        LocalDate maturityDate = ocr.statementDate() != null
                ? ocr.statementDate().plusMonths(termMonths)
                : LocalDate.now(ZONE).plusMonths(termMonths);

        String recommendedCreditor = detectCreditor(ocr.creditor(), ocr.debtName(), ocr.note());

        String recommendedName = ocr.debtName() != null && !ocr.debtName().isBlank()
                ? ocr.debtName()
                : (recommendedCreditor + " " + (ocr.debtType() != null ? ocr.debtType().name() : "Loan"));

        DebtType recommendedType = ocr.debtType() != null ? ocr.debtType() : DebtType.AMORTIZING_LOAN;

        return new DebtScanPreviewResponse(
                ocr,
                aprAnalysis,
                matchedDebt != null ? matchedDebt.getId() : null,
                matchedDebt != null ? matchedDebt.getName() : null,
                action,
                recommendedName,
                recommendedCreditor,
                recommendedType,
                originalPrincipal,
                principal,
                recommendedApr,
                monthlyDue,
                dueDay,
                dueDate,
                maturityDate,
                validationStatus,
                message
        );
    }

    /**
     * Commits the user-reviewed debt action (Creates, Updates Balance, or Records Payment).
     * Enforces the Zero-Silent-Write invariant.
     */
    @Transactional
    public Object confirmDebtScan(ConfirmDebtScanRequest req) {
        if (req == null || req.action() == null) {
            throw new IllegalArgumentException("Confirm request and action must not be null");
        }

        switch (req.action()) {
            case CREATE_NEW_DEBT -> {
                BigDecimal orig = req.originalPrincipal() != null && req.originalPrincipal().compareTo(BigDecimal.ZERO) > 0
                        ? req.originalPrincipal()
                        : req.principal();
                CreateDebtRequest createReq = new CreateDebtRequest(
                        req.name(),
                        req.creditor(),
                        req.debtType() != null ? req.debtType() : DebtType.AMORTIZING_LOAN,
                        "VND",
                        orig,
                        req.principal(),
                        req.apr(),
                        InterestAccrualConvention.ACTUAL_365,
                        req.monthlyDue(),
                        req.dueDayOfMonth() != null ? req.dueDayOfMonth() : 5,
                        LocalDate.now(ZONE),
                        req.maturityDate(),
                        req.notes() != null ? req.notes() : "Created via Document OCR"
                );
                return createDebt(createReq);
            }
            case UPDATE_DEBT_BALANCE -> {
                if (req.debtId() == null) {
                    throw new IllegalArgumentException("debtId is required to update balance");
                }
                Debt debt = getDebtById(req.debtId());
                if (req.principal() != null) {
                    debt.setCurrentPrincipal(req.principal());
                    if (req.principal().compareTo(BigDecimal.ZERO) <= 0) {
                        debt.setStatus(DebtStatus.PAID_OFF);
                    }
                }
                if (req.monthlyDue() != null && req.monthlyDue().compareTo(BigDecimal.ZERO) > 0) {
                    debt.setMonthlyMinimumDue(req.monthlyDue());
                }
                if (req.dueDayOfMonth() != null && req.dueDayOfMonth() > 0) {
                    debt.setDueDayOfMonth(req.dueDayOfMonth());
                }
                if (req.notes() != null) {
                    debt.setNotes((debt.getNotes() != null ? debt.getNotes() + "\n" : "") + req.notes());
                }
                log.info("Updated balance for debt {} ({}) to {} VND via OCR confirmation",
                        debt.getId(), debt.getName(), debt.getCurrentPrincipal());
                return debtRepo.save(debt);
            }
            case RECORD_DEBT_PAYMENT -> {
                if (req.debtId() == null) {
                    throw new IllegalArgumentException("debtId is required to record payment");
                }
                BigDecimal paymentAmount = req.paymentAmount() != null
                        ? req.paymentAmount()
                        : (req.monthlyDue() != null ? req.monthlyDue() : BigDecimal.ZERO);

                RecordDebtPaymentRequest paymentReq = new RecordDebtPaymentRequest(
                        paymentAmount,
                        null,
                        null,
                        null,
                        req.dueDate() != null ? req.dueDate() : LocalDate.now(ZONE),
                        null,
                        req.notes() != null ? req.notes() : "Payment recorded via Slip OCR"
                );
                return recordPayment(req.debtId(), paymentReq);
            }
            default -> throw new IllegalArgumentException("Unsupported action: " + req.action());
        }
    }

    private Debt findMatchingDebt(DebtOcrResult ocr, List<Debt> activeDebts) {
        if (activeDebts == null || activeDebts.isEmpty() || ocr == null) {
            return null;
        }

        String creditor = ocr.creditor() != null ? ocr.creditor().toLowerCase().trim() : "";
        String debtName = ocr.debtName() != null ? ocr.debtName().toLowerCase().trim() : "";

        for (Debt d : activeDebts) {
            String existingCreditor = d.getCreditor() != null ? d.getCreditor().toLowerCase().trim() : "";
            String existingName = d.getName() != null ? d.getName().toLowerCase().trim() : "";

            // Check exact or partial creditor match
            boolean creditorMatch = !creditor.isEmpty() && (existingCreditor.contains(creditor) || creditor.contains(existingCreditor));
            boolean nameMatch = !debtName.isEmpty() && (existingName.contains(debtName) || debtName.contains(existingName));

            if (creditorMatch && (ocr.debtType() == null || ocr.debtType() == d.getDebtType())) {
                return d;
            }
            if (nameMatch) {
                return d;
            }
        }
        return null;
    }
}
