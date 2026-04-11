package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.ConsecutiveAbsenceWarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface ConsecutiveAbsenceWarningRepository extends JpaRepository<ConsecutiveAbsenceWarning, Long> {

    boolean existsByInternUserIdAndStreakEndDate(Long internUserId, LocalDate streakEndDate);
}
