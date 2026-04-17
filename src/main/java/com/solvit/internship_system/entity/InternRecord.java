package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "interns", indexes = {
        @Index(columnList = "user_id"),
        @Index(columnList = "supervisor_id"),
        @Index(columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supervisor_id")
    private User supervisor;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 255)
    private String university;

    @Column(length = 255)
    private String department;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InternStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 8)
    private PreferredLanguage preferredLanguage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum InternStatus {
        ACTIVE, COMPLETED, DROPPED, SUSPENDED
    }

    public enum PreferredLanguage {
        EN, FR, KI
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (preferredLanguage == null) {
            preferredLanguage = PreferredLanguage.EN;
        }
        if (status == null) {
            status = InternStatus.ACTIVE;
        }
    }
}
