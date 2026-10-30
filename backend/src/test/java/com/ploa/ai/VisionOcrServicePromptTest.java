package com.ploa.ai;

import com.ploa.ai.prompt.LocalizedPromptRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class VisionOcrServicePromptTest {

    private LocalizedPromptRegistry promptRegistry;
    private VisionOcrService ocrService;

    @BeforeEach
    void setUp() {
        promptRegistry = new LocalizedPromptRegistry(new DefaultResourceLoader(), "vi");
        ChatClient.Builder mockBuilder = Mockito.mock(ChatClient.Builder.class);
        ChatClient mockChatClient = Mockito.mock(ChatClient.class);
        when(mockBuilder.build()).thenReturn(mockChatClient);

        ocrService = new VisionOcrService(
                mockBuilder,
                null,
                null,
                promptRegistry
        );
    }

    @Test
    @DisplayName("Should resolve Vietnamese localized receipt OCR prompt with entity preservation rules")
    void testVietnameseReceiptOcrPrompt() {
        String prompt = ocrService.getReceiptOcrPrompt();
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("Strict Localization & Entity Preservation Rules");
        assertThat(prompt).contains("Quán ăn, nhà hàng, cà phê");
        assertThat(prompt).contains("WinMart, Co.opmart");
    }

    @Test
    @DisplayName("Should resolve Vietnamese localized debt OCR prompt with banking entity anchors")
    void testVietnameseDebtOcrPrompt() {
        String prompt = ocrService.getDebtOcrPrompt();
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("SmartCredit");
        assertThat(prompt).contains("VPBank");
        assertThat(prompt).contains("Dư nợ chưa thanh toán");
        assertThat(prompt).contains("Số tiền đã giải ngân");
    }

    @Test
    @DisplayName("Should resolve Vietnamese text expense parser prompt")
    void testVietnameseTextExpensePrompt() {
        String prompt = ocrService.getTextExpensePrompt();
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("50k");
        assertThat(prompt).contains("1.5tr");
        assertThat(prompt).contains("Cà phê muối");
    }

    @Test
    @DisplayName("Should fall back cleanly if promptRegistry is null")
    void testFallbackWhenRegistryNull() {
        ChatClient.Builder mockBuilder = Mockito.mock(ChatClient.Builder.class);
        when(mockBuilder.build()).thenReturn(Mockito.mock(ChatClient.class));

        VisionOcrService unconfigured = new VisionOcrService(
                mockBuilder,
                null,
                null
        );
        assertThat(unconfigured.getReceiptOcrPrompt()).isNotBlank();
        assertThat(unconfigured.getDebtOcrPrompt()).isNotBlank();
        assertThat(unconfigured.getTextExpensePrompt()).isNotBlank();
    }
}
