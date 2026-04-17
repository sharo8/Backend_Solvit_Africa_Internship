package com.solvit.internship_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ai_performance_scores", indexes = {
        @Index(columnList = "intern_id"),
        @Index(columnList = "week_number"),
        @Index(columnList = "computed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPerformanceScoreRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternRecord intern;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "attendance_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal attendanceScore;
    @Column(name = "task_completion_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal taskCompletionScore;
    @Column(name = "work_quality_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal workQualityScore;
    @Column(name = "technical_skills_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal technicalSkillsScore;
    @Column(name = "conduct_engagement_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal conductEngagementScore;

    @Column(name = "final_score", nullable = false, precision = 6, scale = 2)
    private BigDecimal finalScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Grade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Prediction prediction;

    @Column(name = "prediction_confidence", nullable = false, precision = 6, scale = 4)
    private BigDecimal predictionConfidence;

    @Column(columnDefinition = "JSON")
    private String insights;
    @Column(columnDefinition = "JSON")
    private String recommendations;
    @Column(name = "skill_gaps", columnDefinition = "JSON")
    private String skillGaps;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_trend", length = 32)
    private ScoreTrend scoreTrend;

    @Column(name = "previous_score", precision = 6, scale = 2)
    private BigDecimal previousScore;
    @Column(name = "score_change", precision = 6, scale = 2)
    private BigDecimal scoreChange;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum Grade { EXCELLENT, GOOD, SATISFACTORY, NEEDS_IMPROVEMENT, FAILING }
    public enum Prediction { ON_TRACK, AT_RISK, CRITICAL }
    public enum ScoreTrend { IMPROVING, STABLE, DECLINING }

    @PrePersist
    protected void onCreate() {
        if (computedAt == null) {
            computedAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
