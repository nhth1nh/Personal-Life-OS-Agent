package com.ploa.ai.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Spring AI CallAdvisor that intercepts all model calls to log token usage, latency,
 * and estimated costs, recording them into the audit service.
 */
@Component
public class AiAuditAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(AiAuditAdvisor.class);
    private final AiAuditService auditService;

    public AiAuditAdvisor(AiAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long startTime = System.currentTimeMillis();
        String operation = extractOperation(request);
        int promptLength = calculatePromptLength(request);
        String model = "unknown";

        try {
            ChatClientResponse response = chain.nextCall(request);
            long durationMs = System.currentTimeMillis() - startTime;

            int promptTokens = 0;
            int completionTokens = 0;
            int totalTokens = 0;

            ChatResponse chatResponse = response != null ? response.chatResponse() : null;
            if (chatResponse != null) {
                ChatResponseMetadata metadata = chatResponse.getMetadata();
                if (metadata != null) {
                    if (metadata.getModel() != null && !metadata.getModel().isBlank()) {
                        model = metadata.getModel();
                    }
                    Usage usage = metadata.getUsage();
                    if (usage != null) {
                        promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                        completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                        totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : (promptTokens + completionTokens);
                    }
                }

                // If provider does not report token usage, estimate from character lengths
                if (promptTokens == 0 && totalTokens == 0) {
                    promptTokens = Math.max(1, promptLength / 4);
                    String outputText = (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null)
                            ? chatResponse.getResult().getOutput().getText()
                            : "";
                    completionTokens = Math.max(1, (outputText != null ? outputText.length() : 0) / 4);
                    totalTokens = promptTokens + completionTokens;
                }
            }

            auditService.recordSuccess(
                    operation,
                    model,
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    durationMs,
                    promptLength
            );

            return response;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            auditService.recordFailure(operation, model, durationMs, promptLength, e.getMessage());
            throw e;
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(request);
    }

    @Override
    public String getName() {
        return "AiAuditAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private String extractOperation(ChatClientRequest request) {
        if (request != null && request.context() != null) {
            Object op = request.context().get("operation");
            if (op instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return "general_call";
    }

    private int calculatePromptLength(ChatClientRequest request) {
        if (request == null || request.prompt() == null || request.prompt().getInstructions() == null) {
            return 0;
        }
        return request.prompt().getInstructions().stream()
                .mapToInt(m -> m.getText() != null ? m.getText().length() : 0)
                .sum();
    }
}
