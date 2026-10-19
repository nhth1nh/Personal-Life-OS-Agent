package com.ploa.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploa.assistant.memory.ChatMessage;
import com.ploa.assistant.memory.MemoryService;
import com.ploa.assistant.memory.UserMemory;
import com.ploa.assistant.tools.AssistantTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Conversational Life OS Assistant.
 * Uses atomic, selective tool execution to fetch ONLY relevant data on demand.
 * Maintains multi-turn conversation memory and persistent user preferences in PostgreSQL.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final AssistantTools assistantTools;
    private final MemoryService memoryService;
    private final ChatClient chatClient;

    public AssistantService(AssistantTools assistantTools,
                            MemoryService memoryService,
                            ChatClient.Builder chatClientBuilder) {
        this.assistantTools = assistantTools;
        this.memoryService = memoryService;
        this.chatClient = chatClientBuilder.build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemoryPayload(String category, String key, String value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolPlan(List<String> tools, String date, MemoryPayload memory) {}

    public String chat(String userMessage) {
        return chat("default-session", userMessage);
    }

    public String chat(String chatId, String userMessage) {
        log.info("Assistant chat query received from [{}]: {}", chatId, userMessage);
        LocalDate today = LocalDate.now();

        // 1. Record user turn in chat memory
        memoryService.saveChatMessage(chatId, "user", userMessage);

        // 2. Fetch conversation context & persistent user preferences
        List<ChatMessage> history = memoryService.getRecentChatHistory(chatId, 8);
        List<UserMemory> preferences = memoryService.getAllPreferences();

        // 3. Determine tool execution plan (Selective Grounding)
        ToolPlan plan = planToolExecution(userMessage, preferences);

        // 4. Execute only the planned tools
        Map<String, Object> groundingData = executePlan(plan);

        // 5. Build prompt with selective grounding, user profile, and conversation history
        String systemPrompt = buildSystemPrompt(today, groundingData, preferences, history);

        // 6. Synthesize the conversational response
        String reply = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        // 7. Record assistant turn in chat memory
        memoryService.saveChatMessage(chatId, "assistant", reply);

        return reply;
    }

    private ToolPlan planToolExecution(String userMessage, List<UserMemory> preferences) {
        String plannerPrompt = """
                You are the Tool Planner for the Personal Life OS Assistant.
                Analyze the user's message and determine which specific data tools (if any) are required.
                Today's date is %s.
                
                AVAILABLE TOOLS:
                - "getDailyHealth": Physical biometrics (steps, distance, active/total kcal, active time, body battery, stress, heart rate) for a date. Date parameter can be "today", "yesterday", or "YYYY-MM-DD".
                - "getHealthReadiness": Recovery readiness, overnight HRV, 30-day baseline Z-scores, recovery level.
                - "getSleepAnalysis": Sleep duration, deep/light/REM stages, sleep debt against target.
                - "getWeightTrend": Latest weight, 7-day EMA trend, distance to target weight.
                - "getFinancialStatus": Monthly budget, MTD spend, daily burn rate, safe daily allowance, savings rate.
                - "getRecentTransactions": List of recent individual expense transactions.
                - "saveUserMemory": User reveals a persistent habit, diet, running/fitness goal, or preference to store in long-term memory.
                
                RULES:
                1. If the message is a greeting, casual banter, general opinion, or general advice ("hello", "chào mày", "tư vấn cách ngủ ngon"), return NO tools.
                2. If the user asks about "hôm qua" or "yesterday", set date="yesterday".
                3. If the user asks about "hôm nay" or "today", set date="today".
                4. Only select tools strictly relevant to the question (do NOT select finance for health queries, or vice-versa).
                5. If user states a permanent goal or habit (e.g. "tao đang ăn chay", "mục tiêu marathon tháng 12"), include "saveUserMemory" with category, key, value.
                
                OUTPUT FORMAT: Strict JSON only:
                {
                  "tools": ["getDailyHealth", ...],
                  "date": "today" | "yesterday" | "YYYY-MM-DD",
                  "memory": {"category": "FITNESS|DIET|FINANCE|LIFESTYLE|GOAL|GENERAL", "key": "...", "value": "..."}
                }
                If no tools are needed, output:
                {"tools": []}
                """.formatted(LocalDate.now());

        try {
            String rawJson = chatClient.prompt()
                    .system(plannerPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            // Extract clean JSON block if model wrapped in markdown
            String cleaned = cleanJson(rawJson);
            ToolPlan plan = MAPPER.readValue(cleaned, ToolPlan.class);
            log.info("Tool execution plan: tools={}, date={}", plan.tools(), plan.date());
            return plan;
        } catch (Exception e) {
            log.warn("Planner fallback due to error: {}", e.getMessage());
            return heuristicFallbackPlan(userMessage);
        }
    }

    private Map<String, Object> executePlan(ToolPlan plan) {
        if (plan == null || plan.tools() == null || plan.tools().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        String targetDate = plan.date() != null ? plan.date() : "today";

        for (String tool : plan.tools()) {
            try {
                switch (tool) {
                    case "getDailyHealth" -> data.put("daily_health_" + targetDate, assistantTools.getDailyHealth(targetDate));
                    case "getHealthReadiness" -> data.put("readiness_" + targetDate, assistantTools.getHealthReadiness(targetDate));
                    case "getSleepAnalysis" -> data.put("sleep_analysis", assistantTools.getSleepAnalysis());
                    case "getWeightTrend" -> data.put("weight_trend", assistantTools.getWeightTrend());
                    case "getFinancialStatus" -> data.put("financial_status", assistantTools.getFinancialStatus());
                    case "getRecentTransactions" -> data.put("recent_transactions", assistantTools.getRecentTransactions(5));
                    case "saveUserMemory" -> {
                        if (plan.memory() != null && plan.memory().key() != null) {
                            String res = assistantTools.saveUserMemory(
                                    plan.memory().category(),
                                    plan.memory().key(),
                                    plan.memory().value()
                            );
                            data.put("memory_saved", res);
                        }
                    }
                    default -> log.debug("Unknown tool in plan: {}", tool);
                }
            } catch (Exception e) {
                log.warn("Failed executing tool {}: {}", tool, e.getMessage());
            }
        }
        return data;
    }

    private String buildSystemPrompt(LocalDate today,
                                     Map<String, Object> groundingData,
                                     List<UserMemory> preferences,
                                     List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the Personal Life OS Agent (PLOA), a private, highly intelligent personal life companion for physical health, recovery, fitness, and personal finance.\n");
        sb.append("Today's date is ").append(today).append(".\n\n");

        // 1. Persistent User Profile
        if (!preferences.isEmpty()) {
            sb.append("=== USER PROFILE & LONG-TERM MEMORIES ===\n");
            for (UserMemory m : preferences) {
                sb.append("• [").append(m.getCategory()).append("] ")
                        .append(m.getMemoryKey()).append(": ")
                        .append(m.getMemoryValue()).append("\n");
            }
            sb.append("=========================================\n\n");
        }

        // 2. Selective Grounding Data (Only when retrieved!)
        if (!groundingData.isEmpty()) {
            sb.append("=== RETRIEVED GROUNDING DATA ===\n");
            try {
                sb.append(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(groundingData));
            } catch (Exception e) {
                sb.append(groundingData);
            }
            sb.append("\n================================\n\n");
        }

        // 3. Recent Conversation History (excluding the message just received)
        if (history != null && history.size() > 1) {
            sb.append("=== RECENT CONVERSATION HISTORY ===\n");
            for (int i = 0; i < history.size() - 1; i++) {
                ChatMessage m = history.get(i);
                sb.append(m.getRole().toUpperCase()).append(": ").append(m.getContent()).append("\n");
            }
            sb.append("===================================\n\n");
        }

        // 4. Behavioral Guidelines
        sb.append("""
                BEHAVIOR GUIDELINES:
                1. Answer FREELY, naturally, and conversationally based strictly on what the user is asking.
                2. You are NOT constrained to any rigid format or pre-set template.
                3. The retrieved data above is your single source of truth for the user's real numbers. Never invent or falsify metrics.
                4. If specific data was retrieved for 'yesterday' or another date, answer accurately for that requested date!
                5. If no empirical data was needed (e.g. casual chitchat, greetings, conceptual advice), answer warmly and concisely.
                6. MATCH THE USER'S LANGUAGE AND TONE:
                   - If asked in Vietnamese, respond in natural Vietnamese.
                   - If the user uses casual/informal pronouns (e.g. "tao/mày"), match their casual, candid vibe naturally.
                   - If asked in English, reply in English.
                7. Format cleanly using standard Markdown (*bold*, lists, emojis). Avoid unclosed formatting markers.
                """);

        return sb.toString();
    }

    private ToolPlan heuristicFallbackPlan(String text) {
        String t = text.toLowerCase();
        List<String> tools = new ArrayList<>();
        String date = t.contains("hôm qua") || t.contains("yesterday") ? "yesterday" : "today";

        if (t.contains("chạy") || t.contains("bước") || t.contains("calo") || t.contains("step") || t.contains("kcal") || t.contains("activity") || t.contains("vận động") || t.contains("pin") || t.contains("battery") || t.contains("stress")) {
            tools.add("getDailyHealth");
        }
        if (t.contains("ngủ") || t.contains("sleep")) {
            tools.add("getSleepAnalysis");
        }
        if (t.contains("tập") || t.contains("phục hồi") || t.contains("ready") || t.contains("readiness") || t.contains("workout") || t.contains("mệt")) {
            tools.add("getHealthReadiness");
        }
        if (t.contains("tiền") || t.contains("chi") || t.contains("budget") || t.contains("spend") || t.contains("burn") || t.contains("allowance")) {
            tools.add("getFinancialStatus");
        }
        if (t.contains("cân") || t.contains("weight") || t.contains("kg")) {
            tools.add("getWeightTrend");
        }

        return new ToolPlan(tools, date, null);
    }

    private String cleanJson(String raw) {
        if (raw == null) return "{\"tools\":[]}";
        String s = raw.trim();
        if (s.startsWith("```json")) {
            s = s.substring(7);
        } else if (s.startsWith("```")) {
            s = s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }
}
