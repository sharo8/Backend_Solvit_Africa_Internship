package com.solvit.internship_system.dto.attendance.dynamicqr;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InternDynamicQrStatsDto {
    long totalDays;
    long presentDays;
    long absentDays;
    long lateDays;
    double attendanceRate;
    int avgDurationMinutes;
}
