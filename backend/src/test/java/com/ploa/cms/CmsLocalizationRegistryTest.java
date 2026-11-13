package com.ploa.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CmsLocalizationRegistryTest {

    private CmsLocalizationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CmsLocalizationRegistry(
                new DefaultResourceLoader(),
                new ObjectMapper(),
                "vi"
        );
    }

    @Test
    @DisplayName("Should load default English translation bundle")
    void testLoadDefaultTranslations() {
        Map<String, String> strings = registry.getTranslations("en");
        assertThat(strings).isNotEmpty();
        assertThat(strings.get("nav.dashboard")).isEqualTo("Dashboard");
        assertThat(strings.get("nav.health")).isEqualTo("Biometrics");
        assertThat(strings.get("dashboard.morning_briefing")).isEqualTo("Morning Briefing");
    }

    @Test
    @DisplayName("Should load localized Vietnamese translation bundle")
    void testLoadVietnameseTranslations() {
        Map<String, String> strings = registry.getTranslations("vi");
        assertThat(strings).isNotEmpty();
        assertThat(strings.get("nav.dashboard")).isEqualTo("Bảng điều khiển");
        assertThat(strings.get("nav.health")).isEqualTo("Chỉ số sinh học");
        assertThat(strings.get("dashboard.morning_briefing")).isEqualTo("Điểm tin Buổi sáng");
    }

    @Test
    @DisplayName("Should fallback to default English for unconfigured locale")
    void testFallbackForUnconfiguredLocale() {
        Map<String, String> strings = registry.getTranslations("fr");
        assertThat(strings).isNotEmpty();
        assertThat(strings.get("nav.dashboard")).isEqualTo("Dashboard");
    }

    @Test
    @DisplayName("Should return list of supported locales")
    void testGetSupportedLocales() {
        var locales = registry.getSupportedLocales();
        assertThat(locales).hasSize(2);
        assertThat(locales).extracting(CmsLocalizationRegistry.SupportedLocale::code)
                .containsExactlyInAnyOrder("vi", "en");
    }

    @Test
    @DisplayName("Should normalize Accept-Language style locales")
    void testNormalizeAcceptLanguage() {
        Map<String, String> viStrings = registry.getTranslations("vi-VN,vi;q=0.9");
        assertThat(viStrings.get("nav.dashboard")).isEqualTo("Bảng điều khiển");

        Map<String, String> enStrings = registry.getTranslations("en-US,en;q=0.8");
        assertThat(enStrings.get("nav.dashboard")).isEqualTo("Dashboard");
    }
}
