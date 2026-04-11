package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findByActiveTrueAndAssignee_IdOrderByCreatedAtDesc(Long assigneeId, Pageable pageable);

    Page<Task> findByActiveTrueAndAssigner_IdOrderByCreatedAtDesc(Long assignerId, Pageable pageable);

    List<Task> findByActiveTrueAndAssignee_IdAndStatus(Long assigneeId, Task.TaskStatus status);

    Page<Task> findByActiveTrueAndAssignee_IdAndStatus(Long assigneeId, Task.TaskStatus status, Pageable pageable);

    List<Task> findByActiveTrueAndDueDateBetween(LocalDate start, LocalDate end);

    long countByActiveTrueAndStatusIn(Set<Task.TaskStatus> statuses);

    long countByActiveTrueAndStatus(Task.TaskStatus status);

    long countByActiveTrueAndDueDateBeforeAndStatusIn(LocalDate date, Set<Task.TaskStatus> statuses);

    long countByActiveTrueAndAssignee_IdAndStatus(Long assigneeId, Task.TaskStatus status);

    long countByActiveTrueAndAssignee_Id(Long assigneeId);

    List<Task> findByActiveTrueAndStatusInAndDueDateAndDueTomorrowReminderSentFalse(
            Set<Task.TaskStatus> statuses,
            LocalDate dueDate
    );

    List<Task> findByActiveTrueAndStatusInAndDueDateBeforeAndOverdueAlertSentFalse(
            Set<Task.TaskStatus> statuses,
            LocalDate dueBefore
    );

    @Query("SELECT t FROM Task t WHERE COALESCE(t.active, true) = true AND t.dueDate = :due "
            + "AND t.status = :pending "
            + "AND t.sameDayIncompleteReminderSent = false "
            + "AND (t.progressPercent IS NULL OR t.progressPercent = 0)")
    List<Task> findDueTodayPendingNoProgressReminderNotSent(
            @Param("due") LocalDate due,
            @Param("pending") Task.TaskStatus pending
    );

    List<Task> findByActiveTrueAndStatusNotInAndDueDateBeforeAndOverdueAlertSentFalse(
            Set<Task.TaskStatus> notInStatuses,
            LocalDate dueBefore
    );

    boolean existsByActiveTrueAndTitleIgnoreCaseAndAssignee_IdAndProject_IsNull(String title, Long assigneeId);

    boolean existsByActiveTrueAndTitleIgnoreCaseAndAssignee_IdAndProject_Id(String title, Long assigneeId, Long projectId);

    List<Task> findByActiveTrueAndDueDateAndStatusIn(LocalDate dueDate, Set<Task.TaskStatus> statuses);

    List<Task> findByActiveTrueAndDueDateBeforeAndStatusNotIn(LocalDate dueBefore, Set<Task.TaskStatus> notInStatuses);

    long countByActiveTrue();

    List<Task> findByActiveTrue();

    long countByActiveTrueAndAssignee_IdAndStatusIn(Long assigneeId, Set<Task.TaskStatus> statuses);

    long countByActiveTrueAndAssignee_IdAndDueDateBeforeAndStatusNotIn(
            Long assigneeId, LocalDate dueBefore, Set<Task.TaskStatus> notInStatuses);

    /**
     * Explicit LEFT JOINs so optional filters do not imply INNER JOIN on assignee/project/supervisor paths
     * (tasks with null assignee_id were excluded when using t.assignee.id in WHERE).
     */
    @Query("SELECT DISTINCT t FROM Task t "
            + "LEFT JOIN t.assignee aff "
            + "LEFT JOIN t.assigner asg "
            + "LEFT JOIN t.supervisor supT "
            + "LEFT JOIN t.project pr "
            + "LEFT JOIN pr.group grp "
            + "LEFT JOIN grp.supervisor supG "
            + "WHERE COALESCE(t.active, true) = true "
            + "AND (:assignedTo IS NULL OR aff.id = :assignedTo) "
            + "AND (:projectId IS NULL OR (pr IS NOT NULL AND pr.id = :projectId)) "
            + "AND (:status IS NULL OR t.status = :status) "
            + "AND (:supervisorScopeId IS NULL OR "
            + "  (supT IS NOT NULL AND supT.id = :supervisorScopeId) OR "
            + "  (asg IS NOT NULL AND asg.id = :supervisorScopeId) OR "
            + "  (supG IS NOT NULL AND supG.id = :supervisorScopeId))")
    Page<Task> searchFiltered(
            @Param("assignedTo") Long assignedTo,
            @Param("projectId") Long projectId,
            @Param("status") Task.TaskStatus status,
            @Param("supervisorScopeId") Long supervisorScopeId,
            Pageable pageable);

    @Query("SELECT COUNT(DISTINCT t.id) FROM Task t "
            + "LEFT JOIN t.assignee aff "
            + "LEFT JOIN t.assigner asg "
            + "LEFT JOIN t.supervisor supT "
            + "LEFT JOIN t.project pr "
            + "LEFT JOIN pr.group grp "
            + "LEFT JOIN grp.supervisor supG "
            + "WHERE COALESCE(t.active, true) = true "
            + "AND (:assignedTo IS NULL OR aff.id = :assignedTo) "
            + "AND (:projectId IS NULL OR (pr IS NOT NULL AND pr.id = :projectId)) "
            + "AND (:status IS NULL OR t.status = :status) "
            + "AND (:supervisorScopeId IS NULL OR "
            + "  (supT IS NOT NULL AND supT.id = :supervisorScopeId) OR "
            + "  (asg IS NOT NULL AND asg.id = :supervisorScopeId) OR "
            + "  (supG IS NOT NULL AND supG.id = :supervisorScopeId))")
    long countSearchFiltered(
            @Param("assignedTo") Long assignedTo,
            @Param("projectId") Long projectId,
            @Param("status") Task.TaskStatus status,
            @Param("supervisorScopeId") Long supervisorScopeId
    );

    @Query("SELECT DISTINCT t FROM Task t "
            + "LEFT JOIN t.assignee aff "
            + "LEFT JOIN t.assigner asg "
            + "LEFT JOIN t.supervisor supT "
            + "LEFT JOIN t.project pr "
            + "LEFT JOIN pr.group grp "
            + "LEFT JOIN grp.supervisor supG "
            + "WHERE COALESCE(t.active, true) = true "
            + "AND (:assignedTo IS NULL OR aff.id = :assignedTo) "
            + "AND (:projectId IS NULL OR (pr IS NOT NULL AND pr.id = :projectId)) "
            + "AND t.status IN :statuses "
            + "AND (:supervisorScopeId IS NULL OR "
            + "  (supT IS NOT NULL AND supT.id = :supervisorScopeId) OR "
            + "  (asg IS NOT NULL AND asg.id = :supervisorScopeId) OR "
            + "  (supG IS NOT NULL AND supG.id = :supervisorScopeId))")
    Page<Task> searchFilteredStatuses(
            @Param("assignedTo") Long assignedTo,
            @Param("projectId") Long projectId,
            @Param("statuses") Set<Task.TaskStatus> statuses,
            @Param("supervisorScopeId") Long supervisorScopeId,
            Pageable pageable);

    @Query("SELECT COUNT(DISTINCT t.id) FROM Task t "
            + "LEFT JOIN t.assignee aff "
            + "LEFT JOIN t.assigner asg "
            + "LEFT JOIN t.supervisor supT "
            + "LEFT JOIN t.project pr "
            + "LEFT JOIN pr.group grp "
            + "LEFT JOIN grp.supervisor supG "
            + "WHERE COALESCE(t.active, true) = true "
            + "AND (:assignedTo IS NULL OR aff.id = :assignedTo) "
            + "AND (:projectId IS NULL OR (pr IS NOT NULL AND pr.id = :projectId)) "
            + "AND t.status IN :statuses "
            + "AND (:supervisorScopeId IS NULL OR "
            + "  (supT IS NOT NULL AND supT.id = :supervisorScopeId) OR "
            + "  (asg IS NOT NULL AND asg.id = :supervisorScopeId) OR "
            + "  (supG IS NOT NULL AND supG.id = :supervisorScopeId))")
    long countSearchFilteredStatuses(
            @Param("assignedTo") Long assignedTo,
            @Param("projectId") Long projectId,
            @Param("statuses") Set<Task.TaskStatus> statuses,
            @Param("supervisorScopeId") Long supervisorScopeId);

    @Query("SELECT COUNT(DISTINCT t.id) FROM Task t "
            + "LEFT JOIN t.assignee aff "
            + "LEFT JOIN t.assigner asg "
            + "LEFT JOIN t.supervisor supT "
            + "LEFT JOIN t.project pr "
            + "LEFT JOIN pr.group grp "
            + "LEFT JOIN grp.supervisor supG "
            + "WHERE COALESCE(t.active, true) = true "
            + "AND t.dueDate IS NOT NULL AND t.dueDate < :today "
            + "AND t.status IN :statuses "
            + "AND (:supervisorScopeId IS NULL OR "
            + "  (supT IS NOT NULL AND supT.id = :supervisorScopeId) OR "
            + "  (asg IS NOT NULL AND asg.id = :supervisorScopeId) OR "
            + "  (supG IS NOT NULL AND supG.id = :supervisorScopeId))")
    long countOverdueFiltered(
            @Param("today") LocalDate today,
            @Param("statuses") Set<Task.TaskStatus> statuses,
            @Param("supervisorScopeId") Long supervisorScopeId
    );

    /** Overdue tasks for a single assignee (intern dashboard / stats). */
    @Query("SELECT COUNT(DISTINCT t.id) FROM Task t JOIN t.assignee aff "
            + "WHERE COALESCE(t.active, true) = true AND aff.id = :assigneeId "
            + "AND t.dueDate IS NOT NULL AND t.dueDate < :today AND t.status IN :statuses")
    long countOverdueForAssignee(
            @Param("assigneeId") Long assigneeId,
            @Param("today") LocalDate today,
            @Param("statuses") Set<Task.TaskStatus> statuses
    );

    @Query(value = "SELECT COUNT(*) FROM tasks", nativeQuery = true)
    long debugCountAllTasks();

    @Query(value = "SELECT COUNT(*) FROM tasks WHERE active = true OR active = 1", nativeQuery = true)
    long debugCountActiveTasks();

    @Query(value = "SELECT DISTINCT active FROM tasks", nativeQuery = true)
    List<Object> debugDistinctActiveValues();

    /** Maps legacy DB value before {@code COMPLETED} was removed from the Java enum. */
    @Modifying
    @Query(value = "UPDATE tasks SET status = 'IN_REVIEW' WHERE status = 'COMPLETED'", nativeQuery = true)
    int migrateLegacyCompletedStatus();
}
