package com.ploa.health.repository;

import com.ploa.health.model.HealthMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HealthMetricRepository extends JpaRepository<HealthMetric, LocalDate> {

    /**
     * Fetch metrics within the last N days (for baseline computation).
     */
    @Query("""
            SELECT h FROM HealthMetric h
            WHERE h.date >= :from AND h.date <= :to
            ORDER BY h.date DESC
            """)
    List<HealthMetric> findByDateBetweenOrderByDateDesc(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /**
     * Fetch last N entries with non-null hrv for baseline.
     */
    @Query("""
            SELECT h FROM HealthMetric h
            WHERE h.hrvRmssd IS NOT NULL
            AND h.date >= :from
            ORDER BY h.date DESC
            """)
    List<HealthMetric> findHrvMetricsSince(@Param("from") LocalDate from);

    /**
     * Fetch last N entries with non-null RHR for baseline.
     */
    @Query("""
            SELECT h FROM HealthMetric h
            WHERE h.rhr IS NOT NULL
            AND h.date >= :from
            ORDER BY h.date DESC
            """)
    List<HealthMetric> findRhrMetricsSince(@Param("from") LocalDate from);

    /**
     * Fetch last N entries with non-null weight for EMA calculation.
     */
    @Query("""
            SELECT h FROM HealthMetric h
            WHERE h.weightKg IS NOT NULL
            ORDER BY h.date DESC
            LIMIT :limit
            """)
    List<HealthMetric> findLatestWeightEntries(@Param("limit") int limit);
}
