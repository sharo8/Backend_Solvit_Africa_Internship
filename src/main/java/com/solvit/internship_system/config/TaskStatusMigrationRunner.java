package com.solvit.internship_system.config;

import com.solvit.internship_system.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs before normal use of {@link com.solvit.internship_system.entity.Task} so legacy
 * {@code COMPLETED} rows do not break JPA enum mapping.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TaskStatusMigrationRunner implements ApplicationRunner {

    private final TaskRepository taskRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            int n = taskRepository.migrateLegacyCompletedStatus();
            if (n > 0) {
                log.info("Migrated {} task row(s) from COMPLETED to IN_REVIEW", n);
            }
        } catch (Exception e) {
            log.warn("Task status migration could not run (fresh DB or column mismatch): {}", e.getMessage());
        }
    }
}
