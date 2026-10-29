package com.ploa.finance.repository;

import com.ploa.finance.model.DebtSchedule;
import com.ploa.finance.model.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface DebtScheduleRepository extends JpaRepository<DebtSchedule, UUID> {

    List<DebtSchedule> findByDebtIdOrderByDueDateAsc(UUID debtId);

    List<DebtSchedule> findByDueDateBetweenAndStatus(LocalDate startDate, LocalDate endDate, ScheduleStatus status);

    @Query("SELECT COALESCE(SUM(s.expectedPayment), 0) FROM DebtSchedule s WHERE s.dueDate BETWEEN :startDate AND :endDate AND s.status = 'UPCOMING'")
    BigDecimal sumUpcomingObligationsBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
