package com.ploa.finance.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "debt_payments")
public class DebtPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "total_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPaid;

    @Column(name = "principal_portion", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalPortion;

    @Column(name = "interest_portion", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestPortion = BigDecimal.ZERO;

    @Column(name = "fee_portion", nullable = false, precision = 19, scale = 2)
    private BigDecimal feePortion = BigDecimal.ZERO;

    @Column(name = "remaining_principal_snapshot", nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingPrincipalSnapshot;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Debt getDebt() { return debt; }
    public void setDebt(Debt debt) { this.debt = debt; }

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }

    public BigDecimal getPrincipalPortion() { return principalPortion; }
    public void setPrincipalPortion(BigDecimal principalPortion) { this.principalPortion = principalPortion; }

    public BigDecimal getInterestPortion() { return interestPortion; }
    public void setInterestPortion(BigDecimal interestPortion) { this.interestPortion = interestPortion; }

    public BigDecimal getFeePortion() { return feePortion; }
    public void setFeePortion(BigDecimal feePortion) { this.feePortion = feePortion; }

    public BigDecimal getRemainingPrincipalSnapshot() { return remainingPrincipalSnapshot; }
    public void setRemainingPrincipalSnapshot(BigDecimal remainingPrincipalSnapshot) { this.remainingPrincipalSnapshot = remainingPrincipalSnapshot; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
