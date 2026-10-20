package com.ploa.finance.repository;

import com.ploa.finance.model.OcrAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OcrAuditRepository extends JpaRepository<OcrAudit, UUID> {

    Optional<OcrAudit> findByTransactionId(UUID transactionId);

    long countByStatus(String status);

    @Query("SELECT AVG(a.confidenceScore) FROM OcrAudit a WHERE a.confidenceScore IS NOT NULL")
    Double getAverageConfidence();

    @Query("SELECT AVG(a.editCount) FROM OcrAudit a WHERE a.status LIKE 'CONFIRMED%'")
    Double getAverageEditsPerConfirmedScan();
}
