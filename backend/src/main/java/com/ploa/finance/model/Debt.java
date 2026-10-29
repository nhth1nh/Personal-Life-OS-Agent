package com.ploa.finance.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "debts")
public class Debt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "creditor", nullable = false)
    private String creditor;

    @Enumerated(EnumType.STRING)
    @Column(name = "debt_type", nullable = false, length = 30)
    private DebtType debtType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "VND";

    @Column(name = "original_principal", nullable = false, precision = 19, scale = 2)
    private BigDecimal originalPrincipal;

    @Column(name = "current_principal", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentPrincipal;

    @Column(name = "interest_rate_apr", nullable = false, precision = 7, scale = 4)
    private BigDecimal interestRateApr = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_accrual_convention", nullable = false, length = 30)
    private InterestAccrualConvention interestAccrualConvention = InterestAccrualConvention.ACTUAL_365;

    @Column(name = "monthly_minimum_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyMinimumDue = BigDecimal.ZERO;

    @Column(name = "due_day_of_month", nullable = false)
    private int dueDayOfMonth = 1;

    @Column(name = "origination_date", nullable = false)
    private LocalDate originationDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DebtStatus status = DebtStatus.ACTIVE;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCreditor() { return creditor; }
    public void setCreditor(String creditor) { this.creditor = creditor; }

    public DebtType getDebtType() { return debtType; }
    public void setDebtType(DebtType debtType) { this.debtType = debtType; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getOriginalPrincipal() { return originalPrincipal; }
    public void setOriginalPrincipal(BigDecimal originalPrincipal) { this.originalPrincipal = originalPrincipal; }

    public BigDecimal getCurrentPrincipal() { return currentPrincipal; }
    public void setCurrentPrincipal(BigDecimal currentPrincipal) { this.currentPrincipal = currentPrincipal; }

    public BigDecimal getInterestRateApr() { return interestRateApr; }
    public void setInterestRateApr(BigDecimal interestRateApr) { this.interestRateApr = interestRateApr; }

    public InterestAccrualConvention getInterestAccrualConvention() { return interestAccrualConvention; }
    public void setInterestAccrualConvention(InterestAccrualConvention interestAccrualConvention) { this.interestAccrualConvention = interestAccrualConvention; }

    public BigDecimal getMonthlyMinimumDue() { return monthlyMinimumDue; }
    public void setMonthlyMinimumDue(BigDecimal monthlyMinimumDue) { this.monthlyMinimumDue = monthlyMinimumDue; }

    public int getDueDayOfMonth() { return dueDayOfMonth; }
    public void setDueDayOfMonth(int dueDayOfMonth) { this.dueDayOfMonth = dueDayOfMonth; }

    public LocalDate getOriginationDate() { return originationDate; }
    public void setOriginationDate(LocalDate originationDate) { this.originationDate = originationDate; }

    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }

    public DebtStatus getStatus() { return status; }
    public void setStatus(DebtStatus status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
