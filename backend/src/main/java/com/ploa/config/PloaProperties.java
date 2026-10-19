package com.ploa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "ploa")
public record PloaProperties(
        Security security,
        Health health,
        Finance finance,
        Telegram telegram,
        Briefing briefing,
        Ai ai
) {
    public record Security(String apiKey) {}

    public record Health(
            @DefaultValue("68.0") double targetWeightKg,
            @DefaultValue("30") int baselineWindowDays,
            @DefaultValue("7.5") double sleepTargetHours
    ) {}

    public record Finance(
            @DefaultValue("10000000") long monthlyBudgetVnd
    ) {}

    public record Telegram(
            String botToken,
            String botUsername,
            String allowedChatIds
    ) {
        public List<Long> allowedChatIdList() {
            if (allowedChatIds == null || allowedChatIds.isBlank()) return List.of();
            return List.of(allowedChatIds.split(","))
                    .stream()
                    .map(String::trim)
                    .map(Long::parseLong)
                    .toList();
        }
    }

    public record Briefing(
            String cron,
            @DefaultValue("0 30 7 * * *") String morningCron,
            @DefaultValue("0 30 21 * * *") String eveningCron
    ) {}

    public record Ai(String provider, String visionModel, String textModel) {}
}
