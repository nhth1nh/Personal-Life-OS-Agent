package com.ploa.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploa.assistant.tools.AssistantTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conversational Life OS Assistant.
 * Gathers authoritative real-time metrics across all domains via AssistantTools,
 * providing the AI with complete grounding while letting it answer freely,
 * conversationally, and naturally in any language (English, Vietnamese, etc.).
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

        // 1. Gather all current authoritative metrics across domains
        String dataJson = buildCurrentDataJson();

        // 2. Build prompt giving the model complete freedom in response structure
        String systemPrompt = """
                You are the Personal Life OS Agent (PLOA), a private, highly intelligent personal life companion for physical health, recovery, fitness, and personal finance.
                Today's date is %s.
                
                === REAL-TIME GROUNDING DATA ===
                %s
                ================================
                
                BEHAVIOR GUIDELINES:
                1. Answer FREELY, naturally, and conversationally based on what the user is asking.
                2. You are NOT constrained to any rigid format or pre-set template. Express your thoughts, insights, and recommendations organically.
                3. The data above is your single source of truth for the user's real numbers. Never invent or falsify metrics.
                4. Focus on what the user is asking:
                   - If they ask about spending/money, focus on finances.
                   - If they ask about workouts/fatigue/health, focus on recovery and readiness.
                   - If they ask for an overview or "what to pay attention to", synthesize the most critical takeaways.
                   - If they just greet or chat casually, respond warmly without dumping numbers.
                5. MATCH THE USER'S LANGUAGE AND TONE:
                   - If asked in Vietnamese, respond in natural Vietnamese.
                   - If the user uses casual/informal pronouns (e.g. "tao/mày"), match their casual, candid vibe naturally.
                   - If asked in English, reply in English.
                6. Format cleanly using standard Markdown (*bold*, lists, emojis). Avoid unclosed formatting markers.
                """.formatted(today, dataJson);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    private String buildCurrentDataJson() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("garmin_live_biometrics", assistantTools.getLiveGarminMetrics(null));
            data.put("health_readiness", assistantTools.getHealthReadiness(null));
            data.put("sleep_stages", assistantTools.getSleepBreakdown());
            data.put("weight_trend", assistantTools.getWeightTrend());
            data.put("finances_month_to_date", assistantTools.getFinancialBurnRate());
            data.put("recent_transactions", assistantTools.getRecentTransactions(5));
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialize current data for assistant: {}", e.getMessage());
            return "{}";
        }
    }
}
