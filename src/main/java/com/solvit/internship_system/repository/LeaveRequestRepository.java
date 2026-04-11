package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    Page<LeaveRequest> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<LeaveRequest> findByStatusOrderByCreatedAtDesc(LeaveRequest.LeaveStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<LeaveRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<LeaveRequest> findByUser_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long userId,
            LeaveRequest.LeaveStatus status,
            LocalDate date1,
            LocalDate date2
    );
}
