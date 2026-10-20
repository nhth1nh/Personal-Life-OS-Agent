package com.ploa.finance.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ocr_audits")
public class OcrAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "image_sha256", length = 64)
    private String imageSha256;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, CONFIRMED_AS_IS, CONFIRMED_MODIFIED, DISCARDED

    @Column(name = "initial_amount")
    private Long initialAmount;

    @Column(name = "final_amount")
    private Long finalAmount;

    @Column(name = "initial_merchant")
    private String initialMerchant;

    @Column(name = "final_merchant")
    private String finalMerchant;

    @Column(name = "initial_category", length = 50)
    private String initialCategory;

    @Column(name = "final_category", length = 50)
    private String finalCategory;

    @Column(name = "initial_spend_type", length = 15)
    private String initialSpendType;

    @Column(name = "final_spend_type", length = 15)
    private String finalSpendType;

    @Column(name = "edit_count", nullable = false)
    private int editCount = 0;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "user_notes", columnDefinition = "TEXT")
    private String userNotes;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "source_platform", length = 50)
    private String sourcePlatform;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "raw_ocr_json", columnDefinition = "jsonb")
    private JsonNode rawOcrJson;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
    public String getImageSha256() { return imageSha256; }
    public void setImageSha256(String imageSha256) { this.imageSha256 = imageSha256; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getInitialAmount() { return initialAmount; }
    public void setInitialAmount(Long initialAmount) { this.initialAmount = initialAmount; }
    public Long getFinalAmount() { return finalAmount; }
    public void setFinalAmount(Long finalAmount) { this.finalAmount = finalAmount; }
    public String getInitialMerchant() { return initialMerchant; }
    public void setInitialMerchant(String initialMerchant) { this.initialMerchant = initialMerchant; }
    public String getFinalMerchant() { return finalMerchant; }
    public void setFinalMerchant(String finalMerchant) { this.finalMerchant = finalMerchant; }
    public String getInitialCategory() { return initialCategory; }
    public void setInitialCategory(String initialCategory) { this.initialCategory = initialCategory; }
    public String getFinalCategory() { return finalCategory; }
    public void setFinalCategory(String finalCategory) { this.finalCategory = finalCategory; }
    public String getInitialSpendType() { return initialSpendType; }
    public void setInitialSpendType(String initialSpendType) { this.initialSpendType = initialSpendType; }
    public String getFinalSpendType() { return finalSpendType; }
    public void setFinalSpendType(String finalSpendType) { this.finalSpendType = finalSpendType; }
    public int getEditCount() { return editCount; }
    public void setEditCount(int editCount) { this.editCount = editCount; }
    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }
    public String getUserNotes() { return userNotes; }
    public void setUserNotes(String userNotes) { this.userNotes = userNotes; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getSourcePlatform() { return sourcePlatform; }
    public void setSourcePlatform(String sourcePlatform) { this.sourcePlatform = sourcePlatform; }
    public JsonNode getRawOcrJson() { return rawOcrJson; }
    public void setRawOcrJson(JsonNode rawOcrJson) { this.rawOcrJson = rawOcrJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
