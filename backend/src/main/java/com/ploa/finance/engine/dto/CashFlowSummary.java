package com.ploa.finance.engine.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Deterministic cash-flow waterfall evaluation.
 * Separates actual liquid cash, committed obligations, and risk-weighted forecasts.
 */
public record CashFlowSummary(
        LocalDate asOfDate,
        BigDecimal liquidCashNow,
        BigDecimal expectedInflowsBeforeMonthEnd,
        BigDecimal unpaidDebtObligationsBeforeMonthEnd,
        BigDecimal unpaidFixedBillsBeforeMonthEnd,
        BigDecimal emergencyReserveFloor,
        BigDecimal spendableCash,
        int daysRemainingInMonth,
        BigDecimal safeDailyAllowance,
        LiquidityStatus liquidityStatus,
        BigDecimal mtdIncomeReceived,
        BigDecimal mtdOperatingSpend,
        BigDecimal mtdDebtPrincipalPaid,
        BigDecimal netCashFlowMtd,
        BigDecimal netSavingsMtd,
        String recommendation
) {
    public boolean isSolvent() {
        return liquidityStatus == LiquidityStatus.SOLVENT;
    }

    public boolean isDeficit() {
        return liquidityStatus == LiquidityStatus.LIQUIDITY_DEFICIT;
    }
}
