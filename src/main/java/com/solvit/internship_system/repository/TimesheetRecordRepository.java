package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.TimesheetRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TimesheetRecordRepository extends JpaRepository<TimesheetRecord, Long> {
    List<TimesheetRecord> findByIntern_IdOrderByWeekNumberDesc(Long internId);
    Optional<TimesheetRecord> findByIntern_IdAndWeekNumber(Long internId, Integer weekNumber);
}
