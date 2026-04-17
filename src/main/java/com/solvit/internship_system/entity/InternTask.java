package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "intern_tasks", indexes = {
        @Index(columnList = "intern_id"),
        @Index(columnList = "supervisor_id"),
        @Index(columnList = "status"),
        @Index(columnList = "due_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternRecord intern;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supervisor_id", nullable = false)
    private User supervisor;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "assigned_date", nullable = false)
    private LocalDate assignedDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @Column(name = "quality_rating", precision = 4, scale = 2)
    private BigDecimal qualityRating;

    @Column(name = "quality_comment", columnDefinition = "TEXT")
    private String qualityComment;

    @Column(name = "is_on_time", nullable = false)
    @Builder.Default
    private boolean onTime = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum TaskStatus {
        ASSIGNED, IN_PROGRESS, SUBMITTED, APPROVED, REJECTED, OVERDUE
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = TaskStatus.ASSIGNED;
        }
        if (assignedDate == null) {
            assignedDate = LocalDate.now();
        }
    }
}
