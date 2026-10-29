package com.ploa.finance.repository;

import com.ploa.finance.model.Income;
import com.ploa.finance.model.IncomeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface IncomeRepository extends JpaRepository<Income, UUID> {

    List<Income> findByReceivedDateBetweenOrderByReceivedDateDesc(LocalDate startDate, LocalDate endDate);

    List<Income> findByExpectedDateBetweenAndStatus(LocalDate startDate, LocalDate endDate, IncomeStatus status);

    @Query("SELECT COALESCE(SUM(i.actualAmount), 0) FROM Income i WHERE i.receivedDate BETWEEN :startDate AND :endDate AND i.status = 'RECEIVED'")
    BigDecimal sumReceivedIncomeBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(i.expectedAmount * i.confidenceFactor), 0) FROM Income i WHERE i.expectedDate BETWEEN :startDate AND :endDate AND i.status = 'EXPECTED'")
    BigDecimal sumExpectedInflowsBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
