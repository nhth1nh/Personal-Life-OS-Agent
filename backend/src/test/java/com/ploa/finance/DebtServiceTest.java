package com.ploa.finance;

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
}
