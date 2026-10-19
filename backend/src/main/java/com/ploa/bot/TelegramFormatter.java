package com.ploa.bot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility to convert standard Markdown (commonly output by LLMs) into safe Telegram HTML.
 * Prevents Telegram parse errors ([400] Bad Request: can't parse entities) caused by
 * unclosed markdown tokens, stray underscores in identifiers, or unsupported markdown tags.
 */
public final class TelegramFormatter {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(?:[a-zA-Z0-9_+-]*\\n)?([\\s\\S]*?)```");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern HEADER_PATTERN = Pattern.compile("(?m)^#{1,6}\\s+(.+)$");
    private static final Pattern BULLET_PATTERN = Pattern.compile("(?m)^[\\*\\-]\\s+(.+)$");
    private static final Pattern ITALIC_UNDERSCORE_PATTERN = Pattern.compile("(?<=\\s|^)_([^_\\n]+)_(?=\\s|$|[.,!?:;])");
    private static final Pattern ITALIC_ASTERISK_PATTERN = Pattern.compile("(?<=\\s|^)\\*([^*\\n]+)\\*(?=\\s|$|[.,!?:;])");

    private TelegramFormatter() {
    }

    public static String markdownToHtml(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // 1. Basic HTML escape so user/model content doesn't break HTML tags
        String text = input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        // 2. Multi-line code blocks
        text = CODE_BLOCK_PATTERN.matcher(text).replaceAll("<pre>$1</pre>");

        // 3. Inline code
        text = INLINE_CODE_PATTERN.matcher(text).replaceAll("<code>$1</code>");

        // 4. Headers (### Heading -> <b>Heading</b>)
        text = HEADER_PATTERN.matcher(text).replaceAll("<b>$1</b>");

        // 5. Bold (**bold**)
        text = BOLD_PATTERN.matcher(text).replaceAll("<b>$1</b>");

        // 6. Bullet lists (* item or - item -> • item)
        text = BULLET_PATTERN.matcher(text).replaceAll("• $1");

        // 7. Italics (_italic_ or *italic* with word boundaries)
        text = ITALIC_UNDERSCORE_PATTERN.matcher(text).replaceAll("<i>$1</i>");
        text = ITALIC_ASTERISK_PATTERN.matcher(text).replaceAll("<i>$1</i>");

        return text;
    }
}
