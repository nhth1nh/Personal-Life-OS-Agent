package com.ploa.ai.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized, multi-locale prompt registry for the Personal Life OS Agent (PLOA).
 * Decouples AI system prompts from Java business logic and enables localized prompts
 * with automatic fallback to standard English/default templates.
 */
@Component
public class LocalizedPromptRegistry {

    private static final Logger log = LoggerFactory.getLogger(LocalizedPromptRegistry.class);

    private final ResourceLoader resourceLoader;
    private final String defaultLocale;
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();

    public LocalizedPromptRegistry(ResourceLoader resourceLoader,
                                   @Value("${ploa.ai.locale:vi}") String defaultLocale) {
        this.resourceLoader = resourceLoader;
        this.defaultLocale = defaultLocale != null && !defaultLocale.isBlank() ? defaultLocale.trim().toLowerCase() : "vi";
    }

    /**
     * Retrieves the prompt template using the application's configured default locale.
     */
    public String getPrompt(String templatePath) {
        return getPrompt(templatePath, this.defaultLocale);
    }

    /**
     * Retrieves the prompt template for a specific locale (e.g. "vi", "en", "ja").
     * Falls back to "default" if the localized template does not exist.
     */
    public String getPrompt(String templatePath, String locale) {
        String targetLocale = (locale != null && !locale.isBlank()) ? locale.trim().toLowerCase() : this.defaultLocale;
        String cacheKey = targetLocale + ":" + templatePath;

        return promptCache.computeIfAbsent(cacheKey, k -> loadPromptResource(templatePath, targetLocale));
    }

    /**
     * Retrieves and renders a prompt template with parameter substitution.
     * Replaces placeholder occurrences of {variableName} with provided values.
     */
    public String render(String templatePath, String locale, Map<String, Object> variables) {
        String template = getPrompt(templatePath, locale);
        if (variables == null || variables.isEmpty()) {
            return template;
        }

        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String val = entry.getValue() != null ? entry.getValue().toString() : "";
            rendered = rendered.replace(placeholder, val);
        }
        return rendered;
    }

    /**
     * Renders using the configured default locale.
     */
    public String render(String templatePath, Map<String, Object> variables) {
        return render(templatePath, this.defaultLocale, variables);
    }

    /**
     * Clears cached prompt templates, allowing runtime reloading during development.
     */
    public void clearCache() {
        promptCache.clear();
        log.info("Prompt cache cleared.");
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    private String loadPromptResource(String templatePath, String locale) {
        String normalizedPath = templatePath.endsWith(".st") ? templatePath : templatePath + ".st";

        // 1. Try localized template: classpath:prompts/{locale}/{templatePath}.st
        String localizedLocation = "classpath:prompts/" + locale + "/" + normalizedPath;
        Resource localizedResource = resourceLoader.getResource(localizedLocation);
        if (localizedResource.exists()) {
            try {
                return readResource(localizedResource);
            } catch (IOException e) {
                log.warn("Failed to read localized prompt at {}: {}", localizedLocation, e.getMessage());
            }
        }

        // 2. Fallback to default template: classpath:prompts/default/{templatePath}.st
        String defaultLocation = "classpath:prompts/default/" + normalizedPath;
        Resource defaultResource = resourceLoader.getResource(defaultLocation);
        if (defaultResource.exists()) {
            try {
                return readResource(defaultResource);
            } catch (IOException e) {
                log.error("Failed to read default prompt at {}: {}", defaultLocation, e.getMessage());
                throw new IllegalStateException("Failed to read prompt: " + defaultLocation, e);
            }
        }

        throw new IllegalArgumentException("Prompt template not found for path '" + templatePath
                + "' in locale '" + locale + "' or 'default'. Checked: " + localizedLocation + ", " + defaultLocation);
    }

    private String readResource(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
