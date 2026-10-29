package com.ploa.finance.repository;

import com.ploa.finance.model.Debt;
import com.ploa.finance.model.DebtStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface DebtRepository extends JpaRepository<Debt, UUID> {

    List<Debt> findByStatusOrderByMaturityDateAsc(DebtStatus status);

    @Query("SELECT COALESCE(SUM(d.currentPrincipal), 0) FROM Debt d WHERE d.status = 'ACTIVE'")
    BigDecimal sumTotalActivePrincipal();

    @Query("SELECT COALESCE(SUM(d.monthlyMinimumDue), 0) FROM Debt d WHERE d.status = 'ACTIVE'")
    BigDecimal sumTotalMonthlyMinimumDue();
}
