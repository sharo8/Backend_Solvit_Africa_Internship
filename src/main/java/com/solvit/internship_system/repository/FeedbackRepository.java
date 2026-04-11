package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    @EntityGraph(attributePaths = {"intern", "supervisor"})
    Page<Feedback> findByIntern_IdOrderByCreatedAtDesc(Long internId, Pageable pageable);

    @EntityGraph(attributePaths = {"intern", "supervisor"})
    Page<Feedback> findBySupervisor_IdOrderByCreatedAtDesc(Long supervisorId, Pageable pageable);

    List<Feedback> findByCreatedAtBetween(Instant start, Instant end);

    @EntityGraph(attributePaths = {"intern", "supervisor"})
    List<Feedback> findBySupervisor_IdAndCreatedAtBetween(Long supervisorId, Instant start, Instant end);

    List<Feedback> findByIntern_IdOrderByCreatedAtDesc(Long internId);

    List<Feedback> findByCreatedAtAfter(Instant after);

    List<Feedback> findByIntern_IdAndCreatedAtBetweenOrderByCreatedAtAsc(Long internId, Instant from, Instant to);
}
