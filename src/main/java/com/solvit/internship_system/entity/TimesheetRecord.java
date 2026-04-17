package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "timesheets", indexes = {
        @Index(columnList = "intern_id"),
        @Index(columnList = "week_number"),
        @Index(columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intern_id", nullable = false)
    private InternRecord intern;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Column(name = "hours_logged", nullable = false, precision = 6, scale = 2)
    private BigDecimal hoursLogged;

    @Column(name = "hours_verified", precision = 6, scale = 2)
    private BigDecimal hoursVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TimesheetStatus status;

    @Column(name = "supervisor_note", columnDefinition = "TEXT")
    private String supervisorNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public enum TimesheetStatus { PENDING, APPROVED, REJECTED, DISCREPANCY }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = TimesheetStatus.PENDING;
        }
    }
}
