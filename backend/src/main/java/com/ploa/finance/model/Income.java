package com.ploa.finance.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "incomes")
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", precision = 19, scale = 2)
    private BigDecimal actualAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_type", nullable = false, length = 30)
    private IncomeType incomeType;

    @Column(name = "confidence_factor", nullable = false)
    private Double confidenceFactor = 1.0;

    @Column(name = "is_recurring", nullable = false)
    private boolean isRecurring = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", length = 20)
    private IncomeFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IncomeStatus status = IncomeStatus.EXPECTED;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private FinancialAccount account;

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

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public BigDecimal getExpectedAmount() { return expectedAmount; }
    public void setExpectedAmount(BigDecimal expectedAmount) { this.expectedAmount = expectedAmount; }

    public BigDecimal getActualAmount() { return actualAmount; }
    public void setActualAmount(BigDecimal actualAmount) { this.actualAmount = actualAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getExpectedDate() { return expectedDate; }
    public void setExpectedDate(LocalDate expectedDate) { this.expectedDate = expectedDate; }

    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }

    public IncomeType getIncomeType() { return incomeType; }
    public void setIncomeType(IncomeType incomeType) { this.incomeType = incomeType; }

    public Double getConfidenceFactor() { return confidenceFactor; }
    public void setConfidenceFactor(Double confidenceFactor) { this.confidenceFactor = confidenceFactor; }

    public boolean isRecurring() { return isRecurring; }
    public void setRecurring(boolean recurring) { isRecurring = recurring; }

    public IncomeFrequency getFrequency() { return frequency; }
    public void setFrequency(IncomeFrequency frequency) { this.frequency = frequency; }

    public IncomeStatus getStatus() { return status; }
    public void setStatus(IncomeStatus status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public FinancialAccount getAccount() { return account; }
    public void setAccount(FinancialAccount account) { this.account = account; }
    public UUID getAccountId() { return account != null ? account.getId() : null; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
