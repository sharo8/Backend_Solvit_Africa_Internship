package com.solvit.internship_system.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternDetailDTO {

    private UserResponseDTO user;
    private Double attendanceRate;
    private long tasksCompleted;
    private long tasksPending;
    private Double performanceScore;
    private Instant lastLoginAt;

    private LocalDate internshipStartDate;
    private LocalDate internshipEndDate;
    private String internshipStatus;
}
