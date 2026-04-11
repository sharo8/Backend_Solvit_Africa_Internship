package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.LearningPath;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LearningPathRepository extends JpaRepository<LearningPath, Long> {

    List<LearningPath> findByUser_IdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    Page<LearningPath> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
