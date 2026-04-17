package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.SelfReflection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SelfReflectionRepository extends JpaRepository<SelfReflection, Long> {
    List<SelfReflection> findByIntern_IdOrderByWeekNumberDesc(Long internId);
    Optional<SelfReflection> findByIntern_IdAndWeekNumber(Long internId, Integer weekNumber);
}
