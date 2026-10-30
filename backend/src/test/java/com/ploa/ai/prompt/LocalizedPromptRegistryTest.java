package com.ploa.ai.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalizedPromptRegistryTest {

    private LocalizedPromptRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new LocalizedPromptRegistry(new DefaultResourceLoader(), "vi");
    }

    @Test
    @DisplayName("Should load localized Vietnamese receipt OCR prompt when requested")
    void testLoadVietnamesePrompt() {
        String prompt = registry.getPrompt("ocr/receipt-ocr", "vi");
        assertThat(prompt).isNotEmpty();
        assertThat(prompt).contains("Highlands Coffee");
        assertThat(prompt).contains("Bách Hóa Xanh");
    }

    @Test
    @DisplayName("Should fallback to default prompt when localized template is not present")
    void testFallbackToDefaultPrompt() {
        // morning-briefing currently exists in default, not in ja
        String prompt = registry.getPrompt("briefing/morning-briefing", "ja");
        assertThat(prompt).isNotEmpty();
        assertThat(prompt).contains("Recovery: Interpret readiness level");
    }

    @Test
    @DisplayName("Should use default locale 'vi' when locale is omitted")
    void testUseDefaultLocale() {
        String prompt = registry.getPrompt("ocr/debt-ocr");
        assertThat(prompt).isNotEmpty();
        assertThat(prompt).contains("SmartCredit");
        assertThat(prompt).contains("Vay tiền mặt trung hạn");
    }

    @Test
    @DisplayName("Should substitute variables correctly with render")
    void testRenderVariables() {
        String rendered = registry.render("briefing/morning-briefing", "default", Map.of(
                "non_existent_key", "value"
        ));
        assertThat(rendered).contains("write a concise, motivating morning briefing");
    }

    @Test
    @DisplayName("Should throw clear exception when template path does not exist in any locale")
    void testNonExistentTemplate() {
        assertThatThrownBy(() -> registry.getPrompt("non/existent/template"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt template not found");
    }
}
