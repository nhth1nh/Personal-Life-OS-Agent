package com.ploa.finance;

import com.ploa.finance.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEntityTest {

    @Test
    @DisplayName("FinancialAccount should maintain strict BigDecimal balance and attributes")
    void testFinancialAccount() {
        FinancialAccount acc = new FinancialAccount();
        acc.setName("Techcombank Payroll");
        acc.setAccountType(AccountType.CHECKING);
        acc.setCurrency("VND");
        acc.setCurrentBalance(new BigDecimal("25000000.00"));

        assertThat(acc.getName()).isEqualTo("Techcombank Payroll");
        assertThat(acc.getAccountType()).isEqualTo(AccountType.CHECKING);
        assertThat(acc.getCurrentBalance()).isEqualByComparingTo("25000000.00");
        assertThat(acc.isActive()).isTrue();
    }

    @Test
    @DisplayName("Debt entity should accurately store contractual attributes and APR")
    void testDebtEntity() {
        Debt debt = new Debt();
        debt.setName("Home Mortgage");
        debt.setCreditor("Vietcombank");
        debt.setDebtType(DebtType.AMORTIZING_LOAN);
        debt.setOriginalPrincipal(new BigDecimal("1200000000.00"));
        debt.setCurrentPrincipal(new BigDecimal("1150000000.00"));
        debt.setInterestRateApr(new BigDecimal("10.5000"));
        debt.setInterestAccrualConvention(InterestAccrualConvention.ACTUAL_365);
        debt.setMonthlyMinimumDue(new BigDecimal("14500000.00"));
        debt.setDueDayOfMonth(15);
        debt.setOriginationDate(LocalDate.of(2025, 1, 15));
        debt.setStatus(DebtStatus.ACTIVE);

        assertThat(debt.getName()).isEqualTo("Home Mortgage");
        assertThat(debt.getCreditor()).isEqualTo("Vietcombank");
        assertThat(debt.getDebtType()).isEqualTo(DebtType.AMORTIZING_LOAN);
        assertThat(debt.getCurrentPrincipal()).isEqualByComparingTo("1150000000.00");
        assertThat(debt.getInterestRateApr()).isEqualByComparingTo("10.5000");
        assertThat(debt.getInterestAccrualConvention()).isEqualTo(InterestAccrualConvention.ACTUAL_365);
        assertThat(debt.getMonthlyMinimumDue()).isEqualByComparingTo("14500000.00");
        assertThat(debt.getDueDayOfMonth()).isEqualTo(15);
        assertThat(debt.getStatus()).isEqualTo(DebtStatus.ACTIVE);
    }

    @Test
    @DisplayName("Income entity should model confidence factor and recurrence")
    void testIncomeEntity() {
        Income inc = new Income();
        inc.setSource("Primary Employer");
        inc.setExpectedAmount(new BigDecimal("43000000.00"));
        inc.setExpectedDate(LocalDate.of(2026, 10, 30));
        inc.setIncomeType(IncomeType.SALARY);
        inc.setConfidenceFactor(1.0);
        inc.setRecurring(true);
        inc.setFrequency(IncomeFrequency.MONTHLY);
        inc.setStatus(IncomeStatus.EXPECTED);

        assertThat(inc.getSource()).isEqualTo("Primary Employer");
        assertThat(inc.getExpectedAmount()).isEqualByComparingTo("43000000.00");
        assertThat(inc.getConfidenceFactor()).isEqualTo(1.0);
        assertThat(inc.isRecurring()).isTrue();
        assertThat(inc.getFrequency()).isEqualTo(IncomeFrequency.MONTHLY);
        assertThat(inc.getStatus()).isEqualTo(IncomeStatus.EXPECTED);
    }

    @Test
    @DisplayName("Transaction should support explicit TransactionType and account linkage")
    void testTransactionDomainEnhancement() {
        UUID accountId = UUID.randomUUID();
        UUID debtId = UUID.randomUUID();

        Transaction tx = new Transaction();
        tx.setAccountId(accountId);
        tx.setDebtId(debtId);
        tx.setTransactionType(TransactionType.DEBT_PRINCIPAL_PAYMENT);
        tx.setAmount(5000000L);
        tx.setTxDate(LocalDate.now());
        tx.setCategory("Debt Service");
        tx.setSpendType(SpendType.FIXED);

        assertThat(tx.getAccountId()).isEqualTo(accountId);
        assertThat(tx.getDebtId()).isEqualTo(debtId);
        assertThat(tx.getTransactionType()).isEqualTo(TransactionType.DEBT_PRINCIPAL_PAYMENT);
        assertThat(tx.getAmount()).isEqualTo(5000000L);
    }
}
