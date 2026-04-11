package com.solvit.internship_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    @Email
    private String email;

    @JsonIgnore
    @Column(name = "password_hash")
    private String passwordHash;

    @NotBlank
    @Column(nullable = false)
    private String firstName;

    @NotBlank
    @Column(nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    /** VARCHAR avoids MySQL ENUM drift when new {@link Role} values are added (ENUM would truncate unknown values). */
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32)")
    private Role role;

    @Column(unique = true)
    private String universityId;

    @Column(name = "email_verified")
    private boolean emailVerified;

    @Column(name = "mfa_enabled")
    private boolean mfaEnabled;

    @Column(name = "profile_completed")
    private boolean profileCompleted;

    @Column(name = "consent_given")
    private boolean consentGiven;

    @Builder.Default
    @Column(name = "active")
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "hr_approval_status", nullable = false, length = 32)
    @Builder.Default
    private HrApprovalStatus hrApprovalStatus = HrApprovalStatus.APPROVED;

    /** Matches JWT {@code sid} claim; new OTP login replaces this and invalidates other tabs. */
    @Column(name = "auth_session_id", length = 64)
    private String authSessionId;

    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_by_id")
    private Long createdById;

    @Builder.Default
    @Column(name = "first_login")
    private Boolean firstLogin = true;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
