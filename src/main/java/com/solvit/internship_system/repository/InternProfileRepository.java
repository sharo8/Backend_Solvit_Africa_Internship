package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.InternProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InternProfileRepository extends JpaRepository<InternProfile, Long> {

    Optional<InternProfile> findByUser_Id(Long userId);

    /** Loads user eagerly to avoid lazy init when serializing to JSON. */
    @EntityGraph(attributePaths = {"user"})
    Optional<InternProfile> findDetailByUser_Id(Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<InternProfile> findBySupervisorUserId(Long supervisorUserId);

    @EntityGraph(attributePaths = {"user"})
    @Query("select p from InternProfile p join fetch p.user u where u.id in :userIds")
    List<InternProfile> findAllByUserIds(@Param("userIds") List<Long> userIds);

    List<InternProfile> findByInstitution(String institution);

    @EntityGraph(attributePaths = {"user"})
    List<InternProfile> findByInternshipEndDate(LocalDate endDate);

    /** Internship period not finished (end ≥ today), both dates set; active interns only. */
    @EntityGraph(attributePaths = {"user"})
    @Query("select p from InternProfile p join fetch p.user u where u.role = 'INTERN' and u.active = true "
            + "and p.internshipEndDate >= :today and p.internshipStartDate is not null and p.internshipEndDate is not null")
    List<InternProfile> findWithOpenContractOnOrAfter(@Param("today") LocalDate today);
}
