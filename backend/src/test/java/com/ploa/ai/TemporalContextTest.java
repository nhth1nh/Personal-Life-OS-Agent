package com.ploa.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalContextTest {

    @Test
    @DisplayName("Should generate valid temporal context with date, time, and timezone")
    void testTemporalContextGeneration() {
        TemporalContext ctx = new TemporalContext(ZoneId.of("Asia/Ho_Chi_Minh"));

        ZonedDateTime now = ctx.now();
        assertThat(now).isNotNull();
        assertThat(ctx.currentDate()).isEqualTo(now.toLocalDate());
        assertThat(ctx.currentTime()).isNotNull();
        assertThat(ctx.getDayOfWeek()).isNotEmpty();
        assertThat(ctx.getTimeOfDay()).isIn("Morning", "Noon", "Afternoon", "Evening", "Night");
        assertThat(ctx.getIsoTimestamp()).contains("+07:00");

        String promptContext = ctx.getFormattedPromptContext();
        assertThat(promptContext)
                .contains("=== CURRENT TEMPORAL CONTEXT ===")
                .contains("Current Timestamp:")
                .contains("Current Date:")
                .contains("Current Time:")
                .contains("Timezone: Asia/Ho_Chi_Minh");
    }
}
