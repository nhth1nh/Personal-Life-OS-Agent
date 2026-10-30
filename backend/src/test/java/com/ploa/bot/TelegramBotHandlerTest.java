package com.ploa.bot;

import com.ploa.config.PloaProperties;
import com.ploa.profile.UserProfileService;
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
                new PloaProperties.Telegram(true, "", "", ""),
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
                null,
                new PloaProperties.Telegram(true, "token", "user", ""),
                null,
                null
        );
        TelegramBotHandler emptyHandler = new TelegramBotHandler(
                emptyProps, null, null, null, null, null, null, null
        );

        assertThat(emptyHandler.isAllowed(123456789L)).isFalse();
        assertThat(emptyHandler.isAllowed(987654321L)).isFalse();
    }

    @Test
    @DisplayName("Configured allowed chat ID list should permit whitelisted and block unknown IDs")
    void testIsAllowedWithConfiguredWhitelist() {
        PloaProperties props = new PloaProperties(
                null,
                new PloaProperties.Telegram(true, "token", "user", "123456789,999888777"),
                null,
                null
        );
        TelegramBotHandler customHandler = new TelegramBotHandler(
                props, null, null, null, null, null, null, null
        );

        assertThat(customHandler.isAllowed(123456789L)).isTrue();
        assertThat(customHandler.isAllowed(999888777L)).isTrue();
        assertThat(customHandler.isAllowed(555555555L)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/expense 50k coffee",
            "/pay 120k groceries",
            "/spend 200k utility bill",
            "invoice attached",
            "receipt from grocery store",
            "grocery checkout bill",
            "hóa đơn tiền điện",
            "phiếu thu tiền phòng",
            "chuyển khoản VietQR"
    })
    @DisplayName("Captions explicitly indicating receipts/bills should be detected as expense intent")
    void testImageExpenseIntentTrue(String caption) {
        assertThat(handler.isImageExpenseIntent(caption))
                .as("Caption should be treated as expense intent: %s", caption)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "How many calories in this pizza?",
            "Is this meal healthy for my diet?",
            "What do you think about this workout?",
            "Look at this puppy",
            "Nice sunset today"
    })
    @DisplayName("Captions asking questions or general life comments should NOT be detected as expense intent")
    void testImageExpenseIntentFalse(String caption) {
        assertThat(handler.isImageExpenseIntent(caption))
                .as("Caption should NOT be treated as expense intent: %s", caption)
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/debt home loan statement",
            "/loan VPBank 50tr",
            "sao kê tài khoản tín dụng Techcombank",
            "hợp đồng vay FE Credit",
            "phiếu thanh toán trả góp",
            "dư nợ thẻ tín dụng kỳ này"
    })
    @DisplayName("Captions explicitly indicating debt or bank statements should be detected as debt intent")
    void testImageDebtIntentTrue(String caption) {
        assertThat(handler.isImageDebtIntent(caption))
                .as("Caption should be treated as debt intent: %s", caption)
                .isTrue();
    }

    @Test
    @DisplayName("buildImageIntentMessage should generate 4 interactive buttons with correct callback data")
    void testBuildImageIntentMessage() {
        BotMessageFactory factory = new BotMessageFactory();
        var msg = factory.buildImageIntentMessage(12345L, "test-uuid-123");

        assertThat(msg.getText()).contains("What would you like to do with this image?");
        var markup = (org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup) msg.getReplyMarkup();
        assertThat(markup).isNotNull();
        var rows = markup.getKeyboard();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get(0).getCallbackData()).isEqualTo("IMG_RECEIPT:test-uuid-123");
        assertThat(rows.get(0).get(1).getCallbackData()).isEqualTo("IMG_DEBT:test-uuid-123");
        assertThat(rows.get(1).get(0).getCallbackData()).isEqualTo("IMG_CHAT:test-uuid-123");
        assertThat(rows.get(1).get(1).getCallbackData()).isEqualTo("IMG_CANCEL:test-uuid-123");
    }

    @Test
    @DisplayName("cleanupExpiredPendingImages should evict entries older than 30 minutes")
    void testCleanupExpiredPendingImages() {
        String oldId = "old-pending-id";
        String freshId = "fresh-pending-id";

        handler.pendingImages.put(oldId, new TelegramBotHandler.PendingImage(
                new byte[0], "image/jpeg", 1L, null,
                java.time.Instant.now().minus(35, java.time.temporal.ChronoUnit.MINUTES)
        ));
        handler.pendingImages.put(freshId, new TelegramBotHandler.PendingImage(
                new byte[0], "image/jpeg", 2L, null,
                java.time.Instant.now().minus(5, java.time.temporal.ChronoUnit.MINUTES)
        ));

        handler.cleanupExpiredPendingImages();

        assertThat(handler.pendingImages).containsKey(freshId);
        assertThat(handler.pendingImages).doesNotContainKey(oldId);
    }

    @Test
    @DisplayName("Should include /init command in help text")
    void testHelpTextIncludesInit() {
        BotMessageFactory factory = new BotMessageFactory();
        String help = factory.helpText();
        assertThat(help).contains("/init");
    }

    @Test
    @DisplayName("Should format profile summary and update success correctly")
    void testBuildProfileSummaryAndSuccess() {
        BotMessageFactory factory = new BotMessageFactory();
        var profile = new UserProfileService.UserProfileData(65.0, 8.0, 30, 15000000L, "Alex");

        String summary = factory.buildProfileSummary(profile);
        assertThat(summary).contains("15.000.000 VND");
        assertThat(summary).contains("65.0 kg");
        assertThat(summary).contains("8.0 hours/night");
        assertThat(summary).contains("Alex");

        String success = factory.buildProfileUpdateSuccess(profile);
        assertThat(success).contains("PLOA Profile & Base Targets Updated!");
        assertThat(success).contains("15.000.000 VND");
    }
}
