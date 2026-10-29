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

    @Column(name = "account_id", nullable = false)
    private UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Column(name = "debt_id")
    private UUID debtId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType = TransactionType.EXPENSE_DISCRETIONARY;

    @Column(name = "tx_date", nullable = false)
    private LocalDate txDate;

    @Column(name = "tx_timestamp")
    private OffsetDateTime txTimestamp;

    @Column(name = "image_sha256", length = 64)
    private String imageSha256;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "duplicate_warning")
    private String duplicateWarning;

    @Column(name = "amount", nullable = false)
    private Long amount;  // VND

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "spend_type", length = 15)
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

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed = true;

    @Column(name = "telegram_msg_id")
    private Long telegramMsgId;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (txDate == null) {
            txDate = LocalDate.now();
        }
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getDebtId() { return debtId; }
    public void setDebtId(UUID debtId) { this.debtId = debtId; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }

    public LocalDate getTxDate() { return txDate; }
    public void setTxDate(LocalDate txDate) { this.txDate = txDate; }

    public OffsetDateTime getTxTimestamp() { return txTimestamp; }
    public void setTxTimestamp(OffsetDateTime txTimestamp) { this.txTimestamp = txTimestamp; }

    public String getImageSha256() { return imageSha256; }
    public void setImageSha256(String imageSha256) { this.imageSha256 = imageSha256; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getDuplicateWarning() { return duplicateWarning; }
    public void setDuplicateWarning(String duplicateWarning) { this.duplicateWarning = duplicateWarning; }

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

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public Long getTelegramMsgId() { return telegramMsgId; }
    public void setTelegramMsgId(Long telegramMsgId) { this.telegramMsgId = telegramMsgId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
