package com.ploa.briefing;

import com.ploa.briefing.dto.DailyBriefingContext;
import com.ploa.config.PloaProperties;
import com.ploa.finance.service.FinanceService;
import com.ploa.health.service.HealthService;
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
import java.util.Locale;

/**
 * Morning briefing orchestrator.
 * Runs at 07:30 AM, gathers deterministic metrics from both domains,
 * passes them to LLM as structured context, pushes result to Telegram.
 * LLM is STRICTLY FORBIDDEN from doing math — it only synthesizes.
 */
@Component
public class BriefingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(BriefingOrchestrator.class);
    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    private static final String SYSTEM_PROMPT = """
            You are a personal health and finance advisor for a Vietnamese professional.
            You receive pre-calculated metrics — DO NOT recalculate any numbers.
            Your job: write a concise, motivating morning briefing (max 200 words) in English.
            
            Structure:
            1. 🏃 Recovery: Interpret readiness level and recommend today's badminton/exercise intensity.
            2. 💤 Sleep: Comment on last night's sleep and any debt.
            3. ⚖️ Weight: Brief comment on EMA trend vs target.
            4. 💰 Finance: Comment on spending pace vs budget.
            5. 🎯 Focus: One actionable priority for today.
            
            Tone: direct, no fluff. Use Vietnamese currency (VND, format with dots).
            DO NOT recalculate or re-derive any numbers — only interpret what's given.
            """;

    private final HealthService healthService;
    private final FinanceService financeService;
    private final ChatClient chatClient;
    private final PloaProperties properties;
    private TelegramClient telegramClient;

    public BriefingOrchestrator(HealthService healthService,
                                 FinanceService financeService,
                                 ChatClient.Builder chatClientBuilder,
                                 PloaProperties properties) {
        this.healthService = healthService;
        this.financeService = financeService;
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
        String token = properties.telegram().botToken();
        if (token != null && !token.isBlank()) {
            this.telegramClient = new OkHttpTelegramClient(token);
        }
    }

    @Scheduled(cron = "${ploa.briefing.cron:0 30 7 * * *}")
    public void sendMorningBriefing() {
        log.info("Morning briefing triggered");
        try {
            LocalDate today = LocalDate.now();
            DailyBriefingContext ctx = new DailyBriefingContext(
                    today,
                    healthService.computeReadiness(today),
                    healthService.computeWeightEma(),
                    financeService.getBurnRate()
            );

            String briefingText = generateBriefing(ctx);
            sendToTelegram(briefingText);
            log.info("Morning briefing sent successfully");
        } catch (Exception e) {
            log.error("Morning briefing failed: {}", e.getMessage(), e);
        }
    }

    public String generateBriefingPreview() {
        LocalDate today = LocalDate.now();
        DailyBriefingContext ctx = new DailyBriefingContext(
                today,
                healthService.computeReadiness(today),
                healthService.computeWeightEma(),
                financeService.getBurnRate()
        );
        return generateBriefing(ctx);
    }

    private String generateBriefing(DailyBriefingContext ctx) {
        String context = buildContextString(ctx);

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(context)
                .call()
                .content();
    }

    private String buildContextString(DailyBriefingContext ctx) {
        var r = ctx.readiness();
        var w = ctx.weightEma();
        var f = ctx.burnRate();

        return """
                === DAILY BRIEFING DATA for %s ===
                
                --- HEALTH & RECOVERY ---
                Readiness Level: %s
                HRV rMSSD today: %s ms  |  30d mean: %s ms  |  Z-score: %s
                RHR today: %s bpm  |  30d mean: %s bpm  |  Z-score: %s
                Sleep last night: %s min total (%s min deep, %s min REM)
                7-day sleep debt: %s min vs target of %.0f min/night
                Restorative ratio (Deep+REM/Total): %s%%
                
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
                ctx.date(),
                r != null ? r.level() : "N/A",
                fmt(r != null ? r.hrvRmssd() : null), fmt(r != null ? r.hrv30dMean() : null), fmt2(r != null ? r.hrvZScore() : null),
                fmt(r != null && r.rhr() != null ? r.rhr().doubleValue() : null),
                fmt2(r != null ? r.rhr30dMean() : null), fmt2(r != null ? r.rhrZScore() : null),
                fmt(r != null && r.sleepTotalMin() != null ? r.sleepTotalMin().doubleValue() : null),
                fmt(r != null && r.sleepDeepMin() != null ? r.sleepDeepMin().doubleValue() : null),
                fmt(r != null && r.sleepRemMin() != null ? r.sleepRemMin().doubleValue() : null),
                r != null ? r.sleepDebtMin() : "N/A",
                properties.health().sleepTargetHours() * 60,
                fmt2(r != null ? r.restorativeRatioPct() : null),
                w != null ? w.latestWeightKg() : "N/A",
                w != null ? w.ema7Kg() : "N/A",
                w != null ? w.targetWeightKg() : properties.health().targetWeightKg(),
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
    }

    private void sendToTelegram(String text) {
        if (telegramClient == null) {
            String token = properties.telegram().botToken();
            if (token != null && !token.isBlank()) {
                telegramClient = new OkHttpTelegramClient(token);
            } else {
                log.warn("Telegram bot token not configured, skipping push briefing.");
                return;
            }
        }
        properties.telegram().allowedChatIdList().forEach(chatId -> {
            try {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(text)
                        .parseMode("Markdown")
                        .build());
            } catch (Exception e) {
                log.error("Failed to send briefing to chat {}: {}", chatId, e.getMessage());
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
