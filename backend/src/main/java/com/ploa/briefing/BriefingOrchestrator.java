package com.ploa.briefing;

import com.ploa.ai.TemporalContext;
import com.ploa.assistant.memory.MemoryService;
import com.ploa.assistant.memory.UserMemory;
import com.ploa.briefing.dto.DailyBriefingContext;
import com.ploa.config.PloaProperties;
import com.ploa.finance.engine.dto.BurnRateResult;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.service.FinanceService;
import com.ploa.health.engine.dto.ReadinessResult;
import com.ploa.health.engine.dto.WeightEmaResult;
import com.ploa.health.model.HealthMetric;
import com.ploa.health.service.ActivityService;
import com.ploa.health.service.HealthService;
import com.ploa.profile.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Morning briefing & Evening summary orchestrator.
 * - Morning Briefing (07:30 AM): Synthesizes physical readiness, sleep debt, body composition, and financial pacing.
 * - Evening Summary (09:30 PM / 21:30): Complete recap of the day's activity, caloric burn, energy drain, stress, expenses, and wind-down advice.
 *
 * LLM is STRICTLY FORBIDDEN from calculating or inventing math — it only interprets pre-computed empirical data.
 */
@Component
public class BriefingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BriefingOrchestrator.class);
    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    private static final String SYSTEM_PROMPT_MORNING = """
            You are a personal health, recovery, and finance executive advisor.
            You receive pre-calculated metrics — DO NOT recalculate or falsify any numbers.
            Your job: write a concise, motivating morning briefing (max 200 words) in English.
            
            Structure:
            1. 🏃 Recovery: Interpret readiness level, factor in yesterday's workout strain if any, and recommend today's exercise intensity.
            2. 💤 Sleep: Comment on last night's sleep and any debt.
            3. ⚖️ Weight: Brief comment on EMA trend vs target.
            4. 💰 Finance: Comment on spending pace vs budget.
            5. 🎯 Focus: One actionable priority for today.
            
            Tone: direct, encouraging, no fluff. Currency: VND (formatted with separators).
            DO NOT recalculate or re-derive any numbers — only interpret what's given.
            Format cleanly using Markdown (*bold*, lists, emojis).
            """;

    private static final String SYSTEM_PROMPT_EVENING = """
            You are a personal executive health, recovery, and finance advisor for the Personal Life OS Agent (PLOA).
            You receive pre-calculated empirical data for the day concluding at 9:30 PM (21:30).
            DO NOT recalculate or falsify any numbers — only synthesize and interpret what is given.
            Write a concise, reflective, and motivating Evening Summary (max 250 words) in English.
            
            Structure:
            1. 🏁 Daily Activity & Workouts: Specific athletic workouts completed today (sport, duration, calories, training effect), steps completed, distance (km), active calories burned vs total calories, active/intensity minutes.
            2. 🔋 Energy & Stress Dynamics: Body Battery ending level, highest/lowest, charged vs drained, average stress, max peak, and stress qualifier.
            3. 💰 Daily Financial Recap: Total spent today, breakdown of today's purchases/merchants, remaining monthly budget, and safe daily allowance for tomorrow.
            4. 🌙 Wind-down & Nightly Target: Actionable recovery recommendations for tonight (recommended sleep duration, winding down, sleep debt payoff).
            
            Tone: warm, reflective, encouraging, executive.
            If user profile/goals (e.g. running goal, diet) are provided, weave them in naturally.
            Format cleanly using Markdown (*bold*, lists, emojis).
            """;

    private final HealthService healthService;
    private final ActivityService activityService;
    private final FinanceService financeService;
    private final MemoryService memoryService;
    private final UserProfileService profileService;
    private final TemporalContext temporalContext;
    private final ChatClient chatClient;
    private final PloaProperties properties;
    private final com.ploa.ai.prompt.LocalizedPromptRegistry promptRegistry;
    private TelegramClient telegramClient;

    @Autowired
    public BriefingOrchestrator(HealthService healthService,
                                ActivityService activityService,
                                FinanceService financeService,
                                MemoryService memoryService,
                                UserProfileService profileService,
                                TemporalContext temporalContext,
                                ChatClient.Builder chatClientBuilder,
                                PloaProperties properties,
                                @Autowired(required = false) com.ploa.ai.prompt.LocalizedPromptRegistry promptRegistry) {
        this.healthService = healthService;
        this.activityService = activityService;
        this.financeService = financeService;
        this.memoryService = memoryService;
        this.profileService = profileService;
        this.temporalContext = temporalContext;
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
        this.promptRegistry = promptRegistry;
        String token = (properties != null && properties.telegram() != null) ? properties.telegram().botToken() : null;
        if (token != null && !token.isBlank()) {
            this.telegramClient = new OkHttpTelegramClient(token);
        }
    }

    public BriefingOrchestrator(HealthService healthService,
                                ActivityService activityService,
                                FinanceService financeService,
                                MemoryService memoryService,
                                UserProfileService profileService,
                                TemporalContext temporalContext,
                                ChatClient.Builder chatClientBuilder,
                                PloaProperties properties) {
        this(healthService, activityService, financeService, memoryService, profileService,
                temporalContext, chatClientBuilder, properties, null);
    }

    public com.ploa.ai.prompt.LocalizedPromptRegistry getPromptRegistry() {
        return promptRegistry;
    }

    private String getMorningPrompt() {
        if (promptRegistry != null) {
            return promptRegistry.getPrompt("briefing/morning-briefing");
        }
        return SYSTEM_PROMPT_MORNING;
    }

    private String getEveningPrompt() {
        if (promptRegistry != null) {
            return promptRegistry.getPrompt("briefing/evening-briefing");
        }
        return SYSTEM_PROMPT_EVENING;
    }

    // ── Morning Briefing (07:30 AM Asia/Ho_Chi_Minh) ──────────────────────────
    @Scheduled(cron = "${ploa.briefing.morning-cron:${ploa.briefing.cron:0 30 7 * * *}}",
               zone = "${ploa.briefing.timezone:Asia/Ho_Chi_Minh}")
    public void sendMorningBriefing() {
        log.info("Morning briefing scheduled trigger");
        try {
            LocalDate today = temporalContext.currentDate();
            DailyBriefingContext ctx = new DailyBriefingContext(
                    today,
                    healthService.computeReadiness(today),
                    healthService.computeWeightEma(),
                    financeService.getBurnRate(),
                    financeService.getCashFlow()
            );

            String briefingText = generateBriefing(ctx);
            sendToTelegram(briefingText);
            log.info("Morning briefing sent successfully to Telegram");
        } catch (Exception e) {
            log.error("Morning briefing failed: {}", e.getMessage(), e);
        }
    }

    public String generateBriefingPreview() {
        LocalDate today = temporalContext.currentDate();
        DailyBriefingContext ctx = new DailyBriefingContext(
                today,
                healthService.computeReadiness(today),
                healthService.computeWeightEma(),
                financeService.getBurnRate(),
                financeService.getCashFlow()
        );
        return generateBriefing(ctx);
    }

    private String generateBriefing(DailyBriefingContext ctx) {
        String context = buildMorningContextString(ctx);

        return chatClient.prompt()
                .advisors(a -> a.param("operation", "briefing_morning"))
                .system(getMorningPrompt())
                .user(context)
                .call()
                .content();
    }

    // ── Evening Summary (09:30 PM / 21:30 Asia/Ho_Chi_Minh) ──────────────────
    @Scheduled(cron = "${ploa.briefing.evening-cron:0 30 21 * * *}",
               zone = "${ploa.briefing.timezone:Asia/Ho_Chi_Minh}")
    public void sendEveningSummary() {
        log.info("Evening summary scheduled trigger (21:30)");
        try {
            String summaryText = generateEveningSummaryPreview();
            sendToTelegram(summaryText);
            log.info("Evening summary sent successfully to Telegram");
        } catch (Exception e) {
            log.error("Evening summary failed: {}", e.getMessage(), e);
        }
    }

    public String generateEveningSummaryPreview() {
        LocalDate today = temporalContext.currentDate();
        HealthMetric health = healthService.getMetricsForDate(today);
        ReadinessResult readiness = healthService.computeReadiness(today);
        WeightEmaResult weight = healthService.computeWeightEma();
        BurnRateResult burnRate = financeService.getBurnRate();
        List<Transaction> todayTxs = financeService.getTransactionsForDate(today);
        Long todaySpent = financeService.getSpentForDate(today);
        List<UserMemory> profile = memoryService.getAllPreferences();

        String context = buildEveningContextString(today, health, readiness, weight, burnRate, todayTxs, todaySpent, profile);

        return chatClient.prompt()
                .advisors(a -> a.param("operation", "briefing_evening"))
                .system(getEveningPrompt())
                .user(context)
                .call()
                .content();
    }

    // ── Context Builders ──────────────────────────────────────────────────────

    private String buildMorningContextString(DailyBriefingContext ctx) {
        var r = ctx.readiness();
        var w = ctx.weightEma();
        var f = ctx.burnRate();
        String yesterdayWorkouts = activityService.getWorkoutSummaryText(ctx.date().minusDays(1));

        String base = """
                %s
                
                === DAILY MORNING BRIEFING DATA for %s ===
                
                --- HEALTH & RECOVERY ---
                Readiness Level: %s
                HRV rMSSD today: %s ms  |  30d mean: %s ms  |  Z-score: %s
                RHR today: %s bpm  |  30d mean: %s bpm  |  Z-score: %s
                Sleep last night: %s min total (%s min deep, %s min REM)
                7-day sleep debt: %s min vs target of %.0f min/night
                Restorative ratio (Deep+REM/Total): %s%%
                Yesterday's workouts: %s
                
                --- BODY COMPOSITION ---
                Latest weight: %s kg
                7-day EMA weight: %s kg
                Target weight: %s kg
                Delta to target: %s kg
                Weekly trend: %s kg/week
                
                --- FINANCES (Month-to-Date) ---
                Monthly budget: %s VND
                MTD total spend: %s VND
                MTD discretionary: %s VND
                MTD fixed: %s VND
                Remaining budget: %s VND
                Avg daily burn rate: %s VND/day
                Safe daily allowance: %s VND/day
                Days elapsed: %d / Days remaining: %d
                Top categories: %s
                """.formatted(
                temporalContext.getFormattedPromptContext(),
                ctx.date(),
                r != null ? r.level() : "N/A",
                fmt(r != null ? r.hrvRmssd() : null), fmt(r != null ? r.hrv30dMean() : null), fmt2(r != null ? r.hrvZScore() : null),
                fmt(r != null && r.rhr() != null ? r.rhr().doubleValue() : null),
                fmt2(r != null ? r.rhr30dMean() : null), fmt2(r != null ? r.rhrZScore() : null),
                fmt(r != null && r.sleepTotalMin() != null ? r.sleepTotalMin().doubleValue() : null),
                fmt(r != null && r.sleepDeepMin() != null ? r.sleepDeepMin().doubleValue() : null),
                fmt(r != null && r.sleepRemMin() != null ? r.sleepRemMin().doubleValue() : null),
                r != null ? r.sleepDebtMin() : "N/A",
                profileService.getSleepTargetHours() * 60,
                fmt2(r != null ? r.restorativeRatioPct() : null),
                yesterdayWorkouts,
                w != null ? w.latestWeightKg() : "N/A",
                w != null ? w.ema7Kg() : "N/A",
                w != null && w.targetWeightKg() != null ? w.targetWeightKg() : profileService.getTargetWeightKg(),
                w != null ? w.deltaToTargetKg() : "N/A",
                w != null ? w.weeklyTrendKg() : "N/A",
                VND.format(f.monthlyBudgetVnd()),
                VND.format(f.mtdTotalSpendVnd()),
                VND.format(f.mtdDiscretionaryVnd()),
                VND.format(f.mtdFixedVnd()),
                VND.format(f.remainingBudgetVnd()),
                VND.format((long) f.dailyBurnRateVnd()),
                VND.format((long) f.safeDailyAllowanceVnd()),
                f.daysElapsed(), f.daysRemaining(),
                f.categoryBreakdown().toString()
        );

        var cf = ctx.cashFlow();
        if (cf != null) {
            base += """

                --- CASH FLOW & DEBT LIQUIDITY ---
                Liquid cash available now: %s VND
                Expected inflows before month-end: %s VND
                Committed unpaid debt before month-end: %s VND
                Spendable cash now: %s VND
                Cash-aware safe daily allowance: %s VND/day
                Liquidity status: %s
                Advisory note: %s
                """.formatted(
                    VND.format(cf.liquidCashNow().longValue()),
                    VND.format(cf.expectedInflowsBeforeMonthEnd().longValue()),
                    VND.format(cf.unpaidDebtObligationsBeforeMonthEnd().longValue()),
                    VND.format(cf.spendableCash().longValue()),
                    VND.format(cf.safeDailyAllowance().longValue()),
                    cf.liquidityStatus(),
                    cf.recommendation()
            );
        }
        return base;
    }

    private String buildEveningContextString(LocalDate today,
                                             HealthMetric h,
                                             ReadinessResult r,
                                             WeightEmaResult w,
                                             BurnRateResult f,
                                             List<Transaction> todayTxs,
                                             Long todaySpent,
                                             List<UserMemory> profile) {
        StringBuilder txs = new StringBuilder();
        if (todayTxs.isEmpty()) {
            txs.append("No expenses logged today.");
        } else {
            for (Transaction t : todayTxs) {
                txs.append(String.format("• %s VND | %s (%s) | %s%s\n",
                        VND.format(t.getAmount()),
                        t.getCategory(),
                        t.getSpendType(),
                        t.getMerchant() != null ? t.getMerchant() + ": " : "",
                        t.getNote() != null ? t.getNote() : ""));
            }
        }

        StringBuilder prof = new StringBuilder();
        if (profile != null && !profile.isEmpty()) {
            for (UserMemory m : profile) {
                prof.append("• [").append(m.getCategory()).append("] ")
                        .append(m.getMemoryKey()).append(": ")
                        .append(m.getMemoryValue()).append("\n");
            }
        } else {
            prof.append("Standard profile.\n");
        }

        return """
                %s
                
                === EVENING SUMMARY EMPIRICAL DATA for %s ===
                
                --- USER PROFILE & GOALS ---
                %s
                
                --- PHYSICAL ACTIVITY & STRAIN TODAY ---
                Workouts & Sessions: %s
                Steps completed: %s
                Distance: %s km
                Active Calories: %s kcal  |  Total Calories: %s kcal
                Active Time: %s min (Moderate: %s min, Vigorous: %s min)
                
                --- ENERGY RESERVES & STRESS TODAY ---
                Body Battery Ending: %s/100 (Highest: %s, Lowest: %s)
                Body Battery Charged: +%s  |  Drained: -%s
                Stress Level: %s/100 (Max Peak: %s, Qualifier: %s)
                Resting Heart Rate: %s bpm (Min: %s, Max: %s)
                SpO2 Average: %s%%  |  Respiration Average: %s breaths/min
                
                --- MORNING BASELINE & SLEEP FROM PREVIOUS NIGHT ---
                Morning Readiness Level: %s (HRV Z: %s, RHR Z: %s)
                Sleep duration last night: %s min (Deep: %s, REM: %s)
                7-day cumulative sleep debt: %s min
                
                --- TODAY'S FINANCIAL RECAP ---
                Total Spent Today: %s VND
                Today's Transactions:
                %s
                
                --- MONTHLY PACING FOR TOMORROW ---
                Remaining Monthly Budget: %s VND
                Updated Safe Daily Allowance: %s VND/day
                Days Remaining in Month: %d
                """.formatted(
                temporalContext.getFormattedPromptContext(),
                today,
                prof.toString(),
                activityService.getWorkoutSummaryText(today),
                h != null && h.getSteps() != null ? String.format("%,d", h.getSteps()) : "N/A",
                h != null && h.getDistanceMeters() != null ? String.format("%.2f", h.getDistanceMeters() / 1000.0) : "N/A",
                h != null && h.getActiveKcal() != null ? h.getActiveKcal() : "N/A",
                h != null && h.getTotalKcal() != null ? h.getTotalKcal() : "N/A",
                h != null && h.getActiveTimeMin() != null ? h.getActiveTimeMin() : "N/A",
                h != null && h.getModerateMin() != null ? h.getModerateMin() : "N/A",
                h != null && h.getVigorousMin() != null ? h.getVigorousMin() : "N/A",
                h != null && h.getBodyBatteryCurrent() != null ? h.getBodyBatteryCurrent() : "N/A",
                h != null && h.getBodyBatteryHighest() != null ? h.getBodyBatteryHighest() : "N/A",
                h != null && h.getBodyBatteryLowest() != null ? h.getBodyBatteryLowest() : "N/A",
                h != null && h.getBodyBatteryCharged() != null ? h.getBodyBatteryCharged() : "N/A",
                h != null && h.getBodyBatteryDrained() != null ? h.getBodyBatteryDrained() : "N/A",
                h != null && h.getStressAvg() != null ? h.getStressAvg() : "N/A",
                h != null && h.getStressMax() != null ? h.getStressMax() : "N/A",
                h != null && h.getStressQualifier() != null ? h.getStressQualifier() : "N/A",
                h != null && h.getRhr() != null ? h.getRhr() : "N/A",
                h != null && h.getMinHr() != null ? h.getMinHr() : "N/A",
                h != null && h.getMaxHr() != null ? h.getMaxHr() : "N/A",
                fmt(h != null ? h.getSpo2Avg() : null),
                fmt(h != null ? h.getRespirationAvg() : null),
                r != null ? r.level() : "N/A",
                fmt2(r != null ? r.hrvZScore() : null),
                fmt2(r != null ? r.rhrZScore() : null),
                fmt(r != null && r.sleepTotalMin() != null ? r.sleepTotalMin().doubleValue() : null),
                fmt(r != null && r.sleepDeepMin() != null ? r.sleepDeepMin().doubleValue() : null),
                fmt(r != null && r.sleepRemMin() != null ? r.sleepRemMin().doubleValue() : null),
                r != null ? r.sleepDebtMin() : "N/A",
                todaySpent != null ? VND.format(todaySpent) : "0",
                txs.toString(),
                VND.format(f.remainingBudgetVnd()),
                VND.format((long) f.safeDailyAllowanceVnd()),
                f.daysRemaining()
        );
    }

    private void sendToTelegram(String text) {
        if (properties == null || properties.telegram() == null || !properties.telegram().enabled()) {
            log.info("Telegram is disabled, skipping push briefing.");
            return;
        }
        if (telegramClient == null) {
            String token = (properties != null && properties.telegram() != null) ? properties.telegram().botToken() : null;
            if (token != null && !token.isBlank()) {
                telegramClient = new OkHttpTelegramClient(token);
            } else {
                log.warn("Telegram bot token not configured, skipping push briefing.");
                return;
            }
        }
        String html = com.ploa.bot.TelegramFormatter.markdownToHtml(text);
        properties.telegram().allowedChatIdList().forEach(chatId -> {
            try {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(html)
                        .parseMode("HTML")
                        .build());
            } catch (Exception e) {
                log.warn("Failed to send HTML briefing to chat {}, retrying plain text: {}", chatId, e.getMessage());
                try {
                    telegramClient.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text(text)
                            .build());
                } catch (Exception ex) {
                    log.error("Failed to send briefing to chat {}: {}", chatId, ex.getMessage());
                }
            }
        });
    }

    private String fmt(Double value) {
        return value != null ? String.format("%.1f", value) : "N/A";
    }

    private String fmt2(Double value) {
        return value != null ? String.format("%.2f", value) : "N/A";
    }
}
