package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.InternTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface InternTaskRepository extends JpaRepository<InternTask, Long> {
    List<InternTask> findByIntern_IdOrderByCreatedAtDesc(Long internId);
    long countByIntern_IdAndStatus(Long internId, InternTask.TaskStatus status);
    long countByIntern_IdAndStatusAndDueDateBefore(Long internId, InternTask.TaskStatus status, LocalDate date);
}
