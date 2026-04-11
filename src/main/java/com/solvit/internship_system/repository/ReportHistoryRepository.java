package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.ReportHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportHistoryRepository extends JpaRepository<ReportHistory, Long> {

    Page<ReportHistory> findByActiveTrueOrderByGeneratedAtDesc(Pageable pageable);

    Page<ReportHistory> findByGeneratedByAndActiveTrueOrderByGeneratedAtDesc(Long generatedBy, Pageable pageable);

    long countByActiveTrueAndGeneratedAtGreaterThanEqualAndGeneratedAtLessThan(
            java.time.Instant startInclusive,
            java.time.Instant endExclusive);
}
