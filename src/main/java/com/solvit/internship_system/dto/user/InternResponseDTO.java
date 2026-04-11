package com.solvit.internship_system.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InternResponseDTO extends UserResponseDTO {

    private String supervisorName;
    private Double attendanceRate;
    private Double performanceScore;
    private boolean profileCompleted;

    /** Contract dates; both null if not configured. */
    private LocalDate internshipStartDate;
    private LocalDate internshipEndDate;
    /** ACTIVE, UPCOMING, COMPLETED, or NO_DATES */
    private String internshipStatus;
}
