package com.ploa.ai.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiAuditAdvisorTest {

    @Test
    @DisplayName("Should intercept ChatClient call, log token metrics, and record to AiAuditService")
    void shouldAuditChatClientCall() {
        AiAuditService auditService = new AiAuditService();
        AiAuditAdvisor advisor = new AiAuditAdvisor(auditService);

        // Prepare mock ChatClientResponse
        DefaultUsage usage = new DefaultUsage(250, 50, 300);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("gemini-1.5-flash")
                .usage(usage)
                .build();
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("Output text"))), metadata);
        ChatClientResponse clientResponse = new ChatClientResponse(chatResponse, Map.of());

        // Prepare request
        ChatClientRequest request = new ChatClientRequest(new Prompt("Test prompt"), Map.of("operation", "assistant_synthesis"));
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(clientResponse);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertThat(result).isNotNull();
        var records = auditService.getRecentRecords(10);
        assertThat(records).hasSize(1);

        AiAuditRecord record = records.get(0);
        assertThat(record.operation()).isEqualTo("assistant_synthesis");
        assertThat(record.model()).isEqualTo("gemini-1.5-flash");
        assertThat(record.promptTokens()).isEqualTo(250);
        assertThat(record.completionTokens()).isEqualTo(50);
        assertThat(record.totalTokens()).isEqualTo(300);
        assertThat(record.promptChars()).isGreaterThan(0);
        assertThat(record.success()).isTrue();
    }
}
