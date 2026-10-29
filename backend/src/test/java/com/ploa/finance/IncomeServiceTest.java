package com.ploa.finance;

import com.ploa.finance.api.dto.CreateIncomeRequest;
import com.ploa.finance.model.FinancialAccount;
import com.ploa.finance.model.Income;
import com.ploa.finance.model.IncomeFrequency;
import com.ploa.finance.model.IncomeStatus;
import com.ploa.finance.model.IncomeType;
import com.ploa.finance.repository.FinancialAccountRepository;
import com.ploa.finance.repository.IncomeRepository;
import com.ploa.finance.service.IncomeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IncomeServiceTest {

    private IncomeRepository incomeRepo;
    private FinancialAccountRepository accountRepo;
    private IncomeService incomeService;

    @BeforeEach
    void setUp() {
        incomeRepo = mock(IncomeRepository.class);
        accountRepo = mock(FinancialAccountRepository.class);
        incomeService = new IncomeService(incomeRepo, accountRepo);
    }

    @Test
    @DisplayName("Should create expected salary income")
    void testCreateIncome() {
        CreateIncomeRequest req = new CreateIncomeRequest(
                "Acme Corp",
                BigDecimal.valueOf(43_000_000),
                null,
                LocalDate.of(2026, 4, 30),
                null,
                IncomeType.SALARY,
                1.0,
                true,
                IncomeFrequency.MONTHLY,
                IncomeStatus.EXPECTED,
                "Monthly salary"
        );

        when(incomeRepo.save(any(Income.class))).thenAnswer(i -> {
            Income in = i.getArgument(0);
            if (in.getId() == null) in.setId(UUID.randomUUID());
            return in;
        });

        Income created = incomeService.createIncome(req);

        assertThat(created).isNotNull();
        assertThat(created.getSource()).isEqualTo("Acme Corp");
        assertThat(created.getExpectedAmount()).isEqualByComparingTo(BigDecimal.valueOf(43_000_000));
        assertThat(created.getStatus()).isEqualTo(IncomeStatus.EXPECTED);
        verify(incomeRepo, times(1)).save(any(Income.class));
    }

    @Test
    @DisplayName("Should mark income as received and credit financial account")
    void testMarkAsReceived() {
        UUID incomeId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Income income = new Income();
        income.setId(incomeId);
        income.setSource("Client Project");
        income.setExpectedAmount(BigDecimal.valueOf(15_000_000));
        income.setStatus(IncomeStatus.EXPECTED);

        FinancialAccount account = new FinancialAccount();
        account.setId(accountId);
        account.setName("Techcombank Main");
        account.setCurrentBalance(BigDecimal.valueOf(5_000_000));

        when(incomeRepo.findById(incomeId)).thenReturn(Optional.of(income));
        when(accountRepo.findById(accountId)).thenReturn(Optional.of(account));
        when(incomeRepo.save(any(Income.class))).thenAnswer(i -> i.getArgument(0));

        Income updated = incomeService.markAsReceived(incomeId, BigDecimal.valueOf(15_000_000), LocalDate.of(2026, 4, 20), accountId);

        assertThat(updated.getStatus()).isEqualTo(IncomeStatus.RECEIVED);
        assertThat(updated.getActualAmount()).isEqualByComparingTo(BigDecimal.valueOf(15_000_000));
        assertThat(updated.getReceivedDate()).isEqualTo(LocalDate.of(2026, 4, 20));

        // Account balance credited
        assertThat(account.getCurrentBalance()).isEqualByComparingTo(BigDecimal.valueOf(20_000_000));
        verify(accountRepo, times(1)).save(account);
    }
}
