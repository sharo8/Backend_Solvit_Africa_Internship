package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.AiPerformanceScoreRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiPerformanceScoreRecordRepository extends JpaRepository<AiPerformanceScoreRecord, Long> {
    List<AiPerformanceScoreRecord> findByIntern_IdOrderByComputedAtDesc(Long internId);
    Optional<AiPerformanceScoreRecord> findFirstByIntern_IdOrderByComputedAtDesc(Long internId);
}
