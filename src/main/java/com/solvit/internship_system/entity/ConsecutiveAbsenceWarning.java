package com.solvit.internship_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "consecutive_absence_warning",
        uniqueConstraints = @UniqueConstraint(columnNames = {"intern_user_id", "streak_end_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsecutiveAbsenceWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intern_user_id", nullable = false)
    private Long internUserId;

    @Column(name = "streak_end_date", nullable = false)
    private LocalDate streakEndDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
