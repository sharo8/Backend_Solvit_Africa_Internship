package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.AiAlertRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAlertRecordRepository extends JpaRepository<AiAlertRecord, Long> {
    List<AiAlertRecord> findByIntern_IdOrderByCreatedAtDesc(Long internId);
    List<AiAlertRecord> findBySupervisor_IdAndResolvedFalseOrderByCreatedAtDesc(Long supervisorId);
}
