package com.ploa.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploa.ai.dto.OcrResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Vision OCR service: sends receipt/transfer images to LLM and extracts structured JSON.
 * Works with both Gemini (cloud) and Qwen2.5-VL (local via Ollama).
 * The LLM's ONLY job here is entity extraction — no math, no inference.
 */
@Service
public class VisionOcrService {

    private static final Logger log = LoggerFactory.getLogger(VisionOcrService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules(); // registers JavaTimeModule for LocalDate

    private static final String OCR_SYSTEM_PROMPT = """
            You are a strict JSON extraction engine. Extract financial transaction data from the provided image.
            
            Return ONLY a valid JSON object with these fields (use null for missing values):
            {
              "document_type": "BANK_TRANSFER" | "RECEIPT" | "E_COMMERCE" | "UNKNOWN",
              "source_platform": "VietQR" | "Shopee" | "manual" | null,
              "transaction_date": "YYYY-MM-DD" | null,
              "total_amount": <integer in VND, no decimals> | null,
              "currency": "VND",
              "category": "Food" | "Groceries" | "Transport" | "Bills" | "Shopping" | "Sports" | "Other",
              "spend_type": "FIXED" | "DISCRETIONARY",
              "merchant": "<merchant name>" | null,
              "note": "<brief description>" | null,
              "line_items": [] | null
            }
            
            Rules:
            - NEVER invent data. Use null if uncertain.
            - Amounts MUST be integers in VND (multiply by 1000 if shown in thousands).
            - Bills/Rent/Subscriptions = FIXED. Everything else = DISCRETIONARY.
            - Return ONLY the JSON object, no markdown, no explanation.
            """;

    private final ChatClient chatClient;

    public VisionOcrService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Extract structured transaction data from an image URL.
     *
     * @param imageUrl   URL or data URI of the receipt/transfer image
     * @param mimeType   image MIME type (e.g., "image/jpeg")
     * @return parsed OcrResult, or a fallback with nulls on failure
     */
    public OcrResult extractFromImage(String imageUrl, String mimeType) {
        log.info("Running Vision OCR on image: {}", imageUrl);
        try {
            Media media = new Media(MimeType.valueOf(mimeType), URI.create(imageUrl));
            UserMessage userMessage = UserMessage.builder()
                    .text("Extract the financial transaction from this image.")
                    .media(media)
                    .build();

            String raw = chatClient.prompt()
                    .system(OCR_SYSTEM_PROMPT)
                    .messages(userMessage)
                    .call()
                    .content();

            log.debug("OCR raw response: {}", raw);

            // Strip markdown code fences if present
            String json = raw.strip();
            if (json.startsWith("```")) {
                json = json.replaceFirst("```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }

            return MAPPER.readValue(json, OcrResult.class);
        } catch (Exception e) {
            log.error("OCR extraction failed: {}", e.getMessage(), e);
            return new OcrResult("UNKNOWN", null, LocalDate.now(), null,
                    "VND", "Other", "DISCRETIONARY", null, "OCR failed", null);
        }
    }

    /**
     * Parse a quick text expense like "50k coffee" or "120k groceries".
     */
    public OcrResult extractFromText(String text) {
        log.info("Parsing text expense: {}", text);
        String prompt = """
                Parse this expense note into structured JSON.
                Input: "%s"
                
                Return ONLY JSON (same schema as before). For amounts:
                - "50k" = 50000 VND
                - "1.5m" = 1500000 VND
                - "200" = 200 VND (assume literal if no unit)
                """.formatted(text);
        try {
            String raw = chatClient.prompt()
                    .system(OCR_SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();

            String json = raw.strip();
            if (json.startsWith("```")) {
                json = json.replaceFirst("```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }
            OcrResult result = MAPPER.readValue(json, OcrResult.class);
            log.info("Text OCR parsed: amount={} VND, category={}", result.totalAmount(), result.category());
            return result;
        } catch (Exception e) {
            log.error("Text OCR parsing failed: {}", e.getMessage(), e);
            return new OcrResult("UNKNOWN", "manual", LocalDate.now(), null,
                    "VND", "Other", "DISCRETIONARY", null, text, null);
        }
    }
}
