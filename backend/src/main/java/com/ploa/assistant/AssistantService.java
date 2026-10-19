package com.ploa.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploa.assistant.tools.AssistantTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Conversational Life OS Assistant.
 * Dynamically queries deterministic health and finance engines via AssistantTools on demand,
 * allowing the AI to answer user questions freely, naturally, and contextually.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final AssistantTools assistantTools;
    private final ChatClient chatClient;

    public AssistantService(AssistantTools assistantTools,
                            ChatClient.Builder chatClientBuilder) {
        this.assistantTools = assistantTools;
        this.chatClient = chatClientBuilder.build();
    }

    public String chat(String userMessage) {
        log.info("Assistant chat query received: {}", userMessage);
        LocalDate today = LocalDate.now();

        // 1. Dynamically gather relevant data from engines using tools
        String toolContext = gatherRelevantToolData(userMessage);

        // 2. Build prompt giving the assistant full freedom in its response style
        String dataSection = toolContext.isEmpty()
                ? "No specific personal metrics were required for this query."
                : """
                  === AUTHORITATIVE REAL-TIME DATA RETRIEVED FROM ENGINES ===
                  %s
                  ===========================================================
                  """.formatted(toolContext);

        String systemPrompt = """
                You are the Personal Life OS Agent (PLOA), a private, intelligent life companion for physical health, recovery, fitness, and personal finance.
                Today's date is %s.
                
                %s
                
                BEHAVIOR GUIDELINES:
                1. Answer FREELY, naturally, and conversationally based on what the user is asking.
                2. You are NOT constrained to any fixed template or rigid format. Express your thoughts, insights, and recommendations organically.
                3. If tool data is provided above, use it as your source of truth.
                4. Never invent or falsify user metrics. All metrics in the tool context are authoritative.
                5. For casual greetings, identity questions, or general conversation, reply warmly and helpfully without reciting numbers.
                6. Respond in the user's language (English by default, or Vietnamese/the language the user speaks).
                7. Format cleanly using Markdown and emojis.
                """.formatted(today, dataSection);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    private String gatherRelevantToolData(String message) {
        String lower = message.toLowerCase();
        StringBuilder sb = new StringBuilder();

        boolean needsHealth = lower.matches(".*\\b(health|recovery|readiness|workout|exercise|training|gym|run|running|hrv|rhr|heart|stress|stressed|fatigue|tired|energy|strain|cardio)\\b.*");
        boolean needsSleep = lower.matches(".*\\b(sleep|sleeping|slept|bed|bedtime|wake|waking|rem|deep sleep|light sleep|nap|insomnia|rest|debt)\\b.*");
        boolean needsWeight = lower.matches(".*\\b(weight|weigh|scale|kg|kilogram|body|fat|muscle|diet|bulk|cutting|ema)\\b.*");
        boolean needsFinance = lower.matches(".*\\b(finance|finances|financial|money|budget|spend|spending|spent|burn|allowance|savings|afford|cost|expensive|balance)\\b.*");
        boolean needsTransactions = lower.matches(".*\\b(transaction|transactions|history|receipt|receipts|purchase|purchases|bought|groceries|coffee|shopee)\\b.*");
        boolean isComprehensive = lower.matches(".*\\b(overview|summary|status|how am i doing|briefing|checkup|report|dashboard)\\b.*");

        if (isComprehensive) {
            needsHealth = true;
            needsSleep = true;
            needsWeight = true;
            needsFinance = true;
        }

        try {
            if (needsHealth) {
                var readiness = assistantTools.getHealthReadiness(null);
                sb.append("[Tool: getHealthReadiness]\n").append(MAPPER.writeValueAsString(readiness)).append("\n\n");
            }
            if (needsSleep) {
                var sleep = assistantTools.getSleepBreakdown();
                sb.append("[Tool: getSleepBreakdown]\n").append(MAPPER.writeValueAsString(sleep)).append("\n\n");
            }
            if (needsWeight) {
                var weight = assistantTools.getWeightTrend();
                sb.append("[Tool: getWeightTrend]\n").append(MAPPER.writeValueAsString(weight)).append("\n\n");
            }
            if (needsFinance) {
                var finance = assistantTools.getFinancialBurnRate();
                sb.append("[Tool: getFinancialBurnRate]\n").append(MAPPER.writeValueAsString(finance)).append("\n\n");
            }
            if (needsTransactions) {
                var txs = assistantTools.getRecentTransactions(10);
                sb.append("[Tool: getRecentTransactions]\n").append(MAPPER.writeValueAsString(txs)).append("\n\n");
            }
        } catch (Exception e) {
            log.warn("Error serializing tool data for assistant: {}", e.getMessage());
        }

        return sb.toString().trim();
    }
}
