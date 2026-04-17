package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.GroupStatus;
import com.solvit.internship_system.entity.ProjectGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectGroupRepository extends JpaRepository<ProjectGroup, Long> {

    Page<ProjectGroup> findByActiveTrue(Pageable pageable);

    Page<ProjectGroup> findByActiveTrueAndSupervisor_Id(Long supervisorId, Pageable pageable);

    @Query("SELECT DISTINCT g FROM ProjectGroup g LEFT JOIN FETCH g.interns WHERE g.id = :id AND g.active = true")
    Optional<ProjectGroup> findByIdAndActiveTrueWithInterns(@Param("id") Long id);

    List<ProjectGroup> findByActiveTrueAndStatusAndEndDateBetween(
            GroupStatus status,
            LocalDate startInclusive,
            LocalDate endInclusive);

    boolean existsByActiveTrueAndStatusInAndInterns_Id(Collection<GroupStatus> statuses, Long internId);

    @Query("SELECT DISTINCT g FROM ProjectGroup g LEFT JOIN FETCH g.interns LEFT JOIN FETCH g.supervisor WHERE g.active = true")
    List<ProjectGroup> findAllActiveWithInternsAndSupervisor();

    @Query("SELECT DISTINCT g FROM ProjectGroup g "
            + "LEFT JOIN FETCH g.supervisor sup "
            + "LEFT JOIN g.interns i "
            + "WHERE g.active = true AND i.id = :internId")
    List<ProjectGroup> findActiveByInternIdWithSupervisor(@Param("internId") Long internId);
}
