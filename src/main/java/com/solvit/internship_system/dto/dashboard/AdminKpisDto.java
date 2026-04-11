package com.solvit.internship_system.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminKpisDto {
    private long totalUsers;
    private long totalInterns;
    private long totalSupervisors;
    private long atRiskInterns;
    private double averageAttendanceRate;
    private double averagePerformanceScore;
    private long pendingLeaveRequests;
    private double taskCompletionRate;
    private long activeInterns;
    private long completedInternships;
}
