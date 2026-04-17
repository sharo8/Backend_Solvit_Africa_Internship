package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "evaluation_forms", indexes = {
        @Index(columnList = "intern_id"),
        @Index(columnList = "supervisor_id"),
        @Index(columnList = "form_type"),
        @Index(columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationForm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternRecord intern;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supervisor_id", nullable = false)
    private User supervisor;

    @Enumerated(EnumType.STRING)
    @Column(name = "form_type", nullable = false, length = 32)
    private FormType formType;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "submission_date")
    private Instant submissionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FormStatus status;

    @Column(name = "attendance_rating", precision = 4, scale = 2)
    private BigDecimal attendanceRating;
    @Column(name = "task_completion_rating", precision = 4, scale = 2)
    private BigDecimal taskCompletionRating;
    @Column(name = "work_quality_rating", precision = 4, scale = 2)
    private BigDecimal workQualityRating;
    @Column(name = "technical_skills_rating", precision = 4, scale = 2)
    private BigDecimal technicalSkillsRating;
    @Column(name = "conduct_engagement_rating", precision = 4, scale = 2)
    private BigDecimal conductEngagementRating;

    @Column(name = "attendance_comment", columnDefinition = "TEXT")
    private String attendanceComment;
    @Column(name = "task_completion_comment", columnDefinition = "TEXT")
    private String taskCompletionComment;
    @Column(name = "work_quality_comment", columnDefinition = "TEXT")
    private String workQualityComment;
    @Column(name = "technical_skills_comment", columnDefinition = "TEXT")
    private String technicalSkillsComment;
    @Column(name = "conduct_comment", columnDefinition = "TEXT")
    private String conductComment;
    @Column(name = "general_comment", columnDefinition = "TEXT")
    private String generalComment;

    @Column(name = "ai_computed_score", precision = 6, scale = 2)
    private BigDecimal aiComputedScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_grade", length = 32)
    private AiGrade aiGrade;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_prediction", length = 32)
    private AiPrediction aiPrediction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum FormType { MID_TERM, FINAL }
    public enum FormStatus { DRAFT, SUBMITTED, REVIEWED }
    public enum AiGrade { EXCELLENT, GOOD, SATISFACTORY, NEEDS_IMPROVEMENT, FAILING }
    public enum AiPrediction { ON_TRACK, AT_RISK, CRITICAL }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = FormStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
