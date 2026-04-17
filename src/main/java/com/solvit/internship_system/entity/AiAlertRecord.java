package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_alerts", indexes = {
        @Index(columnList = "intern_id"),
        @Index(columnList = "supervisor_id"),
        @Index(columnList = "alert_type"),
        @Index(columnList = "severity")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAlertRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternRecord intern;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private User supervisor;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 64)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AlertSeverity severity;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "triggered_value", precision = 10, scale = 2)
    private BigDecimal triggeredValue;

    @Column(name = "threshold_value", precision = 10, scale = 2)
    private BigDecimal thresholdValue;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "is_resolved", nullable = false)
    @Builder.Default
    private boolean resolved = false;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum AlertType {
        ATTENDANCE_CRITICAL, ATTENDANCE_WARNING, TASK_OVERDUE, QUALITY_DROP, SKILL_PLATEAU,
        PREDICTION_AT_RISK, PREDICTION_CRITICAL, TIMESHEET_DISCREPANCY, CONSECUTIVE_ABSENCES
    }

    public enum AlertSeverity { LOW, MEDIUM, HIGH, CRITICAL }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
