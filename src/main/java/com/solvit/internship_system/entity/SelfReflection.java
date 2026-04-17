package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "self_reflections", indexes = {
        @Index(columnList = "intern_id"),
        @Index(columnList = "week_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfReflection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternRecord intern;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "submission_date", nullable = false)
    private Instant submissionDate;

    @Column(columnDefinition = "TEXT")
    private String accomplishments;
    @Column(columnDefinition = "TEXT")
    private String challenges;
    @Column(name = "goals_next_week", columnDefinition = "TEXT")
    private String goalsNextWeek;

    @Column(name = "self_rating_attendance", precision = 4, scale = 2)
    private BigDecimal selfRatingAttendance;
    @Column(name = "self_rating_tasks", precision = 4, scale = 2)
    private BigDecimal selfRatingTasks;
    @Column(name = "self_rating_skills", precision = 4, scale = 2)
    private BigDecimal selfRatingSkills;
    @Column(name = "self_rating_conduct", precision = 4, scale = 2)
    private BigDecimal selfRatingConduct;

    @Column(name = "overall_self_score", precision = 6, scale = 2)
    private BigDecimal overallSelfScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Mood mood;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum Mood { GREAT, GOOD, NEUTRAL, STRUGGLING, OVERWHELMED }

    @PrePersist
    @PreUpdate
    protected void normalize() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (submissionDate == null) {
            submissionDate = Instant.now();
        }
        if (overallSelfScore == null) {
            BigDecimal sum = BigDecimal.ZERO;
            int count = 0;
            if (selfRatingAttendance != null) { sum = sum.add(selfRatingAttendance); count++; }
            if (selfRatingTasks != null) { sum = sum.add(selfRatingTasks); count++; }
            if (selfRatingSkills != null) { sum = sum.add(selfRatingSkills); count++; }
            if (selfRatingConduct != null) { sum = sum.add(selfRatingConduct); count++; }
            if (count > 0) {
                overallSelfScore = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            }
        }
    }
}
