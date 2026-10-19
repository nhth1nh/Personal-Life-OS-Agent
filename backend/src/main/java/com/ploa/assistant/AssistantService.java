package com.ploa.assistant;

import com.ploa.finance.service.FinanceService;
import com.ploa.health.service.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    private final HealthService healthService;
    private final FinanceService financeService;
    private final ChatClient chatClient;

    public AssistantService(HealthService healthService,
                            FinanceService financeService,
                            ChatClient.Builder chatClientBuilder) {
        this.healthService = healthService;
        this.financeService = financeService;
        this.chatClient = chatClientBuilder.build();
    }

    public String chat(String userMessage) {
        log.info("Assistant chat query received: {}", userMessage);
        String context = buildSystemContext();

        String systemPrompt = """
                You are the Personal Life OS Agent (PLOA), a private, intelligent health, fitness, and personal finance advisor.
                You have real-time access to the user's latest physical recovery (Garmin) and financial budget data.

                === CURRENT REAL-TIME METRICS ===
                %s
                =================================

                CORE GUIDELINES:
                1. Answer directly, concisely, and insightfully.
                2. Respond in the language used by the user (English by default, or the user's language).
                3. GROUND YOUR ANSWERS IN THE REAL PRE-CALCULATED NUMBERS ABOVE.
                   - For health questions: mention readiness level, HRV Z-score, RHR, sleep stages, and sleep debt.
                   - For workout/exercise questions: if readiness is STRESSED (e.g. HRV Z < -1.5), advise against high intensity; if NORMAL/PRIME, encourage moderate/hard training.
                   - For weight/diet questions: mention current weight, 7-day EMA, target weight, and weekly trend.
                   - For finance questions: mention remaining budget, days remaining, daily burn rate, and safe daily allowance.
                4. STRICT RULE: You do NOT calculate or re-derive math formulas — you only interpret and advise based on the deterministic metrics provided.
                5. Keep replies friendly, practical, and action-oriented. Format using clean Markdown with appropriate emojis.
                """.formatted(context);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    private String buildSystemContext() {
        LocalDate today = LocalDate.now();
        var r = healthService.computeReadiness(today);
        var w = healthService.computeWeightEma();
        var f = financeService.getBurnRate();

        return """
                Date: %s
                [HEALTH & RECOVERY]
                - Readiness Level: %s
                - HRV rMSSD: %s ms (30-day Baseline: %s ms, Z-Score: %s)
                - Resting Heart Rate: %s bpm (30-day Baseline: %s bpm, Z-Score: %s)
                - Sleep last night: %s min (Deep: %s min, REM: %s min)
                - 7-day Sleep Debt: %s min
                - Restorative Sleep Ratio: %s%%
                - Health Engine Assessment: %s

                [BODY COMPOSITION]
                - Latest Weight: %s kg (Logged: %s)
                - 7-day EMA Weight: %s kg
                - Target Weight: %s kg (Delta to target: %s kg)
                - Weekly Trend: %s kg/week

                [FINANCES (Month-to-Date)]
                - Monthly Budget: %s VND
                - MTD Total Spend: %s VND (Discretionary: %s VND, Fixed: %s VND)
                - Remaining Budget: %s VND
                - Days Elapsed: %d / Days Remaining: %d
                - Average Daily Burn Rate: %s VND/day
                - Safe Daily Allowance: %s VND/day
                - Monthly Savings Rate: %.1f%%
                """.formatted(
                today,
                r.level(),
                fmt(r.hrvRmssd()), fmt(r.hrv30dMean()), fmt2(r.hrvZScore()),
                r.rhr() != null ? r.rhr() : "N/A", fmt2(r.rhr30dMean()), fmt2(r.rhrZScore()),
                r.sleepTotalMin() != null ? r.sleepTotalMin() : "N/A",
                r.sleepDeepMin() != null ? r.sleepDeepMin() : "N/A",
                r.sleepRemMin() != null ? r.sleepRemMin() : "N/A",
                r.sleepDebtMin() != null ? r.sleepDebtMin() : "N/A",
                fmt2(r.restorativeRatioPct()),
                r.summary(),
                w.latestWeightKg() != null ? w.latestWeightKg() : "N/A",
                w.lastMeasurementDate() != null ? w.lastMeasurementDate() : "N/A",
                w.ema7Kg() != null ? w.ema7Kg() : "N/A",
                w.targetWeightKg() != null ? w.targetWeightKg() : "N/A",
                w.deltaToTargetKg() != null ? String.format("%+.2f", w.deltaToTargetKg()) : "N/A",
                w.weeklyTrendKg() != null ? String.format("%+.2f", w.weeklyTrendKg()) : "N/A",
                VND.format(f.monthlyBudgetVnd()),
                VND.format(f.mtdTotalSpendVnd()),
                VND.format(f.mtdDiscretionaryVnd()),
                VND.format(f.mtdFixedVnd()),
                VND.format(f.remainingBudgetVnd()),
                f.daysElapsed(), f.daysRemaining(),
                VND.format((long) f.dailyBurnRateVnd()),
                VND.format((long) f.safeDailyAllowanceVnd()),
                f.monthlySavingsRatePct()
        );
    }

    private String fmt(Double v) {
        return v != null ? String.format("%.1f", v) : "N/A";
    }

    private String fmt2(Double v) {
        return v != null ? String.format("%.2f", v) : "N/A";
    }
}
