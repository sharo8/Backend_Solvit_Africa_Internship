package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.AttendanceLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceLocationRepository extends JpaRepository<AttendanceLocation, Long> {

    List<AttendanceLocation> findByActiveTrueOrderByNameAsc();
}
