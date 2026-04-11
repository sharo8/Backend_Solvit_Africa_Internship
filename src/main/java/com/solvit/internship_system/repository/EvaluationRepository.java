package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.Evaluation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    @Query("SELECT e FROM Evaluation e "
            + "LEFT JOIN FETCH e.intern "
            + "LEFT JOIN FETCH e.evaluator "
            + "LEFT JOIN FETCH e.group g "
            + "LEFT JOIN FETCH g.supervisor "
            + "WHERE e.id = :id AND e.active = true")
    Optional<Evaluation> findByIdWithDetails(@Param("id") Long id);

    boolean existsByIntern_IdAndTypeAndGroup_IdAndActiveTrue(
            Long internId,
            Evaluation.EvaluationType type,
            Long groupId);

    boolean existsByIntern_IdAndTypeAndGroupIsNullAndActiveTrue(
            Long internId,
            Evaluation.EvaluationType type);

    @Query("SELECT e FROM Evaluation e WHERE e.active = true "
            + "AND (:internId IS NULL OR e.intern.id = :internId) "
            + "AND (:groupId IS NULL OR (e.group IS NOT NULL AND e.group.id = :groupId)) "
            + "AND (:type IS NULL OR e.type = :type) "
            + "AND (:status IS NULL OR e.status = :status) "
            + "AND (:supervisorScopeId IS NULL OR e.evaluator.id = :supervisorScopeId "
            + "     OR (e.group IS NOT NULL AND e.group.supervisor.id = :supervisorScopeId))")
    Page<Evaluation> searchFiltered(
            @Param("internId") Long internId,
            @Param("groupId") Long groupId,
            @Param("type") Evaluation.EvaluationType type,
            @Param("status") Evaluation.EvaluationStatus status,
            @Param("supervisorScopeId") Long supervisorScopeId,
            Pageable pageable);

    Page<Evaluation> findByIntern_IdAndStatusInAndActiveTrue(
            Long internId,
            List<Evaluation.EvaluationStatus> statuses,
            Pageable pageable);

    List<Evaluation> findByIntern_IdAndActiveTrue(Long internId);
}
