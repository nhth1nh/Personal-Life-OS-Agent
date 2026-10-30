package com.ploa.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Structured output from Vision OCR extraction.
 * Produced by LLM (Gemini/Qwen2.5-VL) from receipt/transfer images.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrResult(
        @JsonProperty("document_type") @JsonAlias({"documentType", "type"}) String documentType,
        @JsonProperty("source_platform") String sourcePlatform,
        @JsonProperty("invoice_number") @JsonAlias({"invoiceNumber", "order_id", "reference_code", "ref_no", "bill_id"}) String invoiceNumber,
        @JsonProperty("transaction_date") @JsonAlias({"date", "transactionDate", "tx_date"}) LocalDate transactionDate,
        @JsonProperty("transaction_time") @JsonAlias({"transactionTime", "time"}) String transactionTime,
        @JsonProperty("total_amount") @JsonAlias({"amount", "totalAmount", "total"}) Long totalAmount,
        @JsonProperty("currency") String currency,
        @JsonProperty("category") String category,
        @JsonProperty("spend_type") @JsonAlias({"spendType"}) String spendType,
        @JsonProperty("merchant") String merchant,
        @JsonProperty("note") String note,
        @JsonProperty("confidence") @JsonAlias({"confidence_score"}) Double confidence,
        @JsonProperty("line_items") List<LineItem> lineItems
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineItem(
            @JsonProperty("name") String name,
            @JsonProperty("quantity") Integer quantity,
            @JsonProperty("unit_price") @JsonAlias({"unitPrice", "price"}) Long unitPrice,
            @JsonProperty("total") @JsonAlias({"total_price", "totalPrice", "amount"}) Long total
    ) {}
}
