package com.solvit.internship_system.dto.tasks;

import com.solvit.internship_system.entity.Task;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class TaskDto {
    private Long id;
    private String title;
    private String description;
    private String instructions;
    private Task.TaskStatus status;
    private Task.TaskPriority priority;
    private LocalDate dueDate;
    private Integer progressPercent;
    private String feedbackNote;
    /** Intern-submitted completion proof (links, notes). */
    private String completionEvidence;
    private String evidenceUrl;
    private String evidenceNotes;
    private String supervisorComment;
    private String rejectionReason;
    private Integer estimatedHours;
    /** Same as persisted {@code timeLoggedSeconds}; API alias. */
    private Integer loggedSeconds;
    private Integer timeLoggedSeconds;
    private Integer submissionCount;
    private boolean emailSent;

    private UserSummaryDto assignee;
    private UserSummaryDto assigner;
    private UserSummaryDto supervisor;

    private Long projectId;
    private String projectTitle;
    private boolean active;

    private Instant startedAt;
    private Instant completedAt;
    private Instant validatedAt;
    private Long validatedBy;
    private String validatedByName;

    private Long cohortGroupId;
    private String cohortGroupName;
    private String groupAssignmentBatchId;
    private Long dependsOnTaskId;

    /** When status is OVERDUE and dueDate is set: days past deadline. */
    private Long daysOverdue;
    /** Display helpers */
    private String assignedToName;
    private String assignedToAvatar;
    private String supervisorName;
    private String groupName;
}
