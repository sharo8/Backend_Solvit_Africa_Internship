package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.QrExtraAccessRequest;
import com.solvit.internship_system.entity.QrExtraAccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface QrExtraAccessRequestRepository extends JpaRepository<QrExtraAccessRequest, Long> {

    Optional<QrExtraAccessRequest> findFirstByInternUserIdAndRequestDateAndStatusOrderByCreatedAtDesc(
            Long internUserId, LocalDate requestDate, QrExtraAccessStatus status);

    List<QrExtraAccessRequest> findBySupervisorUserIdAndStatusOrderByCreatedAtDesc(
            Long supervisorUserId, QrExtraAccessStatus status);

    Optional<QrExtraAccessRequest> findTopByInternUserIdAndRequestDateOrderByCreatedAtDesc(
            Long internUserId, LocalDate requestDate);
}
