package com.ploa.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploa.ai.TemporalContext;
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
    private final TemporalContext temporalContext;
    private final ChatClient chatClient;

    public AssistantService(AssistantTools assistantTools,
                            MemoryService memoryService,
                            TemporalContext temporalContext,
                            ChatClient.Builder chatClientBuilder) {
        this.assistantTools = assistantTools;
        this.memoryService = memoryService;
        this.temporalContext = temporalContext;
        this.chatClient = chatClientBuilder.build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemoryPayload(String category, String key, String value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolPlan(List<String> tools, String date, MemoryPayload memory) {}

    public String chat(String userMessage) {
        return chat("default-session", userMessage);
    }

    public void clearChatHistory(String chatId) {
        memoryService.clearChatHistory(chatId);
        log.info("Cleared chat memory history for session: {}", chatId);
    }

    public String chat(String chatId, String userMessage) {
        log.info("Assistant chat query received from [{}] at {}: {}", chatId, temporalContext.getIsoTimestamp(), userMessage);
        LocalDate today = temporalContext.currentDate();

        // 1. Record user turn in chat memory
        memoryService.saveChatMessage(chatId, "user", userMessage);

        // 2. Fetch conversation context & persistent user preferences
        List<ChatMessage> history = memoryService.getRecentChatHistory(chatId, 8);
        List<UserMemory> preferences = memoryService.getAllPreferences();

        // 3. Determine tool execution plan (Selective Grounding) with temporal & conversational context
        ToolPlan plan = planToolExecution(userMessage, preferences, history);

        // 4. Execute only the planned tools
        Map<String, Object> groundingData = executePlan(plan);
        if (groundingData.containsKey("memory_saved")) {
            preferences = memoryService.getAllPreferences();
        }

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

    private ToolPlan planToolExecution(String userMessage, List<UserMemory> preferences, List<ChatMessage> history) {
        StringBuilder historyContext = new StringBuilder();
        if (history != null && history.size() > 1) {
            historyContext.append("=== RECENT CONVERSATION CONTEXT (Prior Turns) ===\n");
            // Exclude the last message which is the current userMessage just recorded
            int limit = Math.min(history.size() - 1, 6);
            int start = (history.size() - 1) - limit;
            for (int i = start; i < history.size() - 1; i++) {
                ChatMessage m = history.get(i);
                historyContext.append(m.getRole().toUpperCase()).append(": ").append(m.getContent()).append("\n");
            }
            historyContext.append("================================================\n\n");
        }

        String plannerPrompt = """
                You are the Tool Planner for the Personal Life OS Assistant.
                Analyze the user's message and determine which specific data tools (if any) are required.

                %s

                %sAVAILABLE TOOLS:
                - "syncGarminData": Trigger an immediate live sync with Garmin Connect to pull up-to-the-second biometrics from the user's Garmin watch (steps, active calories, Body Battery, stress, heart rate). Call this ONLY when the user explicitly requests to sync, update, or refresh live data from their watch.
                - "getDailyHealth": Physical biometrics (steps, distance, active/total kcal, active time, body battery, stress, heart rate) for a date. Date parameter can be "today", "yesterday", or "YYYY-MM-DD".
                - "getHealthReadiness": Recovery readiness, overnight HRV, 30-day baseline Z-scores, recovery level.
                - "getSleepAnalysis": Sleep duration, deep/light/REM stages, sleep debt against target.
                - "getWeightTrend": Latest weight, 7-day EMA trend, distance to target weight.
                - "getFinancialStatus": Monthly budget, MTD spend, daily burn rate, safe daily allowance, savings rate.
                - "getRecentTransactions": List of recent individual expense transactions.
                - "getPendingTransactions": List of unconfirmed or pending draft transactions awaiting user confirmation.
                - "saveUserMemory": Persist permanent, enduring profile facts or long-term preferences that provide persistent context across future sessions (user identity/name, communication style, pronouns, dietary rules, fixed budget limits, enduring goals, explicit save instructions).

                RULES:
                1. TEMPORAL GROUNDING: Always evaluate relative time tokens ("today", "yesterday", "last night", "this morning", "tonight", "right now") against CURRENT TEMPORAL CONTEXT above.
                2. "syncGarminData" TRIGGER: If the user explicitly asks to sync, update, or refresh data from their Garmin watch/tracker (e.g. "sync my watch", "refresh my steps", "update data from watch"), include "syncGarminData".
                3. "saveUserMemory" TRIGGER (HIGH THRESHOLD - ONLY FOR ENDURING PROFILE CONTEXT):
                   Include "saveUserMemory" in "tools" and populate the "memory" field ONLY when the user communicates durable, long-term personal context needed across future conversations:
                   - Profile & Identity: User's name, nickname, profession, age (e.g., "My name is Alex", "I work as a software engineer", "Call me Sam"). Set category="PROFILE".
                   - Interaction Persona & Style: How to address them, pronouns, tone, language rules (e.g., "Address me casually", "speak informally", "always reply in English", "be concise"). Set category="PERSONA" or "PREFERENCE".
                   - Enduring Rules & Constraints: Permanent dietary restrictions (e.g., "I am vegetarian", "allergic to peanuts"), fixed monthly financial budgets (e.g., "monthly budget is 15M VND"), or major milestone athletic goals (e.g., "target 42km marathon in December"). Set category="DIET" | "FINANCE" | "FITNESS" | "GOAL".
                   - Explicit Memory Requests: When user explicitly commands to remember or note down ("remember that...", "keep in mind that...", "take note that..."). Set appropriate category.

                4. "saveUserMemory" STRICT PROHIBITIONS (NEVER SAVE TO MEMORY):
                   - NEVER save daily routines, workplace habits, office lunches, or schedules (e.g., "I eat company lunch on working days", "I eat out on Fridays", "I usually nap 30m", "I work from 9 to 5"). These are casual chitchat or conversational context, NOT permanent profile memories!
                   - NEVER save answers to conversational questions (e.g. replying to what they are eating today, where they have lunch, how their day is going).
                   - NEVER save single-occurrence daily activities, logs, or events (e.g., "I napped for 30 minutes at noon", "I ran 5km today", "just finished lunch", "bought coffee for $3", "just woke up"). These are temporary daily activities or conversation remarks, NOT permanent user profile attributes!
                   - NEVER save transient states, momentary moods, or physical feelings (e.g., "I am tired", "bored today", "sleepy", "starting work now").
                   - NEVER save user questions, inquiries, or clarifications (e.g., "What should I call you?", "Why did you say that?", "Can you check my sleep?", "Do you remember me?"). Questions are never memories!
                   - NEVER save conversational follow-ups or dialog flow. Conversation history already maintains the dialog context; saveUserMemory is strictly for durable database persistence across future sessions.
                   - GOLDEN RULE: When the user is simply chatting, answering a question, or describing their daily routine without saying "remember this", DO NOT save. Return {"tools": []}.
                   - If in doubt, DO NOT call "saveUserMemory".

                5. ZERO-TOOL DEFAULT ({"tools": []}):
                   Return {"tools": []} whenever the user is:
                   - Having normal conversation or chitchat (e.g., greetings, thanking, acknowledging: "okay", "thanks", "got it").
                   - Answering or discussing daily activities, workplace lunches, meals, or schedules.
                   - Discussing, reacting to, or commenting on recent conversation context (e.g., "I took a 30m nap at noon", "Is that healthy?", "Sounds good").
                   - Asking general advice, opinions, or conceptual knowledge that doesn't require fetching user database records (e.g., "How long should a power nap be?", "How can I run faster?").
                   - Stating transient daily updates that don't need any tool execution.

                6. DATE RESOLUTION:
                   - If the user asks about past physical activity ("yesterday", "last night"), set date="yesterday".
                   - If the user asks about current physical activity ("today", "now"), set date="today".
                   - If a calendar date is specified, set date="YYYY-MM-DD".

                7. DOMAIN ISOLATION: Only select data tools relevant to the domain (do not select finance for health queries, or health for finance queries).

                OUTPUT FORMAT: Strict JSON only:
                - If tools are needed:
                  {
                    "tools": ["saveUserMemory"],
                    "date": "today" | "yesterday" | "YYYY-MM-DD",
                    "memory": {"category": "PROFILE|PERSONA|PREFERENCE|FITNESS|DIET|FINANCE|GOAL", "key": "...", "value": "..."}
                  }
                  NOTE: "tools" MUST be a flat JSON array of strings (e.g. ["saveUserMemory"]), NEVER an array of objects.
                - If no tools are needed:
                  {"tools": []}
                """.formatted(temporalContext.getFormattedPromptContext(), historyContext.toString());

        try {
            String rawJson = chatClient.prompt()
                    .system(plannerPrompt)
                    .user(userMessage)
                    .call()
                    .content();

            // Extract clean JSON block if model wrapped in markdown
            String cleaned = cleanJson(rawJson);
            log.info("Planner response: {}", cleaned);
            ToolPlan plan = parseToolPlan(cleaned);
            log.info("Tool execution plan: tools={}, date={}", plan.tools(), plan.date());
            return plan;
        } catch (Exception e) {
            log.warn("Planner fallback due to error: {}", e.getMessage());
            return heuristicFallbackPlan(userMessage);
        }
    }

    public ToolPlan parseToolPlan(String cleaned) throws Exception {
        JsonNode root = MAPPER.readTree(cleaned);
        List<String> tools = new ArrayList<>();
        MemoryPayload memory = null;

        JsonNode toolsNode = root.get("tools");
        if (toolsNode != null && toolsNode.isArray()) {
            for (JsonNode t : toolsNode) {
                if (t.isTextual()) {
                    tools.add(t.asText());
                } else if (t.isObject()) {
                    if (t.has("name")) {
                        tools.add(t.get("name").asText());
                    }
                    if (t.has("arguments")) {
                        JsonNode args = t.get("arguments");
                        if (args.has("category") || args.has("key") || args.has("value")) {
                            memory = new MemoryPayload(
                                    args.path("category").asText("GENERAL"),
                                    args.path("key").asText(""),
                                    args.path("value").asText("")
                            );
                        }
                    }
                }
            }
        }

        if (memory == null && root.has("memory")) {
            JsonNode m = root.get("memory");
            memory = new MemoryPayload(
                    m.path("category").asText("GENERAL"),
                    m.path("key").asText(""),
                    m.path("value").asText("")
            );
        }

        String date = root.has("date") && !root.get("date").isNull() ? root.get("date").asText("today") : "today";
        return new ToolPlan(tools, date, memory);
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
                    case "syncGarminData" -> data.put("live_garmin_sync", assistantTools.syncGarminData());
                    case "getDailyHealth" -> data.put("daily_health_" + targetDate, assistantTools.getDailyHealth(targetDate));
                    case "getHealthReadiness" -> data.put("readiness_" + targetDate, assistantTools.getHealthReadiness(targetDate));
                    case "getSleepAnalysis" -> data.put("sleep_analysis", assistantTools.getSleepAnalysis());
                    case "getWeightTrend" -> data.put("weight_trend", assistantTools.getWeightTrend());
                    case "getFinancialStatus" -> data.put("financial_status", assistantTools.getFinancialStatus());
                    case "getRecentTransactions" -> data.put("recent_transactions", assistantTools.getRecentTransactions(5));
                    case "getPendingTransactions" -> data.put("pending_transactions", assistantTools.getPendingTransactions());
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
        sb.append("You are the Personal Life OS Agent (PLOA), a private, highly intelligent personal life companion for physical health, recovery, fitness, and personal finance.\n\n");
        sb.append(temporalContext.getFormattedPromptContext()).append("\n\n");

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
                1. STRICTLY ADHERE to all rules, personas, forms of address, pronouns, tone instructions, and life preferences specified in "USER PROFILE & LONG-TERM MEMORIES" above (e.g. if the user asked to be addressed with specific pronouns or style in any language, you MUST strictly use that style).
                2. Answer FREELY, naturally, and conversationally based strictly on what the user is asking.
                3. You are NOT constrained to any rigid format or pre-set template.
                4. The retrieved data above is your single source of truth for the user's real numbers. Never invent or falsify metrics.
                5. If specific data was retrieved for 'yesterday' or another date, answer accurately for that requested date!
                6. If a new memory was saved during this turn (present in RETRIEVED GROUNDING DATA as "memory_saved"), acknowledge it naturally and confirm you will remember it.
                7. MATCH THE USER'S LANGUAGE AND TONE:
                   - If asked in Vietnamese, respond in natural Vietnamese.
                   - If the user uses casual or informal tone, match their casual, candid vibe naturally.
                   - If asked in English, reply in English.
                8. Format cleanly using standard Markdown (*bold*, lists, emojis). Avoid unclosed formatting markers.
                9. REAL-TIME TEMPORAL AWARENESS:
                   - You are strictly aware of the exact real-time timestamp, date, day of week, and time of day (%s, %s) specified in "CURRENT TEMPORAL CONTEXT" at the top of the prompt.
                   - Always align greetings (e.g. good morning, good afternoon, good evening), recommendations, and commentary on meals, sleep, or workouts with the current time of day.
                   - Relative temporal references ("today", "yesterday", "last night", "this morning", "tonight", "right now", "earlier") MUST be interpreted against this exact timestamp.
                """.formatted(temporalContext.getTimeOfDay(), temporalContext.getDayOfWeek()));

        return sb.toString();
    }

    private ToolPlan heuristicFallbackPlan(String text) {
        String t = text.toLowerCase();
        List<String> tools = new ArrayList<>();
        String date = t.contains("yesterday") || t.contains("last night") ? "yesterday" : "today";

        if (t.contains("sync") || t.contains("refresh")) {
            tools.add("syncGarminData");
        }
        if (t.contains("run") || t.contains("walk") || t.contains("step") || t.contains("calorie") || t.contains("kcal")
                || t.contains("activity") || t.contains("battery") || t.contains("stress") || t.contains("heart")) {
            tools.add("getDailyHealth");
        }
        if (t.contains("sleep")) {
            tools.add("getSleepAnalysis");
        }
        if (t.contains("recovery") || t.contains("ready") || t.contains("readiness") || t.contains("workout")
                || t.contains("train") || t.contains("fatigue") || t.contains("tired")) {
            tools.add("getHealthReadiness");
        }
        if (t.contains("money") || t.contains("spend") || t.contains("budget") || t.contains("burn")
                || t.contains("allowance") || t.contains("expense") || t.contains("cost") || t.contains("bill")
                || t.contains("rent") || t.contains("done")) {
            tools.add("getFinancialStatus");
            tools.add("getPendingTransactions");
        }
        if (t.contains("weight") || t.contains("scale") || t.contains("kg") || t.contains("lbs")) {
            tools.add("getWeightTrend");
        }

        return new ToolPlan(tools, date, null);
    }

    private String cleanJson(String raw) {
        if (raw == null || raw.isBlank()) return "{\"tools\":[]}";
        String s = raw.trim();
        int firstBrace = s.indexOf('{');
        int lastBrace = s.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return s.substring(firstBrace, lastBrace + 1);
        }
        return s;
    }
}
