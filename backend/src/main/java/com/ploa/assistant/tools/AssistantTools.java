package com.ploa.assistant.tools;

import com.ploa.assistant.memory.MemoryService;
import com.ploa.assistant.memory.UserMemory;
import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.service.FinanceService;
import com.ploa.health.engine.dto.ReadinessResult;
import com.ploa.health.engine.dto.SleepResult;
import com.ploa.health.engine.dto.WeightEmaResult;
import com.ploa.health.model.HealthMetric;
import com.ploa.health.service.HealthService;
import com.ploa.profile.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Spring AI Tool functions available to the Life OS Assistant.
 * Exclusive, atomic tools that the AI can call ONLY when specific data is required.
 */
@Component
public class AssistantTools {

    private static final Logger log = LoggerFactory.getLogger(AssistantTools.class);

    private final HealthService healthService;
    private final FinanceService financeService;
    private final MemoryService memoryService;
    private final UserProfileService profileService;

    public AssistantTools(HealthService healthService,
                          FinanceService financeService,
                          MemoryService memoryService,
                          UserProfileService profileService) {
        this.healthService = healthService;
        this.financeService = financeService;
        this.memoryService = memoryService;
        this.profileService = profileService;
    }

    public record DailyHealthSummary(
            LocalDate date,
            Integer steps,
            Integer distanceMeters,
            Integer activeKcal,
            Integer totalKcal,
            Integer activeTimeMin,
            Integer moderateMin,
            Integer vigorousMin,
            Integer bodyBatteryCurrent,
            Integer bodyBatteryHighest,
            Integer bodyBatteryLowest,
            Integer stressAvg,
            Integer stressMax,
            String stressQualifier,
            Integer restingHr,
            Integer minHr,
            Integer maxHr,
            Double spo2Avg,
            Double respirationAvg
    ) {}

    public record TransactionSummary(
            LocalDate date,
            Long amount,
            String currency,
            String category,
            SpendType spendType,
            String merchant,
            String note,
            boolean confirmed
    ) {}

    @Tool(description = "Get physical biometrics for a specific date (steps, active/total calories, active minutes, Body Battery, stress, resting HR, min/max HR, SpO2). Supports date 'today', 'yesterday', or 'YYYY-MM-DD'.")
    public DailyHealthSummary getDailyHealth(
            @ToolParam(description = "Target date: 'today', 'yesterday', or specific 'YYYY-MM-DD'. Defaults to 'today'.", required = false) String date) {
        LocalDate target = parseDate(date);
        log.info("Assistant tool call: getDailyHealth({})", target);
        HealthMetric m = healthService.getMetricsForDate(target);
        if (m == null) {
            return null;
        }
        return new DailyHealthSummary(
                m.getDate(),
                m.getSteps(),
                m.getDistanceMeters(),
                m.getActiveKcal(),
                m.getTotalKcal(),
                m.getActiveTimeMin(),
                m.getModerateMin(),
                m.getVigorousMin(),
                m.getBodyBatteryCurrent(),
                m.getBodyBatteryHighest(),
                m.getBodyBatteryLowest(),
                m.getStressAvg(),
                m.getStressMax(),
                m.getStressQualifier(),
                m.getRhr(),
                m.getMinHr(),
                m.getMaxHr(),
                m.getSpo2Avg(),
                m.getRespirationAvg()
        );
    }

    @Tool(description = "Trigger an immediate live synchronization with Garmin Connect to pull up-to-the-second biometrics from the user's Garmin watch (steps, active calories, Body Battery, stress, heart rate). Call this ONLY when the user explicitly asks to sync, refresh, or update data from their Garmin watch/tracker.")
    public DailyHealthSummary syncGarminData() {
        log.info("Assistant tool call: syncGarminData()");
        healthService.triggerGarminPull();
        return getDailyHealth("today");
    }

    @Tool(description = "Get physical recovery readiness & nervous system strain for a date. Includes overnight HRV (rMSSD), resting heart rate, 30-day baseline Z-scores, recovery level (PRIME, NORMAL, FATIGUED, STRESSED), and sleep debt.")
    public ReadinessResult getHealthReadiness(
            @ToolParam(description = "Target date: 'today', 'yesterday', or 'YYYY-MM-DD'. Defaults to 'today'.", required = false) String date) {
        LocalDate target = parseDate(date);
        log.info("Assistant tool call: getHealthReadiness({})", target);
        return healthService.computeReadiness(target);
    }

    @Tool(description = "Get sleep stage breakdown (deep, light, REM, total duration) and 7-day weekly sleep duration trend.")
    public SleepResult getSleepAnalysis() {
        log.info("Assistant tool call: getSleepAnalysis()");
        return healthService.computeSleepTrend();
    }

    @Tool(description = "Get body weight trend, 7-day Exponential Moving Average (EMA) smoothed weight, delta to target goal, and weekly weight velocity.")
    public WeightEmaResult getWeightTrend() {
        log.info("Assistant tool call: getWeightTrend()");
        return healthService.computeWeightEma();
    }

    @Tool(description = "Get month-to-date personal finance status: monthly budget, total spend, discretionary vs fixed spend, remaining budget, daily burn rate, safe daily allowance, and savings rate.")
    public BurnRateResult getFinancialStatus() {
        log.info("Assistant tool call: getFinancialStatus()");
        return financeService.getBurnRate();
    }

    @Tool(description = "Get recent logged financial transactions for the current month.")
    public List<TransactionSummary> getRecentTransactions(
            @ToolParam(description = "Maximum number of transactions to retrieve (e.g. 5, 10). Default 5.", required = false) Integer limit) {
        int l = (limit != null && limit > 0) ? limit : 5;
        log.info("Assistant tool call: getRecentTransactions(limit={})", l);
        List<Transaction> txs = financeService.getRecentTransactions(l);
        return txs.stream()
                .map(t -> new TransactionSummary(
                        t.getTxDate(),
                        t.getAmount(),
                        t.getCurrency(),
                        t.getCategory(),
                        t.getSpendType(),
                        t.getMerchant(),
                        t.getNote(),
                        t.isConfirmed()
                ))
                .toList();
    }

    @Tool(description = "Get pending draft transactions awaiting user confirmation or recently modified via receipt OCR.")
    public List<TransactionSummary> getPendingTransactions() {
        log.info("Assistant tool call: getPendingTransactions()");
        List<Transaction> txs = financeService.getPendingTransactions();
        return txs.stream()
                .map(t -> new TransactionSummary(
                        t.getTxDate(),
                        t.getAmount(),
                        t.getCurrency(),
                        t.getCategory(),
                        t.getSpendType(),
                        t.getMerchant(),
                        t.getNote(),
                        t.isConfirmed()
                ))
                .toList();
    }

    @Tool(description = "Get persistent user preferences, goals, dietary restrictions, and personal profile facts.")
    public List<UserMemory> getUserProfile() {
        log.info("Assistant tool call: getUserProfile()");
        return memoryService.getAllPreferences();
    }

    @Tool(description = "Persist enduring, long-term profile data or interaction rules (name, communication style, pronouns, dietary rules, budget rules, goals) to PostgreSQL. NEVER use for transient daily activities, momentary feelings, or conversation remarks.")
    public String saveUserMemory(
            @ToolParam(description = "Category: 'PROFILE', 'PERSONA', 'PREFERENCE', 'FITNESS', 'DIET', 'FINANCE', 'GOAL'") String category,
            @ToolParam(description = "Short identifier key, e.g. 'name', 'communication_style', 'diet_type', 'marathon_goal'") String key,
            @ToolParam(description = "Detailed preference value, e.g. 'Alex', 'Informal tone', 'Vegetarian'") String value) {
        log.info("Assistant tool call: saveUserMemory(category={}, key={}, value={})", category, key, value);
        UserMemory m = memoryService.savePreference(category, key, value, "CONVERSATION");
        return "Saved memory [" + m.getCategory() + " / " + m.getMemoryKey() + "]: " + m.getMemoryValue();
    }

    @Tool(description = "Update the user's monthly financial budget in VND. Call this when the user requests to update, change, or set their monthly spending budget.")
    public String updateMonthlyBudget(
            @ToolParam(description = "New monthly budget in VND (e.g. 10000000, 15000000)") long budgetVnd) {
        log.info("Assistant tool call: updateMonthlyBudget({})", budgetVnd);
        profileService.setMonthlyBudgetVnd(budgetVnd, "CONVERSATION");
        return String.format("Updated monthly budget to %,d VND", budgetVnd);
    }

    @Tool(description = "Update the user's personal health targets (target body weight in kg and/or daily sleep goal in hours). Call this when the user expresses a change in their target weight or sleep goal.")
    public String updateHealthTargets(
            @ToolParam(description = "Target body weight in kg (e.g. 65.0, 68.5). Pass null if not changing.", required = false) Double targetWeightKg,
            @ToolParam(description = "Target sleep duration in hours (e.g. 7.5, 8.0). Pass null if not changing.", required = false) Double sleepTargetHours) {
        log.info("Assistant tool call: updateHealthTargets(weight={}, sleep={})", targetWeightKg, sleepTargetHours);
        StringBuilder sb = new StringBuilder("Updated health targets: ");
        if (targetWeightKg != null && targetWeightKg > 0) {
            profileService.setTargetWeightKg(targetWeightKg, "CONVERSATION");
            sb.append(String.format("Target weight: %.1f kg. ", targetWeightKg));
        }
        if (sleepTargetHours != null && sleepTargetHours > 0) {
            profileService.setSleepTargetHours(sleepTargetHours, "CONVERSATION");
            sb.append(String.format("Sleep target: %.1f hours. ", sleepTargetHours));
        }
        return sb.toString().trim();
    }

    public LocalDate parseDate(String date) {
        if (date == null || date.isBlank() || "today".equalsIgnoreCase(date)) {
            return LocalDate.now();
        }
        if ("yesterday".equalsIgnoreCase(date)) {
            return LocalDate.now().minusDays(1);
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (Exception e) {
            log.warn("Invalid date format '{}', defaulting to today", date);
            return LocalDate.now();
        }
    }
}
