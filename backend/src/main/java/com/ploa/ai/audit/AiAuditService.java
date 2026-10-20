package com.ploa.ai.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory developer audit service for LLM calls.
 * Records token consumption and response latency without any end-user pricing bloat.
 */
@Service
public class AiAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiAuditService.class);
    private static final int MAX_BUFFER_SIZE = 500;

    private final Deque<AiAuditRecord> recentRecords = new ConcurrentLinkedDeque<>();

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final AtomicLong totalTokens = new AtomicLong(0);
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    public void recordSuccess(String operation,
                              String model,
                              int promptTokens,
                              int completionTokens,
                              int totalTokensCount,
                              long durationMs,
                              int promptChars) {
        AiAuditRecord record = AiAuditRecord.success(
                operation,
                model,
                promptTokens,
                completionTokens,
                totalTokensCount,
                durationMs,
                promptChars
        );

        totalRequests.incrementAndGet();
        successfulRequests.incrementAndGet();
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalTokens.addAndGet(totalTokensCount);
        totalDurationMs.addAndGet(durationMs);

        addRecord(record);

        // Developer-focused token and latency audit log
        log.info("[AI-AUDIT] op={} model={} tokens: in={} out={} total={} latency={}ms chars={}",
                operation,
                model,
                promptTokens,
                completionTokens,
                totalTokensCount,
                durationMs,
                promptChars
        );

        long count = totalRequests.get();
        if (count % 25 == 0) {
            long total = totalTokens.get();
            double avgLatency = (double) totalDurationMs.get() / count;
            log.info("[AI-AUDIT-SUMMARY] Total calls: {} | tokens: {} (in={}, out={}) | avg_latency: {}ms",
                    count,
                    total,
                    totalPromptTokens.get(),
                    totalCompletionTokens.get(),
                    Math.round(avgLatency)
            );
        }
    }

    public void recordFailure(String operation,
                              String model,
                              long durationMs,
                              int promptChars,
                              String errorMessage) {
        totalRequests.incrementAndGet();
        failedRequests.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);

        AiAuditRecord record = AiAuditRecord.failure(
                operation,
                model,
                durationMs,
                promptChars,
                errorMessage
        );

        addRecord(record);

        log.warn("[AI-AUDIT] op={} model={} FAILED latency={}ms chars={} error={}",
                operation, model, durationMs, promptChars, errorMessage);
    }

    private void addRecord(AiAuditRecord record) {
        recentRecords.addFirst(record);
        while (recentRecords.size() > MAX_BUFFER_SIZE) {
            recentRecords.pollLast();
        }
    }

    public List<AiAuditRecord> getRecentRecords(int limit) {
        int cap = Math.min(Math.max(limit, 1), MAX_BUFFER_SIZE);
        return recentRecords.stream().limit(cap).toList();
    }

    public long getTotalTokens() {
        return totalTokens.get();
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }
}
