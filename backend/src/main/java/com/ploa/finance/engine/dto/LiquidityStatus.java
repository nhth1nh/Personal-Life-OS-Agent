package com.ploa.finance.engine.dto;

/**
 * Epistemic liquidity state of the user's cash timeline.
 */
public enum LiquidityStatus {
    /**
     * Spendable cash is positive; daily discretionary spending is safe.
     */
    SOLVENT,

    /**
     * Spendable cash is negative/zero for the month, but liquid cash currently
     * covers the next 7 days of mandatory obligations.
     */
    PROJECTED_SHORTFALL,

    /**
     * Liquid cash right now is insufficient to cover mandatory obligations due in the next 7 days.
     * Immediate cash crunch.
     */
    LIQUIDITY_DEFICIT
}
