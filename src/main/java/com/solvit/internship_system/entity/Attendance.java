package com.solvit.internship_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendances", indexes = {
    @Index(columnList = "user_id, attendance_date"),
    @Index(columnList = "attendance_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_at")
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "latitude", length = 50)
    private String latitude;

    @Column(name = "longitude", length = 50)
    private String longitude;

    @Column(name = "check_in_location", length = 500)
    private String checkInLocation;

    @Column(name = "check_out_location", length = 500)
    private String checkOutLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private AttendanceStatus status;

    @Column(name = "manual_entry", nullable = false)
    @Builder.Default
    private boolean manualEntry = false;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modified_by_user_id")
    private User modifiedBy;

    @Column(name = "validated_by")
    private Long validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "notes", length = 1000)
    private String notes;

    /** Duration checkOut - checkIn in minutes; null if still on site. */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "is_excused", nullable = false)
    @Builder.Default
    private boolean excused = false;

    @Column(name = "excuse_reason", length = 500)
    private String excuseReason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum AttendanceStatus {
        PENDING,
        PRESENT,
        ABSENT,
        LATE,
        EXCUSED,
        HALF_DAY,
        LEAVE,
        REMOTE,
        VALIDATED
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
