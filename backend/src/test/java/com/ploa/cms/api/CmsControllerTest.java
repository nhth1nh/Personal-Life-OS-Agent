package com.ploa.cms.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploa.cms.CmsLocalizationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CmsControllerTest {

    private CmsController controller;

    @BeforeEach
    void setUp() {
        CmsLocalizationRegistry registry = new CmsLocalizationRegistry(
                new DefaultResourceLoader(),
                new ObjectMapper(),
                "vi"
        );
        controller = new CmsController(registry);
    }

    @Test
    @DisplayName("Should return Vietnamese translations when requested")
    void testGetVietnameseTranslations() {
        ResponseEntity<CmsController.CmsTranslationsResponse> response = controller.getTranslations("vi", null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().locale()).isEqualTo("vi");
        assertThat(response.getBody().translations()).containsKey("nav.dashboard");
        assertThat(response.getBody().translations().get("nav.dashboard")).isEqualTo("Bảng điều khiển");
    }

    @Test
    @DisplayName("Should return English translations when requested")
    void testGetEnglishTranslations() {
        ResponseEntity<CmsController.CmsTranslationsResponse> response = controller.getTranslations("en", null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().locale()).isEqualTo("en");
        assertThat(response.getBody().translations().get("nav.dashboard")).isEqualTo("Dashboard");
    }

    @Test
    @DisplayName("Should return supported locales")
    void testGetSupportedLocales() {
        var locales = controller.getLocales();
        assertThat(locales.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(locales.getBody()).hasSize(2);
    }
}
