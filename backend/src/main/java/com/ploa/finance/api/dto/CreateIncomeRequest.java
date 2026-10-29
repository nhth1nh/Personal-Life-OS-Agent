package com.ploa.finance.api.dto;

import com.ploa.finance.model.IncomeFrequency;
import com.ploa.finance.model.IncomeStatus;
import com.ploa.finance.model.IncomeType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateIncomeRequest(
        String source,
        BigDecimal expectedAmount,
        BigDecimal actualAmount,
        LocalDate expectedDate,
        LocalDate receivedDate,
        IncomeType incomeType,
        Double confidenceFactor,
        Boolean isRecurring,
        IncomeFrequency frequency,
        IncomeStatus status,
        String notes
) {}
