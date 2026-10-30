package com.ploa.finance.engine.dto;

import java.math.BigDecimal;

/**
 * Result of mathematical APR analysis and IRR rate derivation.
 * Distinguishes true declining-balance effective APR from deceptive flat rates.
 */
public record AprAnalysisResult(
        BigDecimal effectiveApr,
        BigDecimal flatApr,
        boolean isZeroInterest,
        String method,
        String explanation
) {}
