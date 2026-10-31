package com.ploa.finance;

import com.ploa.ai.dto.DebtOcrResult;
import com.ploa.finance.api.dto.*;
import com.ploa.finance.api.dto.CreateDebtRequest;
import com.ploa.finance.api.dto.RecordDebtPaymentRequest;
import com.ploa.finance.engine.DebtAmortizationEngine;
import com.ploa.finance.model.*;
import com.ploa.finance.repository.*;
import com.ploa.finance.service.DebtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DebtServiceTest {

    private DebtRepository debtRepo;
    private DebtScheduleRepository scheduleRepo;
    private DebtPaymentRepository paymentRepo;
    private TransactionRepository transactionRepo;
    private FinancialAccountRepository accountRepo;
    private DebtAmortizationEngine amortizationEngine;
    private DebtService debtService;

    @BeforeEach
    void setUp() {
        debtRepo = mock(DebtRepository.class);
        scheduleRepo = mock(DebtScheduleRepository.class);
        paymentRepo = mock(DebtPaymentRepository.class);
        transactionRepo = mock(TransactionRepository.class);
        accountRepo = mock(FinancialAccountRepository.class);
        amortizationEngine = new DebtAmortizationEngine();

        when(transactionRepo.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            if (t.getId() == null) t.setId(UUID.randomUUID());
            return t;
        });

        debtService = new DebtService(
                debtRepo,
                scheduleRepo,
                paymentRepo,
                transactionRepo,
                accountRepo,
                amortizationEngine
        );
    }

    @Test
    @DisplayName("Should create debt and pre-generate contractual amortization schedule")
    void testCreateDebt() {
        CreateDebtRequest req = new CreateDebtRequest(
                "Home Loan",
                "Vietcombank",
                DebtType.AMORTIZING_LOAN,
                "VND",
                BigDecimal.valueOf(100_000_000),
                BigDecimal.valueOf(100_000_000),
                BigDecimal.valueOf(0.08), // 8%
                InterestAccrualConvention.THIRTY_360,
                BigDecimal.valueOf(5_000_000),
                10,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2028, 1, 1),
                "Mortgage portion"
        );

        when(debtRepo.save(any(Debt.class))).thenAnswer(invocation -> {
            Debt d = invocation.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });

        Debt created = debtService.createDebt(req);

        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo("Home Loan");
        assertThat(created.getStatus()).isEqualTo(DebtStatus.ACTIVE);
        verify(debtRepo, times(1)).save(any(Debt.class));
        verify(scheduleRepo, atLeastOnce()).save(any(DebtSchedule.class));
    }

    @Test
    @DisplayName("Anti-Double-Counting Invariant: Payment must split principal (non-operating) and interest (FIXED) ledger transactions")
    void testRecordPaymentAntiDoubleCounting() {
        UUID debtId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Debt debt = new Debt();
        debt.setId(debtId);
        debt.setName("MacBook Installment");
        debt.setCreditor("Apple Store");
        debt.setCurrentPrincipal(BigDecimal.valueOf(20_000_000));
        debt.setInterestRateApr(BigDecimal.valueOf(0.12)); // 12% APR -> 1% monthly
        debt.setMonthlyMinimumDue(BigDecimal.valueOf(3_000_000));
        debt.setStatus(DebtStatus.ACTIVE);

        FinancialAccount account = new FinancialAccount();
        account.setId(accountId);
        account.setName("Techcombank Main");
        account.setCurrentBalance(BigDecimal.valueOf(50_000_000));

        when(debtRepo.findById(debtId)).thenReturn(Optional.of(debt));
        when(accountRepo.findById(accountId)).thenReturn(Optional.of(account));
        when(paymentRepo.save(any(DebtPayment.class))).thenAnswer(i -> i.getArgument(0));

        // Pay 3,000,000 VND: 2,800,000 Principal + 200,000 Interest
        RecordDebtPaymentRequest req = new RecordDebtPaymentRequest(
                BigDecimal.valueOf(3_000_000),
                BigDecimal.valueOf(2_800_000),
                BigDecimal.valueOf(200_000),
                BigDecimal.ZERO,
                LocalDate.of(2026, 4, 15),
                accountId,
                "Month 1 payment"
        );

        DebtPayment payment = debtService.recordPayment(debtId, req);

        assertThat(payment).isNotNull();
        assertThat(payment.getRemainingPrincipalSnapshot()).isEqualByComparingTo(BigDecimal.valueOf(17_200_000));

        // Account balance debited
        assertThat(account.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(47_000_000));

        // Verify ledger transactions created:
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepo, times(2)).save(txCaptor.capture());
        List<Transaction> savedTxs = txCaptor.getAllValues();

        // 1st Transaction: Principal portion -> DEBT_PRINCIPAL_PAYMENT with null spendType (excluded from operating spend)
        Transaction principalTx = savedTxs.stream()
                .filter(t -> t.getTransactionType() == TransactionType.DEBT_PRINCIPAL_PAYMENT)
                .findFirst().orElseThrow();
        assertThat(principalTx.getAmount()).isEqualTo(2_800_000L);
        assertThat(principalTx.getSpendType()).isNull();

        // 2nd Transaction: Interest portion -> DEBT_INTEREST_AND_FEE with SpendType.FIXED
        Transaction interestTx = savedTxs.stream()
                .filter(t -> t.getTransactionType() == TransactionType.DEBT_INTEREST_AND_FEE)
                .findFirst().orElseThrow();
        assertThat(interestTx.getAmount()).isEqualTo(200_000L);
        assertThat(interestTx.getSpendType()).isEqualTo(SpendType.FIXED);
    }

    @Test
    @DisplayName("Should mark debt as PAID_OFF when remaining principal reaches zero")
    void testDebtPaidOff() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt();
        debt.setId(debtId);
        debt.setName("Small Loan");
        debt.setCurrentPrincipal(BigDecimal.valueOf(1_000_000));
        debt.setInterestRateApr(BigDecimal.ZERO);
        debt.setStatus(DebtStatus.ACTIVE);

        when(debtRepo.findById(debtId)).thenReturn(Optional.of(debt));
        when(accountRepo.findById(any())).thenReturn(Optional.empty());
        when(paymentRepo.save(any(DebtPayment.class))).thenAnswer(i -> i.getArgument(0));

        RecordDebtPaymentRequest req = new RecordDebtPaymentRequest(
                BigDecimal.valueOf(1_000_000),
                BigDecimal.valueOf(1_000_000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDate.of(2026, 4, 15),
                null,
                "Final payoff"
        );

        debtService.recordPayment(debtId, req);

        assertThat(debt.getCurrentPrincipal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.PAID_OFF);
    }

    @Test
    @DisplayName("Should preview OCR debt scan and derive true effective APR via IRR when unstated")
    void testPreviewDebtScanNewLoanWithDerivedApr() {
        when(debtRepo.findByStatusOrderByMaturityDateAsc(DebtStatus.ACTIVE)).thenReturn(List.of());

        DebtOcrResult ocr = new DebtOcrResult(
                "AMORTIZING_LOAN_CONTRACT",
                "Shinhan Bank",
                "Consumer Loan",
                DebtType.AMORTIZING_LOAN,
                24_000_000L,
                24_000_000L,
                2_200_000L,
                12,
                12,
                null,
                null,
                5,
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 10, 1),
                false,
                null,
                "Contract #1234",
                0.95
        );

        DebtScanPreviewResponse preview = debtService.previewDebtScan(ocr);

        assertThat(preview.action()).isEqualTo(ReconciliationAction.CREATE_NEW_DEBT);
        assertThat(preview.recommendedPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(24_000_000));
        assertThat(preview.recommendedApr()).isBetween(BigDecimal.valueOf(17.8), BigDecimal.valueOf(18.2));
        assertThat(preview.aprAnalysis().method()).isEqualTo("DERIVED_IRR");
        assertThat(preview.recommendedDueDay()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should match active credit card statement and recommend balance update")
    void testPreviewDebtScanMatchedCreditCardUpdate() {
        UUID cardId = UUID.randomUUID();
        Debt existingCard = new Debt();
        existingCard.setId(cardId);
        existingCard.setName("VPBank StepUp Visa");
        existingCard.setCreditor("VPBank");
        existingCard.setDebtType(DebtType.REVOLVING_CREDIT);
        existingCard.setCurrentPrincipal(BigDecimal.valueOf(5_000_000));
        existingCard.setStatus(DebtStatus.ACTIVE);

        when(debtRepo.findByStatusOrderByMaturityDateAsc(DebtStatus.ACTIVE)).thenReturn(List.of(existingCard));

        DebtOcrResult ocr = new DebtOcrResult(
                "CREDIT_CARD_STATEMENT",
                "VPBank",
                "VPBank Visa",
                DebtType.REVOLVING_CREDIT,
                null,
                15_200_000L, // new statement balance
                760_000L,    // minimum payment
                null,
                null,
                "28%/năm",
                28.0,
                25,
                LocalDate.of(2026, 11, 25),
                LocalDate.of(2026, 10, 20),
                false,
                null,
                "Statement Oct 2026",
                0.98
        );

        DebtScanPreviewResponse preview = debtService.previewDebtScan(ocr);

        assertThat(preview.action()).isEqualTo(ReconciliationAction.UPDATE_DEBT_BALANCE);
        assertThat(preview.matchedDebtId()).isEqualTo(cardId);
        assertThat(preview.recommendedPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(15_200_000));
        assertThat(preview.recommendedMonthlyDue()).isEqualByComparingTo(BigDecimal.valueOf(760_000));
    }

    @Test
    @DisplayName("Should confirm debt scan by updating active debt balance")
    void testConfirmDebtScanUpdateBalance() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt();
        debt.setId(debtId);
        debt.setName("Credit Card");
        debt.setCurrentPrincipal(BigDecimal.valueOf(5_000_000));
        debt.setStatus(DebtStatus.ACTIVE);

        when(debtRepo.findById(debtId)).thenReturn(Optional.of(debt));
        when(debtRepo.save(any(Debt.class))).thenAnswer(i -> i.getArgument(0));

        ConfirmDebtScanRequest req = new ConfirmDebtScanRequest(
                ReconciliationAction.UPDATE_DEBT_BALANCE,
                debtId,
                null,
                null,
                null,
                BigDecimal.valueOf(15_200_000),
                null,
                BigDecimal.valueOf(760_000),
                25,
                null,
                null,
                null,
                "Updated via Oct statement scan"
        );

        Object result = debtService.confirmDebtScan(req);
        assertThat(result).isInstanceOf(Debt.class);
        Debt updated = (Debt) result;
        assertThat(updated.getCurrentPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(15_200_000));
        assertThat(updated.getMonthlyMinimumDue()).isEqualByComparingTo(BigDecimal.valueOf(760_000));
        assertThat(updated.getDueDayOfMonth()).isEqualTo(25);
    }

    @Test
    @DisplayName("Should correctly parse monthly rate 1.72%/thang, calculate installment, and expose true APR on 40m loan")
    void testPreviewDebtScanMonthlyRateLoan() {
        DebtOcrResult ocr = new DebtOcrResult(
                "AMORTIZING_LOAN_CONTRACT",
                "VPBank",
                "Vay tiền mặt trung hạn",
                DebtType.AMORTIZING_LOAN,
                40_000_000L,
                30_964_757L,
                null, // 0 VND paid in current period
                18,
                null,
                "1.72%/tháng",
                null, // model might omit stated_apr
                5,
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 5, 26),
                false,
                null,
                "Disbursement 26/05/2026",
                0.95
        );

        DebtScanPreviewResponse preview = debtService.previewDebtScan(ocr);

        assertThat(preview.action()).isEqualTo(ReconciliationAction.CREATE_NEW_DEBT);
        assertThat(preview.recommendedOriginalPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(40_000_000));
        assertThat(preview.recommendedPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(30_964_757));
        assertThat(preview.aprAnalysis().isZeroInterest()).isFalse();
        // Stated monthly 1.72% * 12 = 20.64% flat -> True effective declining APR is ~36.09%
        assertThat(preview.recommendedApr()).isGreaterThan(BigDecimal.valueOf(20.0));
        assertThat(preview.recommendedMonthlyDue()).isGreaterThan(BigDecimal.valueOf(2_500_000));
    }

    @Test
    @DisplayName("Should reconstruct 40m original principal and override hallucinated principal-only payment")
    void testPreviewDebtScanReconstructOriginalPrincipalAndOverrideZeroPayment() {
        DebtOcrResult ocr = new DebtOcrResult(
                "AMORTIZING_LOAN_CONTRACT",
                "VPBank",
                "Vay tiền mặt trung hạn",
                DebtType.AMORTIZING_LOAN,
                null, // Original loan not on screen
                30_964_757L, // Remaining balance
                1_720_264L, // Hallucinated principal-only payment (30964757 / 18)
                18,
                null,
                "1.72%/tháng",
                20.64,
                5,
                LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 10, 1),
                false,
                null,
                "Ngày giải ngân 26/05/2026",
                0.95
        );

        DebtScanPreviewResponse preview = debtService.previewDebtScan(ocr);

        assertThat(preview.action()).isEqualTo(ReconciliationAction.CREATE_NEW_DEBT);
        assertThat(preview.recommendedOriginalPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(40_000_000));
        assertThat(preview.recommendedPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(30_964_757));
        assertThat(preview.aprAnalysis().isZeroInterest()).isFalse();
        assertThat(preview.aprAnalysis().method()).isEqualTo("FLAT_RATE_EXPOSED");
        assertThat(preview.recommendedApr()).isBetween(BigDecimal.valueOf(35.0), BigDecimal.valueOf(37.0));
        assertThat(preview.recommendedMonthlyDue()).isEqualByComparingTo(BigDecimal.valueOf(2_910_222));
    }

    @Test
    @DisplayName("Should reconstruct 100m loan from 4.92m payment, detect Standard Chartered, and retain 16.49% stated APR")
    void testPreviewDebtScanPersonalInstalmentLoanTailPeriod() {
        DebtOcrResult ocr = new DebtOcrResult(
                "AMORTIZING_LOAN_CONTRACT",
                null,
                "Personal Instalment Loan",
                DebtType.AMORTIZING_LOAN,
                null, // Missing original loan
                28_200_641L, // Current remaining balance
                4_919_756L, // Regular monthly installment
                null, // Term months missing
                7, // 7 months remaining
                "16.48999999%",
                16.49,
                30,
                LocalDate.of(2026, 10, 30),
                null,
                LocalDate.of(2026, 10, 1),
                false,
                null,
                "001-786474-391",
                0.95
        );

        DebtScanPreviewResponse preview = debtService.previewDebtScan(ocr);

        assertThat(preview.action()).isEqualTo(ReconciliationAction.CREATE_NEW_DEBT);
        assertThat(preview.recommendedCreditor()).isEqualTo("Standard Chartered");
        assertThat(preview.recommendedOriginalPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(100_000_000));
        assertThat(preview.recommendedPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(28_200_641));
        assertThat(preview.recommendedApr()).isEqualByComparingTo(BigDecimal.valueOf(16.49));
        assertThat(preview.aprAnalysis().method()).isEqualTo("EXPLICIT_VERIFIED");
        assertThat(preview.recommendedMonthlyDue()).isEqualByComparingTo(BigDecimal.valueOf(4_919_756));
    }

    @Test
    @DisplayName("Should process SmartCredit 100m loan at 24% APR and calculate 5.28m monthly payment")
    void testPreviewDebtScanSmartCreditLoan() {
        DebtOcrResult ocr = new DebtOcrResult(
                "AMORTIZING_LOAN_CONTRACT",
                null,
                "Consumer Loan",
                DebtType.AMORTIZING_LOAN,
                100_000_000L,
                82_959_494L,
                null,
                24,
                null,
                "24.0%/năm",
                24.0,
                30,
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 5, 28),
                null,
                false,
                null,
                "Tài khoản vay: 730-006-119074, Chi nhánh: Trung tâm SmartCredit",
                0.95
        );

        DebtScanPreviewResponse preview = debtService.previewDebtScan(ocr);

        assertThat(preview.action()).isEqualTo(ReconciliationAction.CREATE_NEW_DEBT);
        assertThat(preview.recommendedCreditor()).isEqualTo("SmartCredit");
        assertThat(preview.recommendedOriginalPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(100_000_000));
        assertThat(preview.recommendedPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(82_959_494));
        assertThat(preview.recommendedApr()).isEqualByComparingTo(BigDecimal.valueOf(24.0));
        assertThat(preview.aprAnalysis().method()).isEqualTo("EXPLICIT_VERIFIED");
        assertThat(preview.recommendedMonthlyDue()).isBetween(BigDecimal.valueOf(5_280_000), BigDecimal.valueOf(5_290_000));
    }

    @Test
    @DisplayName("Deduplication Hygiene: Distinct loans from same creditor with different account numbers must CREATE_NEW_DEBT, never collapse into update")
    void testDistinctLoansFromSameCreditorWithDifferentAccountNumbers() {
        Debt existingLoan1 = new Debt();
        existingLoan1.setName("Personal Instalment Loan");
        existingLoan1.setCreditor("Standard Chartered");
        existingLoan1.setDebtType(DebtType.AMORTIZING_LOAN);
        existingLoan1.setAccountNumber("001-786474-391");
        existingLoan1.setCurrentPrincipal(BigDecimal.valueOf(28_200_641));
        existingLoan1.setOriginalPrincipal(BigDecimal.valueOf(100_000_000));
        existingLoan1.setStatus(DebtStatus.ACTIVE);

        when(debtRepo.findByStatusOrderByMaturityDateAsc(DebtStatus.ACTIVE)).thenReturn(List.of(existingLoan1));

        // Loan 2 scan with different account number 001-786474-393 and balance 148,442,898 VND
        DebtOcrResult ocrLoan2 = new DebtOcrResult(
                "AMORTIZING_LOAN_CONTRACT",
                "Standard Chartered",
                "Personal Instalment Loan",
                "001-786474-393",
                DebtType.AMORTIZING_LOAN,
                null,
                148_442_898L,
                3_627_020L,
                null,
                59,
                "15.74%",
                15.74,
                10,
                LocalDate.of(2026, 11, 10),
                null,
                null,
                false,
                null,
                "001-786474-393",
                0.98
        );

        DebtScanPreviewResponse preview = debtService.previewDebtScan(ocrLoan2);

        // Invariant: Must NOT collapse into updating loan 1!
        assertThat(preview.action()).isEqualTo(ReconciliationAction.CREATE_NEW_DEBT);
        assertThat(preview.matchedDebtId()).isNull();
        assertThat(preview.recommendedAccountNumber()).isEqualTo("001-786474-393");
        assertThat(preview.recommendedPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(148_442_898));
        assertThat(preview.recommendedOriginalPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(150_000_000));
        assertThat(preview.recommendedApr()).isEqualByComparingTo(BigDecimal.valueOf(15.74));
        assertThat(preview.recommendedDueDay()).isEqualTo(10);
    }

    @Test
    @DisplayName("Contract Lifecycle: Statement for identical account number must match existing debt and UPDATE_DEBT_BALANCE")
    void testSameLoanAccountMatchTriggersUpdateDebtBalance() {
        UUID loan1Id = UUID.randomUUID();
        Debt existingLoan1 = new Debt();
        existingLoan1.setName("Personal Instalment Loan");
        existingLoan1.setCreditor("Standard Chartered");
        existingLoan1.setDebtType(DebtType.AMORTIZING_LOAN);
        existingLoan1.setAccountNumber("001-786474-391");
        existingLoan1.setCurrentPrincipal(BigDecimal.valueOf(28_200_641));
        existingLoan1.setOriginalPrincipal(BigDecimal.valueOf(100_000_000));
        existingLoan1.setStatus(DebtStatus.ACTIVE);

        // Reflection to set private id for mock testing
        try {
            java.lang.reflect.Field idField = Debt.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(existingLoan1, loan1Id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(debtRepo.findByStatusOrderByMaturityDateAsc(DebtStatus.ACTIVE)).thenReturn(List.of(existingLoan1));

        // Scan next month's statement for 001-786474-391 with reduced balance 23,280,885 VND
        DebtOcrResult updatedStatement = new DebtOcrResult(
                "AMORTIZING_LOAN_CONTRACT",
                "Standard Chartered",
                "Personal Instalment Loan",
                "001-786474-391",
                DebtType.AMORTIZING_LOAN,
                null,
                23_280_885L,
                4_919_756L,
                null,
                6,
                "16.49%",
                16.49,
                30,
                LocalDate.of(2026, 11, 30),
                null,
                null,
                false,
                null,
                "001-786474-391",
                0.95
        );

        DebtScanPreviewResponse preview = debtService.previewDebtScan(updatedStatement);

        assertThat(preview.action()).isEqualTo(ReconciliationAction.UPDATE_DEBT_BALANCE);
        assertThat(preview.matchedDebtId()).isEqualTo(loan1Id);
        assertThat(preview.recommendedPrincipal()).isEqualByComparingTo(BigDecimal.valueOf(23_280_885));
    }

    @Test
    @DisplayName("Should delete debt and cascade cleanup schedules, payments, and ledger references")
    void testDeleteDebtCleansUpSchedulesAndPayments() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt();
        debt.setName("Personal Instalment Loan");
        debt.setCreditor("Standard Chartered");
        debt.setStatus(DebtStatus.ACTIVE);

        when(debtRepo.findById(debtId)).thenReturn(Optional.of(debt));

        debtService.deleteDebt(debtId);

        verify(transactionRepo, times(1)).clearDebtId(debtId);
        verify(scheduleRepo, times(1)).deleteByDebtId(debtId);
        verify(paymentRepo, times(1)).deleteByDebtId(debtId);
        verify(debtRepo, times(1)).delete(debt);
    }
}
