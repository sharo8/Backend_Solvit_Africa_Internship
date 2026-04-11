package com.solvit.internship_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "performance_scores", indexes = {
    @Index(columnList = "intern_id"),
    @Index(columnList = "period_type, period_value")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intern_id", nullable = false)
    private User intern;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "skill_gap_data", columnDefinition = "JSON")
    private String skillGapData;

    @Column(name = "period_type", length = 20)
    private String periodType;

    @Column(name = "period_value", length = 50)
    private String periodValue;

    @Column(name = "at_risk")
    private boolean atRisk;

    /** 0–100: position vs other interns in the same cohort (rolling month). */
    @Column(name = "peer_percentile")
    private Double peerPercentile;

    @Column(name = "recommendations", columnDefinition = "JSON")
    private String recommendations;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
