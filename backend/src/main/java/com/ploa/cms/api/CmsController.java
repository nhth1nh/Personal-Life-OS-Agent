package com.ploa.cms.api;

import com.ploa.cms.CmsLocalizationRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cms")
public class CmsController {

    private final CmsLocalizationRegistry cmsRegistry;

    public CmsController(CmsLocalizationRegistry cmsRegistry) {
        this.cmsRegistry = cmsRegistry;
    }

    public record CmsTranslationsResponse(
            String locale,
            String defaultLocale,
            int keyCount,
            Map<String, String> translations
    ) {}

    /**
     * Get UI localized translation strings for the given locale.
     * Automatically falls back to English/default for missing keys.
     */
    @GetMapping("/i18n")
    public ResponseEntity<CmsTranslationsResponse> getTranslations(
            @RequestParam(value = "locale", required = false) String locale,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage
    ) {
        String targetLocale = (locale != null && !locale.isBlank())
                ? locale
                : acceptLanguage;

        Map<String, String> strings = cmsRegistry.getTranslations(targetLocale);
        String resolvedLocale = (targetLocale != null && !targetLocale.isBlank())
                ? (targetLocale.toLowerCase().startsWith("vi") ? "vi" : "en")
                : cmsRegistry.getDefaultLocale();

        return ResponseEntity.ok(new CmsTranslationsResponse(
                resolvedLocale,
                cmsRegistry.getDefaultLocale(),
                strings.size(),
                strings
        ));
    }

    /**
     * Get the list of all supported locales for UI switching.
     */
    @GetMapping("/locales")
    public ResponseEntity<List<CmsLocalizationRegistry.SupportedLocale>> getLocales() {
        return ResponseEntity.ok(cmsRegistry.getSupportedLocales());
    }

    /**
     * Invalidate and clear the translation cache to reload from disk/resources.
     */
    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, String>> clearCache() {
        cmsRegistry.clearCache();
        return ResponseEntity.ok(Map.of("status", "cache_cleared"));
    }
}
