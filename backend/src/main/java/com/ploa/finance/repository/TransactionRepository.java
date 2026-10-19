package com.ploa.finance.repository;

import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByTxDateBetweenOrderByTxDateDesc(LocalDate from, LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.txDate >= :from AND t.txDate <= :to
            AND t.confirmed = true
            """)
    Long sumConfirmedAmountBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.txDate >= :from AND t.txDate <= :to
            AND t.spendType = :spendType
            AND t.confirmed = true
            """)
    Long sumBySpendTypeBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("spendType") SpendType spendType
    );

    @Query("""
            SELECT t.category, COALESCE(SUM(t.amount), 0) AS total
            FROM Transaction t
            WHERE t.txDate >= :from AND t.txDate <= :to
            AND t.confirmed = true
            GROUP BY t.category
            ORDER BY total DESC
            """)
    List<Object[]> sumByCategoryBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Transaction> findByTelegramMsgId(Long telegramMsgId);
}
