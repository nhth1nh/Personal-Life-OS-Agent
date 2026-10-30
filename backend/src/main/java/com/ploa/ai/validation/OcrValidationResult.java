package com.ploa.ai.validation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of validating raw OCR-extracted data against deterministic mathematical invariants.
 */
public record OcrValidationResult(
        ValidationStatus status,
        boolean isAcceptable,
        List<String> warnings,
        List<String> errors,
        Map<String, Object> details
) {
    public static OcrValidationResult verified() {
        return new OcrValidationResult(ValidationStatus.VERIFIED, true, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
    }

    public static OcrValidationResult reconstructed(List<String> warnings, Map<String, Object> details) {
        return new OcrValidationResult(ValidationStatus.RECONSTRUCTED, true,
                warnings != null ? warnings : Collections.emptyList(),
                Collections.emptyList(),
                details != null ? details : Collections.emptyMap());
    }

    public static OcrValidationResult needsReview(List<String> warnings, Map<String, Object> details) {
        return new OcrValidationResult(ValidationStatus.NEEDS_REVIEW, true,
                warnings != null ? warnings : Collections.emptyList(),
                Collections.emptyList(),
                details != null ? details : Collections.emptyMap());
    }

    public static OcrValidationResult rejected(List<String> errors) {
        return new OcrValidationResult(ValidationStatus.REJECTED, false,
                Collections.emptyList(),
                errors != null ? errors : Collections.emptyList(),
                Collections.emptyMap());
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
