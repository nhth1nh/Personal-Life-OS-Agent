package com.ploa.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Structured output from Vision OCR extraction.
 * Produced by LLM (Gemini/Qwen2.5-VL) from receipt/transfer images.
 */
public record OcrResult(
        @JsonProperty("document_type") String documentType,
        @JsonProperty("source_platform") String sourcePlatform,
        @JsonProperty("transaction_date") LocalDate transactionDate,
        @JsonProperty("total_amount") Long totalAmount,
        @JsonProperty("currency") String currency,
        @JsonProperty("category") String category,
        @JsonProperty("spend_type") String spendType,
        @JsonProperty("merchant") String merchant,
        @JsonProperty("note") String note,
        @JsonProperty("line_items") List<LineItem> lineItems
) {
    public record LineItem(
            @JsonProperty("name") String name,
            @JsonProperty("quantity") Integer quantity,
            @JsonProperty("unit_price") Long unitPrice,
            @JsonProperty("total") Long total
    ) {}
}
