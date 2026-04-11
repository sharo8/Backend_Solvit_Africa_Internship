package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.Announcement;
import com.solvit.internship_system.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    Page<Announcement> findByTargetRoleIsNullOrTargetRoleOrderByPinnedDescCreatedAtDesc(Role targetRole, Pageable pageable);

    Page<Announcement> findAllByOrderByPinnedDescCreatedAtDesc(Pageable pageable);

    long countByTargetRoleIsNullOrTargetRole(Role targetRole);
}
