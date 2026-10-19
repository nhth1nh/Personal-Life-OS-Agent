package com.ploa.finance.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tx_date", nullable = false)
    private LocalDate txDate;

    @Column(name = "amount", nullable = false)
    private Long amount;  // VND

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "spend_type", nullable = false, length = 15)
    private SpendType spendType;

    @Column(name = "merchant")
    private String merchant;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "source_platform", length = 50)
    private String sourcePlatform;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "items_jsonb", columnDefinition = "jsonb")
    private JsonNode itemsJsonb;

    @Column(name = "telegram_msg_id")
    private Long telegramMsgId;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed = false;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters & setters
    public UUID getId() { return id; }
    public LocalDate getTxDate() { return txDate; }
    public void setTxDate(LocalDate txDate) { this.txDate = txDate; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public SpendType getSpendType() { return spendType; }
    public void setSpendType(SpendType spendType) { this.spendType = spendType; }
    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getSourcePlatform() { return sourcePlatform; }
    public void setSourcePlatform(String sourcePlatform) { this.sourcePlatform = sourcePlatform; }
    public JsonNode getItemsJsonb() { return itemsJsonb; }
    public void setItemsJsonb(JsonNode itemsJsonb) { this.itemsJsonb = itemsJsonb; }
    public Long getTelegramMsgId() { return telegramMsgId; }
    public void setTelegramMsgId(Long telegramMsgId) { this.telegramMsgId = telegramMsgId; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
