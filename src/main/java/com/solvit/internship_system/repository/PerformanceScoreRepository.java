package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.PerformanceScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PerformanceScoreRepository extends JpaRepository<PerformanceScore, Long> {

    List<PerformanceScore> findByIntern_IdOrderByCreatedAtDesc(Long internId, org.springframework.data.domain.Pageable pageable);

    List<PerformanceScore> findByAtRiskTrue();

    java.util.Optional<PerformanceScore> findByIntern_IdAndPeriodTypeAndPeriodValue(Long internId, String periodType, String periodValue);
}
