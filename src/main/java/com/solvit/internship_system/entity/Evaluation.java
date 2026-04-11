package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "evaluations", indexes = {
        @Index(columnList = "intern_id"),
        @Index(columnList = "evaluator_id"),
        @Index(columnList = "group_id"),
        @Index(columnList = "status"),
        @Index(columnList = "type"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intern_id", nullable = false)
    private User intern;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluator_id", nullable = false)
    private User evaluator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ProjectGroup group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvaluationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvaluationStatus status;

    @Column(name = "technical_score")
    private Integer technicalScore;

    @Column(name = "communication_score")
    private Integer communicationScore;

    @Column(name = "attendance_score")
    private Integer attendanceScore;

    @Column(name = "initiative_score")
    private Integer initiativeScore;

    /** Average of the four dimension scores; computed in lifecycle hooks — do not set manually in services. */
    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "strengths_note", length = 2000)
    private String strengthsNote;

    @Column(name = "improvement_note", length = 2000)
    private String improvementNote;

    @Column(name = "supervisor_comment", length = 2000)
    private String supervisorComment;

    @Column(name = "intern_response", length = 2000)
    private String internResponse;

    @Column(name = "evaluation_date")
    private LocalDate evaluationDate;

    @Column(name = "intern_acknowledged", nullable = false, columnDefinition = "tinyint(1) default 0")
    @Builder.Default
    private boolean internAcknowledged = false;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "active", nullable = false, columnDefinition = "tinyint(1) default 1")
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum EvaluationType {
        MID_TERM,
        FINAL,
        SPOT_CHECK
    }

    public enum EvaluationStatus {
        DRAFT,
        SUBMITTED,
        ACKNOWLEDGED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        recomputeOverallScore();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        recomputeOverallScore();
    }

    private void recomputeOverallScore() {
        if (technicalScore != null && communicationScore != null && attendanceScore != null && initiativeScore != null) {
            int sum = technicalScore + communicationScore + attendanceScore + initiativeScore;
            this.overallScore = sum / 4;
        } else {
            this.overallScore = null;
        }
    }
}
