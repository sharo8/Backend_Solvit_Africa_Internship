package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "intern_skills", indexes = {
        @Index(columnList = "intern_id"),
        @Index(columnList = "skill_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternSkill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternRecord intern;

    @Column(name = "skill_name", nullable = false, length = 255)
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "initial_level", nullable = false, length = 32)
    private SkillLevel initialLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_level", nullable = false, length = 32)
    private SkillLevel currentLevel;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessed_by")
    private User assessedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public enum SkillLevel { BEGINNER, INTERMEDIATE, ADVANCED }

    @PrePersist
    protected void onCreate() {
        if (assessedAt == null) {
            assessedAt = Instant.now();
        }
        if (currentLevel == null) {
            currentLevel = initialLevel != null ? initialLevel : SkillLevel.BEGINNER;
        }
    }
}
