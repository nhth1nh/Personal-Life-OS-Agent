package com.ploa.ai.validation;

/**
 * Verification state of an OCR extraction result.
 */
public enum ValidationStatus {
    /**
     * Passed all mathematical, temporal, and entity invariant checks cleanly.
     */
    VERIFIED,

    /**
     * Terms were missing or mid-tenure, but successfully reconstructed deterministically.
     */
    RECONSTRUCTED,

    /**
     * Discrepancies, warnings, or low confidence detected; requires user verification before locking.
     */
    NEEDS_REVIEW,

    /**
     * Critical failure (e.g., non-financial image, missing amounts, negative sums).
     */
    REJECTED
}
