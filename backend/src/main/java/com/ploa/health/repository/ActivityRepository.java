package com.ploa.health.repository;

import com.ploa.health.model.Activity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, String> {

    List<Activity> findByDateOrderByStartTimeDesc(LocalDate date);

    List<Activity> findByDateBetweenOrderByStartTimeDesc(LocalDate startDate, LocalDate endDate);

    @Query("SELECT a FROM Activity a ORDER BY a.startTime DESC")
    List<Activity> findRecentActivities(Pageable pageable);

    @Query("SELECT COUNT(a) FROM Activity a WHERE a.date = :date")
    int countByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(a.calories), 0) FROM Activity a WHERE a.date = :date")
    int sumCaloriesByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(a.durationSeconds), 0) FROM Activity a WHERE a.date = :date")
    double sumDurationSecondsByDate(@Param("date") LocalDate date);
}
