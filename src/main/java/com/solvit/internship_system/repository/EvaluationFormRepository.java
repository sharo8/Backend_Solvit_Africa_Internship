package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.EvaluationForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationFormRepository extends JpaRepository<EvaluationForm, Long> {
    List<EvaluationForm> findByIntern_IdOrderByCreatedAtDesc(Long internId);
    Optional<EvaluationForm> findFirstByIntern_IdAndFormTypeOrderByCreatedAtDesc(Long internId, EvaluationForm.FormType formType);
}
