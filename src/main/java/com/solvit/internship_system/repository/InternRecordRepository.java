package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.InternRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InternRecordRepository extends JpaRepository<InternRecord, Long> {
    Optional<InternRecord> findByUser_Id(Long userId);
    List<InternRecord> findBySupervisor_IdAndStatus(Long supervisorId, InternRecord.InternStatus status);
}
