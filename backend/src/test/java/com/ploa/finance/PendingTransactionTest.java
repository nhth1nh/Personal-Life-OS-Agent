package com.ploa.finance;

import com.ploa.finance.model.Transaction;
import com.ploa.finance.repository.OcrAuditRepository;
import com.ploa.finance.repository.TransactionRepository;
import com.ploa.finance.service.FinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PendingTransactionTest {

    private TransactionRepository repo;
    private FinanceService financeService;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(TransactionRepository.class);
        OcrAuditRepository auditRepo = Mockito.mock(OcrAuditRepository.class);
        financeService = new FinanceService(repo, auditRepo, null);
    }

    @Test
    @DisplayName("Should retrieve pending unconfirmed transactions")
    void testGetPendingTransactions() {
        Transaction draft = new Transaction();
        draft.setAmount(10143000L);
        draft.setConfirmed(false);

        when(repo.findByConfirmedFalseOrderByCreatedAtDesc()).thenReturn(List.of(draft));

        List<Transaction> pending = financeService.getPendingTransactions();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getAmount()).isEqualTo(10143000L);
        assertThat(pending.get(0).isConfirmed()).isFalse();
    }
}
