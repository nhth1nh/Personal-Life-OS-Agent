package com.ploa.cms;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized multi-language CMS registry for UI translations.
 * Mirrors the LocalizedPromptRegistry design: loads JSON resource bundles
 * from classpath with caching and hierarchical fallback to default language.
 */
@Component
public class CmsLocalizationRegistry {

    private static final Logger log = LoggerFactory.getLogger(CmsLocalizationRegistry.class);
    private static final String DEFAULT_LOCALE = "default";

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String configuredDefaultLocale;
    private final Map<String, Map<String, String>> translationCache = new ConcurrentHashMap<>();

    public record SupportedLocale(String code, String name, String flag) {}

    private static final List<SupportedLocale> SUPPORTED_LOCALES = List.of(
            new SupportedLocale("vi", "Tiếng Việt", "🇻🇳"),
            new SupportedLocale("en", "English", "🇬🇧")
    );

    public CmsLocalizationRegistry(ResourceLoader resourceLoader,
                                   ObjectMapper objectMapper,
                                   @Value("${ploa.ui.default-locale:vi}") String configuredDefaultLocale) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.configuredDefaultLocale = (configuredDefaultLocale != null && !configuredDefaultLocale.isBlank())
                ? configuredDefaultLocale.trim().toLowerCase()
                : "vi";
    }

    /**
     * Get all supported locales for CMS and UI switching.
     */
    public List<SupportedLocale> getSupportedLocales() {
        return SUPPORTED_LOCALES;
    }

    /**
     * Get the default locale configured for the instance.
     */
    public String getDefaultLocale() {
        return this.configuredDefaultLocale;
    }

    /**
     * Retrieve all localized strings for the given locale.
     * Automatically merges with default strings to ensure zero missing keys.
     */
    public Map<String, String> getTranslations(String locale) {
        String targetLocale = normalizeLocale(locale);
        return translationCache.computeIfAbsent(targetLocale, this::loadAndMergeTranslations);
    }

    /**
     * Invalidate the cache to reload translations from resources (useful for testing and reload).
     */
    public void clearCache() {
        translationCache.clear();
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return this.configuredDefaultLocale;
        }
        String cleaned = locale.trim().toLowerCase();
        if (cleaned.startsWith("vi")) {
            return "vi";
        }
        if (cleaned.startsWith("en")) {
            return "en";
        }
        return cleaned;
    }

    private Map<String, String> loadAndMergeTranslations(String locale) {
        // 1. Load default baseline bundle
        Map<String, String> baseline = loadBundle(DEFAULT_LOCALE);
        if (baseline.isEmpty()) {
            baseline = loadBundle("en");
        }

        // 2. If requesting default or en and baseline was loaded, return it
        if ("default".equals(locale) || "en".equals(locale)) {
            return Collections.unmodifiableMap(baseline);
        }

        // 3. Load target locale bundle
        Map<String, String> localized = loadBundle(locale);

        // 4. Merge: baseline overridden by localized
        Map<String, String> merged = new LinkedHashMap<>(baseline);
        merged.putAll(localized);
        log.info("Loaded {} CMS UI translation keys for locale '{}' ({} localized overrides)",
                merged.size(), locale, localized.size());

        return Collections.unmodifiableMap(merged);
    }

    private Map<String, String> loadBundle(String locale) {
        String resourcePath = "classpath:i18n/ui_" + locale + ".json";
        Resource resource = resourceLoader.getResource(resourcePath);

        if (!resource.exists()) {
            log.debug("No CMS translation bundle found at {}", resourcePath);
            return Collections.emptyMap();
        }

        try (InputStream is = resource.getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            log.warn("Failed to load CMS translation bundle from {}: {}", resourcePath, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
