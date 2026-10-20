package com.ploa.bot;

import com.ploa.config.PloaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramBotHandlerTest {

    private TelegramBotHandler handler;

    @BeforeEach
    void setUp() {
        PloaProperties properties = new PloaProperties(
                null,
                null,
                null,
                new PloaProperties.Telegram("", "", ""),
                null,
                null
        );

        handler = new TelegramBotHandler(
                properties,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "How many calories in this pizza?",
            "Is this meal healthy for my diet?",
            "What do you think about this workout?",
            "Could you check this knee swelling for running?",
            "What is shown in this chart?",
            "Can you explain this exercise form?",
            "How does this food fit my daily calories?",
            "Help me understand this workout summary"
    })
    @DisplayName("Captions asking questions or consultation should be detected as image chat intent")
    void testImageChatIntentTrue(String caption) {
        assertThat(handler.isImageChatIntent(caption))
                .as("Caption should be treated as chat intent: %s", caption)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/expense 50k coffee",
            "/pay 120k groceries",
            "/spend 200k utility bill",
            "invoice attached",
            "receipt from grocery store",
            "grocery checkout bill"
    })
    @DisplayName("Captions explicitly indicating receipts/bills should not be treated as chat intent")
    void testImageChatIntentFalse(String caption) {
        assertThat(handler.isImageChatIntent(caption))
                .as("Caption should be treated as financial document: %s", caption)
                .isFalse();
    }

    @Test
    @DisplayName("Null or empty caption should default to document OCR path")
    void testEmptyAndNull() {
        assertThat(handler.isImageChatIntent(null)).isFalse();
        assertThat(handler.isImageChatIntent("")).isFalse();
        assertThat(handler.isImageChatIntent("   ")).isFalse();
    }

    @Test
    @DisplayName("Empty allowed chat ID list should fail closed and reject all chat IDs")
    void testIsAllowedEmptyFailsClosed() {
        PloaProperties emptyProps = new PloaProperties(
                null, null, null,
                new PloaProperties.Telegram("token", "user", ""),
                null, null
        );
        TelegramBotHandler emptyHandler = new TelegramBotHandler(
                emptyProps, null, null, null, null, null, null
        );

        assertThat(emptyHandler.isAllowed(123456789L)).isFalse();
        assertThat(emptyHandler.isAllowed(987654321L)).isFalse();
    }

    @Test
    @DisplayName("Configured allowed chat ID list should permit whitelisted and block unknown IDs")
    void testIsAllowedWithConfiguredWhitelist() {
        PloaProperties props = new PloaProperties(
                null, null, null,
                new PloaProperties.Telegram("token", "user", "123456789,999888777"),
                null, null
        );
        TelegramBotHandler customHandler = new TelegramBotHandler(
                props, null, null, null, null, null, null
        );

        assertThat(customHandler.isAllowed(123456789L)).isTrue();
        assertThat(customHandler.isAllowed(999888777L)).isTrue();
        assertThat(customHandler.isAllowed(555555555L)).isFalse();
    }
}
