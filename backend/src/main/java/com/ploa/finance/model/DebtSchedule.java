package com.ploa.finance.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "debt_schedules")
public class DebtSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id", nullable = false)
    private Debt debt;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "expected_payment", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedPayment;

    @Column(name = "expected_principal", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedPrincipal;

    @Column(name = "expected_interest", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedInterest;

    @Column(name = "remaining_principal_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingPrincipalAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduleStatus status = ScheduleStatus.UPCOMING;

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

    public int getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(int installmentNumber) { this.installmentNumber = installmentNumber; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getExpectedPayment() { return expectedPayment; }
    public void setExpectedPayment(BigDecimal expectedPayment) { this.expectedPayment = expectedPayment; }

    public BigDecimal getExpectedPrincipal() { return expectedPrincipal; }
    public void setExpectedPrincipal(BigDecimal expectedPrincipal) { this.expectedPrincipal = expectedPrincipal; }

    public BigDecimal getExpectedInterest() { return expectedInterest; }
    public void setExpectedInterest(BigDecimal expectedInterest) { this.expectedInterest = expectedInterest; }

    public BigDecimal getRemainingPrincipalAfter() { return remainingPrincipalAfter; }
    public void setRemainingPrincipalAfter(BigDecimal remainingPrincipalAfter) { this.remainingPrincipalAfter = remainingPrincipalAfter; }

    public ScheduleStatus getStatus() { return status; }
    public void setStatus(ScheduleStatus status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
}
