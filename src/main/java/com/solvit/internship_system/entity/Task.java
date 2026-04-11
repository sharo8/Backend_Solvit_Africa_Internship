package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tasks", indexes = {
        @Index(columnList = "assignee_id"),
        @Index(columnList = "assigner_id"),
        @Index(columnList = "supervisor_user_id"),
        @Index(columnList = "cohort_group_id"),
        @Index(columnList = "status"),
        @Index(columnList = "due_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", nullable = false)
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigner_id")
    private User assigner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /** Accountable supervisor (required for new tasks; may be null on legacy rows). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_user_id")
    private User supervisor;

    /** When set, task was created as part of a cohort-wide assignment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_group_id")
    private ProjectGroup cohortGroup;

    @Column(name = "group_assignment_batch_id", length = 36)
    private String groupAssignmentBatchId;

    /** Blocks submission until this task is VALIDATED. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depends_on_task_id")
    private Task dependsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private TaskPriority priority;

    @Min(0)
    @Max(100)
    @Column(name = "progress_percent")
    private Integer progressPercent;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "feedback_note", length = 2000)
    private String feedbackNote;

    /** Intern-submitted proof of completion (links, notes, file URLs) when moving to IN_REVIEW. */
    @Column(name = "completion_evidence", length = 4000)
    private String completionEvidence;

    @Column(name = "evidence_url", columnDefinition = "TEXT")
    private String evidenceUrl;

    @Column(name = "evidence_notes", columnDefinition = "TEXT")
    private String evidenceNotes;

    @Column(name = "supervisor_comment", columnDefinition = "TEXT")
    private String supervisorComment;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "estimated_hours")
    private Integer estimatedHours;

    @Column(name = "submission_count", nullable = false)
    @Builder.Default
    private Integer submissionCount = 0;

    @Column(name = "expected_evidence_type", length = 32)
    private String expectedEvidenceType;

    @Column(name = "email_sent", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private boolean emailSent = false;

    @Column(name = "due_tomorrow_reminder_sent", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private boolean dueTomorrowReminderSent = false;

    @Column(name = "overdue_alert_sent", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private boolean overdueAlertSent = false;

    @Column(name = "same_day_incomplete_reminder_sent", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private boolean sameDayIncompleteReminderSent = false;

    @Column(name = "in_review_email_sent", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private boolean inReviewEmailSent = false;

    @Column(name = "completed_email_sent", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private boolean completedEmailSent = false;

    @Column(name = "cancelled_email_sent", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private boolean cancelledEmailSent = false;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "milestone_name", length = 255)
    private String milestoneName;

    @Column(name = "validated_by")
    private Long validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    /** Cumulative time logged by intern on this task (e.g. focus timers), in seconds. */
    @Column(name = "time_logged_seconds", nullable = false)
    @Builder.Default
    private Integer timeLoggedSeconds = 0;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "active", nullable = false, columnDefinition = "tinyint(1) default 1")
    @Builder.Default
    private boolean active = true;

    public enum TaskStatus {
        /** Created, not yet started. */
        PENDING,
        /** Intern started working ({@link #startedAt} set). */
        IN_PROGRESS,
        /** Intern submitted; waiting supervisor review. */
        IN_REVIEW,
        /** Supervisor rejected submission; intern must address feedback and re-submit. */
        REJECTED,
        /** Supervisor approved ({@link #validatedBy}, {@link #validatedAt} set). */
        VALIDATED,
        /** Due date passed without submission; set by scheduler only. */
        OVERDUE,
        /** Soft-cancelled ({@link #active} = false). */
        CANCELLED
    }

    public enum TaskPriority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (timeLoggedSeconds == null) {
            timeLoggedSeconds = 0;
        }
        if (submissionCount == null) {
            submissionCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        if (timeLoggedSeconds == null) {
            timeLoggedSeconds = 0;
        }
        if (submissionCount == null) {
            submissionCount = 0;
        }
    }
}
