package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.AttendanceScanLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceScanLogRepository extends JpaRepository<AttendanceScanLog, Long> {
}
