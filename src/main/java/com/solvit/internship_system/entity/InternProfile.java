package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "intern_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 2000)
    private String bio;

    @Column(name = "academic_background", length = 1000)
    private String academicBackground;

    @Column(name = "career_goals", length = 1000)
    private String careerGoals;

    @Column(name = "institution", length = 255)
    private String institution;

    @Column(name = "cv_url", length = 500)
    private String cvUrl;

    @Column(name = "transcript_url", length = 500)
    private String transcriptUrl;

    @Column(name = "id_photo_url", length = 500)
    private String idPhotoUrl;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "supervisor_user_id")
    private Long supervisorUserId;

    @Column(name = "internship_start_date")
    private LocalDate internshipStartDate;

    @Column(name = "internship_end_date")
    private LocalDate internshipEndDate;

    /** When set, 30-day reminder was already sent for this planned end date (cleared when end date is extended). */
    @Column(name = "reminder_30d_sent_for_end_date")
    private LocalDate reminder30dSentForEndDate;

    /** When set, 7-day reminder was already sent for this planned end date. */
    @Column(name = "reminder_7d_sent_for_end_date")
    private LocalDate reminder7dSentForEndDate;

    @Column(name = "profile_completeness_percent")
    private Integer profileCompletenessPercent;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "intern_skills", joinColumns = @JoinColumn(name = "intern_profile_id"))
    @Column(name = "skill")
    private List<String> skills = new ArrayList<>();

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "intern_learning_objectives", joinColumns = @JoinColumn(name = "intern_profile_id"))
    @Column(name = "objective", length = 500)
    private List<String> learningObjectives = new ArrayList<>();

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
