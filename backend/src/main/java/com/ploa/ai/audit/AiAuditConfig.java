package com.ploa.ai.audit;

import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Automatically attaches the AiAuditAdvisor to every ChatClient built in the application.
 */
@Configuration
public class AiAuditConfig {

    @Bean
    public ChatClientCustomizer aiAuditChatClientCustomizer(AiAuditAdvisor advisor) {
        return builder -> builder.defaultAdvisors(advisor);
    }
}
