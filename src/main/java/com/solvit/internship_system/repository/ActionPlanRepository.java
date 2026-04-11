package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.ActionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionPlanRepository extends JpaRepository<ActionPlan, Long> {

    List<ActionPlan> findByIntern_IdOrderByCreatedAtDesc(Long internId);
}
