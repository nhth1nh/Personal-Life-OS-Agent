package com.ploa.finance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecordDebtPaymentRequest(
        BigDecimal totalPaid,
        BigDecimal principalPortion,
        BigDecimal interestPortion,
        BigDecimal feePortion,
        LocalDate paymentDate,
        UUID accountId,
        String note
) {}
