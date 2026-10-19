package com.ploa.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelegramFormatterTest {

    @Test
    void testBoldAndItalicConversion() {
        String input = "Your active calories today stand at **132 kcal** (total: **1,356 kcal**).";
        String html = TelegramFormatter.markdownToHtml(input);
        assertEquals("Your active calories today stand at <b>132 kcal</b> (total: <b>1,356 kcal</b>).", html);
    }

    @Test
    void testPreservesUnderscoresInIdentifiers() {
        String input = "Biometrics: body_battery_current = 5, hrv_rmssd = 45.";
        String html = TelegramFormatter.markdownToHtml(input);
        // Does not turn body_battery_current into italics because underscores are within words
        assertTrue(html.contains("body_battery_current"));
        assertTrue(html.contains("hrv_rmssd"));
        assertFalse(html.contains("<i>battery</i>"));
    }

    @Test
    void testCodeBlocksAndInlineCode() {
        String input = "Check this `curl -X GET /health` command:\n```bash\ndocker ps\n```";
        String html = TelegramFormatter.markdownToHtml(input);
        assertTrue(html.contains("<code>curl -X GET /health</code>"));
        assertTrue(html.contains("<pre>docker ps\n</pre>"));
    }

    @Test
    void testBulletsAndHeaders() {
        String input = "### Report\n* Steps: 1,000\n* HR: 60";
        String html = TelegramFormatter.markdownToHtml(input);
        assertTrue(html.contains("<b>Report</b>"));
        assertTrue(html.contains("• Steps: 1,000"));
        assertTrue(html.contains("• HR: 60"));
    }

    @Test
    void testHtmlEscaping() {
        String input = "Merchant: A&B <Shop> & Co.";
        String html = TelegramFormatter.markdownToHtml(input);
        assertTrue(html.contains("A&amp;B &lt;Shop&gt; &amp; Co."));
    }

    @Test
    void testNullAndEmpty() {
        assertEquals("", TelegramFormatter.markdownToHtml(null));
        assertEquals("", TelegramFormatter.markdownToHtml("   "));
    }
}
