package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.HrApprovalStatus;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndActiveTrue(String email);

    Optional<User> findByUniversityId(String universityId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<User> findByRole(Role role);

    Page<User> findByRole(Role role, Pageable pageable);

    List<User> findByRoleAndActiveTrue(Role role);

    List<User> findByRoleInAndActiveTrue(Collection<Role> roles);

    List<User> findByRoleAndCreatedAtBetween(Role role, Instant start, Instant end);

    @Query("SELECT u FROM User u WHERE " +
            "(:q IS NULL OR :q = '' OR LOWER(u.firstName) LIKE LOWER(CONCAT(CONCAT('%', :q), '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT(CONCAT('%', :q), '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT(CONCAT('%', :q), '%')) OR " +
            "(u.universityId IS NOT NULL AND u.universityId LIKE CONCAT(CONCAT('%', :q), '%'))) " +
            "AND (:role IS NULL OR u.role = :role) " +
            "AND (:active IS NULL OR u.active = :active) " +
            "AND (:hrApproval IS NULL OR u.hrApprovalStatus = :hrApproval) " +
            "ORDER BY u.createdAt DESC")
    Page<User> searchUsers(@Param("q") String q, @Param("role") Role role, @Param("active") Boolean active,
                          @Param("hrApproval") HrApprovalStatus hrApproval, Pageable pageable);

    long countByRole(Role role);

    long countByActive(boolean active);

    long countByCreatedAtAfter(Instant date);

    long countByEmailVerified(boolean emailVerified);

    @Query("SELECT u FROM User u WHERE u.role = 'INTERN' AND u.id IN :ids " +
            "AND (:q IS NULL OR :q = '' OR LOWER(u.firstName) LIKE LOWER(CONCAT(CONCAT('%', :q), '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT(CONCAT('%', :q), '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT(CONCAT('%', :q), '%')) OR " +
            "(u.universityId IS NOT NULL AND u.universityId LIKE CONCAT(CONCAT('%', :q), '%'))) " +
            "AND (:active IS NULL OR u.active = :active) " +
            "ORDER BY u.createdAt DESC")
    Page<User> findInternsByIdInAndSearch(@Param("ids") List<Long> ids, @Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
