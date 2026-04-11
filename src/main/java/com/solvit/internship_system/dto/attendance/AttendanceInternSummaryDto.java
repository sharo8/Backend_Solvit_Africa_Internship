package com.solvit.internship_system.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceInternSummaryDto {

    private Long internId;
    private String firstName;
    private String lastName;
    private String supervisorName;
    /** ACTIVE, UPCOMING, COMPLETED, NO_DATES */
    private String contractPhase;
    private long expectedWorkdays;
    private long countedPresentDays;
    private double attendanceRatePercent;
}
