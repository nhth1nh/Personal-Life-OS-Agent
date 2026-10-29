package com.ploa.finance.repository;

import com.ploa.finance.model.AccountType;
import com.ploa.finance.model.FinancialAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    List<FinancialAccount> findByIsActiveTrue();

    List<FinancialAccount> findByAccountTypeAndIsActiveTrue(AccountType accountType);

    @Query("SELECT COALESCE(SUM(a.currentBalance), 0) FROM FinancialAccount a WHERE a.isActive = true AND a.accountType != 'CREDIT_CARD'")
    BigDecimal sumLiquidCashBalances();
}
