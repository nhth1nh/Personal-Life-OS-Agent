package com.ploa.finance.repository;

import com.ploa.finance.model.DebtPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DebtPaymentRepository extends JpaRepository<DebtPayment, UUID> {

    @Query("SELECT p FROM DebtPayment p WHERE p.debt.id = :debtId ORDER BY p.paymentDate DESC")
    List<DebtPayment> findByDebtIdOrderByPaymentDateDesc(@Param("debtId") UUID debtId);

    List<DebtPayment> findByPaymentDateBetweenOrderByPaymentDateDesc(LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(SUM(p.principalPortion), 0) FROM DebtPayment p WHERE p.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal sumPrincipalPaidBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(p.interestPortion + p.feePortion), 0) FROM DebtPayment p WHERE p.paymentDate BETWEEN :startDate AND :endDate")
    BigDecimal sumInterestAndFeesPaidBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM DebtPayment p WHERE p.debt.id = :debtId")
    void deleteByDebtId(@Param("debtId") UUID debtId);
}
