package com.ploa.finance.api.dto;

import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.TransactionType;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
        Long amount,
        String category,
        SpendType spendType,
        TransactionType transactionType,
        String merchant,
        LocalDate txDate,
        UUID accountId,
        String note
) {}
