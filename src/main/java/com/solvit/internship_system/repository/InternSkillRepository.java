package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.InternSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InternSkillRepository extends JpaRepository<InternSkill, Long> {
    List<InternSkill> findByIntern_IdOrderByAssessedAtDesc(Long internId);
}
