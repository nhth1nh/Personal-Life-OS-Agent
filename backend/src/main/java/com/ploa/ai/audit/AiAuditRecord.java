package com.ploa.ai.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Developer audit record capturing token metrics and latency for an LLM call.
 */
public record AiAuditRecord(
        UUID id,
        Instant timestamp,
        String operation,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long durationMs,
        int promptChars,
        boolean success,
        String errorMessage
) {
    public static AiAuditRecord success(
            String operation,
            String model,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            long durationMs,
            int promptChars
    ) {
        return new AiAuditRecord(
                UUID.randomUUID(),
                Instant.now(),
                operation,
                model,
                promptTokens,
                completionTokens,
                totalTokens,
                durationMs,
                promptChars,
                true,
                null
        );
    }

    public static AiAuditRecord failure(
            String operation,
            String model,
            long durationMs,
            int promptChars,
            String errorMessage
    ) {
        return new AiAuditRecord(
                UUID.randomUUID(),
                Instant.now(),
                operation,
                model,
                0,
                0,
                0,
                durationMs,
                promptChars,
                false,
                errorMessage
        );
    }
}
