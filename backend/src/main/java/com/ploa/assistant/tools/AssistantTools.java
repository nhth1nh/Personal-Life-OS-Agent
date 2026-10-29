package com.ploa.assistant.tools;

import com.ploa.assistant.memory.MemoryService;
import com.ploa.assistant.memory.UserMemory;
import com.ploa.finance.api.dto.CreateIncomeRequest;
import com.ploa.finance.api.dto.CreateTransactionRequest;
import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.engine.dto.CashFlowSummary;
import com.ploa.finance.engine.dto.PayoffSimulationComparison;
import com.ploa.finance.model.*;
import com.ploa.finance.service.DebtService;
import com.ploa.finance.service.IncomeService;
import java.math.BigDecimal;
import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.service.FinanceService;
import com.ploa.health.engine.dto.ReadinessResult;
import com.ploa.health.engine.dto.SleepResult;
import com.ploa.health.engine.dto.WeightEmaResult;
import com.ploa.health.api.dto.ActivitySummaryResponse;
import com.ploa.health.model.HealthMetric;
import com.ploa.health.service.ActivityService;
import com.ploa.health.service.HealthService;
import com.ploa.profile.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ActivityService activityService;
    private final FinanceService financeService;
    private final DebtService debtService;
    private final IncomeService incomeService;
    private final MemoryService memoryService;
    private final UserProfileService profileService;

    @Autowired
    public AssistantTools(HealthService healthService,
                          ActivityService activityService,
                          FinanceService financeService,
                          DebtService debtService,
                          IncomeService incomeService,
                          MemoryService memoryService,
                          UserProfileService profileService) {
        this.healthService = healthService;
        this.activityService = activityService;
        this.financeService = financeService;
        this.debtService = debtService;
        this.incomeService = incomeService;
        this.memoryService = memoryService;
        this.profileService = profileService;
    }

    public record DebtSummary(
            String id,
            String name,
            String creditor,
            String debtType,
            BigDecimal currentPrincipal,
            BigDecimal interestRateApr,
            BigDecimal monthlyMinimumDue,
            LocalDate maturityDate
    ) {}

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

    @Tool(description = "Get detailed athletic workouts and sports activities recorded on Garmin (e.g. running, badminton, cycling, gym, swimming) for a specific date or recent period. Includes duration, calories burned, average/max HR, aerobic & anaerobic training effect, pace, speed, and training load.")
    public List<ActivitySummaryResponse> getWorkouts(
            @ToolParam(description = "Target date: 'today', 'yesterday', or 'YYYY-MM-DD'. Leave empty or 'recent' for recent workouts.", required = false) String date,
            @ToolParam(description = "Limit of recent workouts to retrieve if date is omitted (default 10).", required = false) Integer limit) {
        if (date != null && !date.isBlank() && !date.equalsIgnoreCase("recent") && !date.equalsIgnoreCase("recently") && !date.equalsIgnoreCase("all")) {
            LocalDate target = parseDate(date);
            log.info("Assistant tool call: getWorkouts(date={})", target);
            return activityService.getActivitiesForDate(target);
        }
        int l = (limit != null && limit > 0) ? limit : 10;
        log.info("Assistant tool call: getWorkouts(limit={})", l);
        return activityService.getRecentActivities(l);
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

    @Tool(description = "Get real-world cash-flow waterfall: liquid cash available now, expected inflows before month-end, committed unpaid debt, committed fixed bills, emergency reserve floor, spendable cash, safe daily allowance, and liquidity status (SOLVENT, PROJECTED_SHORTFALL, LIQUIDITY_DEFICIT).")
    public CashFlowSummary getCashFlowStatus() {
        log.info("Assistant tool call: getCashFlowStatus()");
        return financeService.getCashFlow();
    }

    @Tool(description = "Get the user's active debt obligations and liabilities (lender, debt type, principal balance, interest rate APR, monthly minimum payment due, maturity date).")
    public List<DebtSummary> getDebtPortfolio() {
        log.info("Assistant tool call: getDebtPortfolio()");
        if (debtService == null) return List.of();
        return debtService.getActiveDebts().stream()
                .map(d -> new DebtSummary(
                        d.getId().toString(),
                        d.getName(),
                        d.getCreditor(),
                        d.getDebtType().name(),
                        d.getCurrentPrincipal(),
                        d.getInterestRateApr(),
                        d.getMonthlyMinimumDue(),
                        d.getMaturityDate()
                ))
                .toList();
    }

    @Tool(description = "Simulate debt payoff acceleration comparing Baseline vs Avalanche (highest APR first) vs Snowball (smallest balance first) for a given monthly surplus cash injection in VND.")
    public PayoffSimulationComparison simulateDebtPayoff(
            @ToolParam(description = "Monthly extra surplus cash in VND (e.g. 1000000, 3000000, 5000000). Defaults to 0.", required = false) Long monthlySurplusVnd) {
        BigDecimal surplus = monthlySurplusVnd != null ? BigDecimal.valueOf(monthlySurplusVnd) : BigDecimal.ZERO;
        log.info("Assistant tool call: simulateDebtPayoff(surplus={})", surplus);
        return debtService != null ? debtService.simulatePayoff(surplus) : null;
    }

    @Tool(description = "Record a manual financial expenditure directly (amount in VND, category, merchant, note, spend type).")
    public String recordExpense(
            @ToolParam(description = "Amount in VND (e.g. 50000, 250000)") long amountVnd,
            @ToolParam(description = "Category (e.g. Food & Dining, Groceries, Transport, Bills, Shopping)", required = false) String category,
            @ToolParam(description = "Merchant or place (e.g. Grab, Starbucks, WinMart)", required = false) String merchant,
            @ToolParam(description = "Note or description", required = false) String note,
            @ToolParam(description = "Spend type: 'DISCRETIONARY' (default) or 'FIXED'", required = false) String spendType) {
        SpendType st = "FIXED".equalsIgnoreCase(spendType) ? SpendType.FIXED : SpendType.DISCRETIONARY;
        CreateTransactionRequest req = new CreateTransactionRequest(
                amountVnd,
                category != null ? category : "General",
                st,
                st == SpendType.FIXED ? TransactionType.EXPENSE_FIXED : TransactionType.EXPENSE_DISCRETIONARY,
                merchant,
                LocalDate.now(),
                null,
                note
        );
        Transaction tx = financeService.createDirectExpense(req);
        return String.format("Recorded expense: %,d VND for '%s' at %s (Category: %s)",
                tx.getAmount(), tx.getNote() != null ? tx.getNote() : "Expense",
                tx.getMerchant() != null ? tx.getMerchant() : "Unknown", tx.getCategory());
    }

    @Tool(description = "Record an expected or received income stream (source/client/employer, amount in VND, expected date, income type: SALARY, FREELANCE, BONUS, INVESTMENT, OTHER).")
    public String recordIncome(
            @ToolParam(description = "Income source (e.g. Google, Client Acme, Techcombank Dividend)") String source,
            @ToolParam(description = "Amount in VND (e.g. 43000000, 15000000)") long amountVnd,
            @ToolParam(description = "Expected date YYYY-MM-DD (defaults to today)", required = false) String expectedDate,
            @ToolParam(description = "Income type: 'SALARY', 'FREELANCE', 'BONUS', 'INVESTMENT', 'OTHER'", required = false) String incomeType) {
        LocalDate date = parseDate(expectedDate);
        IncomeType type = IncomeType.SALARY;
        if (incomeType != null) {
            try {
                type = IncomeType.valueOf(incomeType.toUpperCase());
            } catch (Exception ignored) {}
        }
        CreateIncomeRequest req = new CreateIncomeRequest(
                source,
                BigDecimal.valueOf(amountVnd),
                null,
                date,
                null,
                type,
                type == IncomeType.SALARY ? 1.0 : 0.7,
                false,
                IncomeFrequency.MONTHLY,
                IncomeStatus.EXPECTED,
                "Logged via Assistant"
        );
        Income income = incomeService != null ? incomeService.createIncome(req) : null;
        return String.format("Recorded income: %,d VND from '%s' expected on %s",
                amountVnd, source, date);
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
