package com.ploa.finance.repository;

import com.ploa.finance.model.SpendType;
import com.ploa.finance.model.Transaction;
import com.ploa.finance.model.TransactionType;
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
            AND t.confirmed = true
            AND t.transactionType IN (
                com.ploa.finance.model.TransactionType.EXPENSE_FIXED,
                com.ploa.finance.model.TransactionType.EXPENSE_DISCRETIONARY,
                com.ploa.finance.model.TransactionType.DEBT_INTEREST_AND_FEE
            )
            """)
    Long sumOperatingSpendBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.txDate >= :from AND t.txDate <= :to
            AND t.confirmed = true
            AND t.transactionType = com.ploa.finance.model.TransactionType.DEBT_PRINCIPAL_PAYMENT
            """)
    Long sumDebtPrincipalPaidBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.txDate >= :from AND t.txDate <= :to
            AND t.confirmed = true
            AND t.transactionType = :txType
            """)
    Long sumByTransactionTypeBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("txType") TransactionType txType
    );

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

    @Query("""
            SELECT t.category, COALESCE(SUM(t.amount), 0) AS total
            FROM Transaction t
            WHERE t.txDate >= :from AND t.txDate <= :to
            AND t.confirmed = true
            AND t.transactionType IN (
                com.ploa.finance.model.TransactionType.EXPENSE_FIXED,
                com.ploa.finance.model.TransactionType.EXPENSE_DISCRETIONARY,
                com.ploa.finance.model.TransactionType.DEBT_INTEREST_AND_FEE
            )
            GROUP BY t.category
            ORDER BY total DESC
            """)
    List<Object[]> sumOperatingSpendByCategoryBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Transaction> findByTelegramMsgId(Long telegramMsgId);

    java.util.Optional<Transaction> findFirstByTelegramMsgId(Long telegramMsgId);

    List<Transaction> findByConfirmedFalseOrderByCreatedAtDesc();

    java.util.Optional<Transaction> findFirstByImageSha256(String imageSha256);

    List<Transaction> findByInvoiceNumberIgnoreCaseAndMerchantIgnoreCase(String invoiceNumber, String merchant);

    List<Transaction> findByAmountAndTxDateAndMerchantIgnoreCase(Long amount, LocalDate txDate, String merchant);

    List<Transaction> findByAmountAndTxDate(Long amount, LocalDate txDate);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Transaction t SET t.debtId = null WHERE t.debtId = :debtId")
    void clearDebtId(@Param("debtId") UUID debtId);
}
