package com.ploa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "ploa")
public record PloaProperties(
        Security security,
        Telegram telegram,
        Briefing briefing,
        Ai ai
) {
    public PloaProperties {
        if (telegram == null) {
            telegram = new Telegram(false, null, null, null);
        }
        if (security == null) {
            security = new Security("changeme-strong-secret");
        }
        if (briefing == null) {
            briefing = new Briefing(null, "Asia/Ho_Chi_Minh", "0 30 7 * * *", "0 30 21 * * *");
        }
    }

    public record Security(String apiKey) {}

    public record Telegram(
            @DefaultValue("true") boolean enabled,
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
            @DefaultValue("Asia/Ho_Chi_Minh") String timezone,
            @DefaultValue("0 30 7 * * *") String morningCron,
            @DefaultValue("0 30 21 * * *") String eveningCron
    ) {}

    public record Ai(String provider, String visionModel, String textModel) {}
}
