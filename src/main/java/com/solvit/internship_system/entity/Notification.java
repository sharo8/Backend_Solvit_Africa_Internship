package com.solvit.internship_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
    @Index(columnList = "user_id"),
    @Index(columnList = "user_id, read_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 2000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type")
    private NotificationType notificationType;

    @Column(name = "related_entity_type", length = 100)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "email_sent")
    private boolean emailSent;

    @Column(name = "created_at")
    private Instant createdAt;

    public enum NotificationType {
        ATTENDANCE_REMINDER,
        TASK_ASSIGNED,
        FEEDBACK_RECEIVED,
        LEAVE_APPROVED,
        LEAVE_REJECTED,
        /** Intern submitted leave; supervisor should review. */
        LEAVE_SUBMITTED,
        PASSWORD_CHANGED,
        OTP_SENT,
        PERFORMANCE_ALERT,
        ANNOUNCEMENT,
        MESSAGE,
        SYSTEM,
        /** Self-service intern registered; pending HR/Admin approval. */
        PENDING_INTERN_REGISTRATION
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isRead() {
        return readAt != null;
    }
}
