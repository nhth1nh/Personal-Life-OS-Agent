package com.ploa.ai;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Provides real-time temporal awareness (timestamp, date, time of day, day of week, timezone)
 * across all AI model prompts and orchestrators.
 */
@Component
public class TemporalContext {

    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ZoneId zoneId;

    public TemporalContext() {
        this.zoneId = DEFAULT_ZONE_ID;
    }

    public TemporalContext(ZoneId zoneId) {
        this.zoneId = zoneId != null ? zoneId : DEFAULT_ZONE_ID;
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(zoneId);
    }

    public LocalDate currentDate() {
        return now().toLocalDate();
    }

    public LocalTime currentTime() {
        return now().toLocalTime();
    }

    public String getIsoTimestamp() {
        return now().format(ISO_FORMATTER);
    }

    public String getDayOfWeek() {
        return now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    public String getTimeOfDay() {
        int hour = now().getHour();
        if (hour >= 5 && hour < 12) return "Morning";
        if (hour >= 12 && hour < 14) return "Noon";
        if (hour >= 14 && hour < 18) return "Afternoon";
        if (hour >= 18 && hour < 22) return "Evening";
        return "Night";
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    /**
     * Builds a structured markdown block representing current real-time temporal context
     * suitable for injecting into system/user prompts.
     */
    public String getFormattedPromptContext() {
        ZonedDateTime zdt = now();
        String day = zdt.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String timeStr = zdt.format(TIME_FORMATTER);
        String iso = zdt.format(ISO_FORMATTER);
        String period = getTimeOfDay();

        return """
                === CURRENT TEMPORAL CONTEXT ===
                • Current Timestamp: %s
                • Current Date: %s (%s)
                • Current Time: %s (%s)
                • Timezone: %s
                ===============================""".formatted(
                iso,
                zdt.toLocalDate(),
                day,
                timeStr,
                period,
                zoneId.getId()
        );
    }
}
