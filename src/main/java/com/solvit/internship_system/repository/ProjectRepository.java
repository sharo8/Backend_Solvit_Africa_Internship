package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("SELECT DISTINCT p FROM Project p "
            + "LEFT JOIN FETCH p.group "
            + "LEFT JOIN FETCH p.supervisor "
            + "LEFT JOIN FETCH p.createdBy "
            + "LEFT JOIN FETCH p.assignedInterns "
            + "WHERE p.active = true")
    List<Project> findByActiveTrue();

    @Query("SELECT DISTINCT p FROM Project p "
            + "LEFT JOIN FETCH p.group g "
            + "LEFT JOIN FETCH g.supervisor "
            + "LEFT JOIN FETCH p.supervisor "
            + "LEFT JOIN FETCH p.createdBy "
            + "LEFT JOIN FETCH p.assignedInterns "
            + "WHERE p.active = true AND (p.endDate IS NULL OR p.endDate >= :today) "
            + "AND (p.supervisor.id = :supervisorId OR (g IS NOT NULL AND g.supervisor.id = :supervisorId) "
            + "OR p.createdBy.id = :supervisorId)")
    List<Project> findActiveForSupervisor(@Param("supervisorId") Long supervisorId, @Param("today") LocalDate today);

    @Query("SELECT DISTINCT p FROM Project p "
            + "LEFT JOIN FETCH p.supervisor "
            + "LEFT JOIN FETCH p.createdBy "
            + "LEFT JOIN FETCH p.assignedInterns "
            + "WHERE p.active = true AND p.endDate = :endDate AND p.status = :status AND p.deadlineWarningSent = false")
    List<Project> findByActiveTrueAndEndDateAndStatusAndDeadlineWarningSentFalse(
            @Param("endDate") LocalDate endDate,
            @Param("status") Project.ProjectStatus status);

    @Query("SELECT p FROM Project p WHERE p.active=true AND (:title IS NULL OR :title='' OR LOWER(p.title) LIKE LOWER(CONCAT('%',:title,'%')))")
    List<Project> searchByTitle(String title);

    @Query("SELECT DISTINCT p FROM Project p "
            + "LEFT JOIN FETCH p.group g "
            + "LEFT JOIN FETCH g.supervisor "
            + "LEFT JOIN FETCH p.supervisor "
            + "LEFT JOIN FETCH p.assignedInterns "
            + "WHERE p.id = :id")
    Optional<Project> findByIdWithGroupContext(@Param("id") Long id);

    @Query("SELECT COUNT(DISTINCT p.id) FROM Project p LEFT JOIN p.assignedInterns i "
            + "WHERE p.active = true AND p.status IN ('ACTIVE','ON_HOLD') AND i.id = :internId")
    long countOngoingProjectsByInternId(@Param("internId") Long internId);

    long countByActiveTrueAndGroup_Id(Long groupId);
}

