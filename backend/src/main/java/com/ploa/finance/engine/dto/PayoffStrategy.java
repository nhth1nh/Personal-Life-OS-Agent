package com.ploa.finance.engine.dto;

/**
 * Strategy applied in debt payoff acceleration simulation.
 */
public enum PayoffStrategy {
    /**
     * Contractual minimum payments only, no surplus or rollover.
     */
    BASELINE,

    /**
     * Prioritize debts with the highest APR first (mathematically optimal for interest minimization).
     */
    AVALANCHE,

    /**
     * Prioritize debts with the smallest current principal balance first (behavioral momentum).
     */
    SNOWBALL
}
