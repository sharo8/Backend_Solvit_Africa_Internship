package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "feedbacks", indexes = {
    @Index(columnList = "intern_id"),
    @Index(columnList = "supervisor_id"),
    @Index(columnList = "feedback_type"),
    @Index(columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_id", nullable = false)
    private User intern;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id", nullable = false)
    private User supervisor;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false)
    private FeedbackType feedbackType;

    @Column(name = "rating_score")
    private Integer ratingScore;

    @Column(length = 2000)
    private String comment;

    /** NLP-derived: POSITIVE, NEUTRAL, NEGATIVE (rule-based lexicon). */
    @Column(name = "sentiment_label", length = 20)
    private String sentimentLabel;

    /** -1.0 (negative) .. 1.0 (positive). */
    @Column(name = "sentiment_score")
    private Double sentimentScore;

    @Column(name = "structured_ratings", columnDefinition = "JSON")
    private String structuredRatings;

    @Column(name = "anonymous")
    private boolean anonymous;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "acknowledged")
    private boolean acknowledged;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum FeedbackType {
        PEER,
        SUPERVISOR,
        SELF,
        THREE_SIXTY
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
