package com.solvit.internship_system.dto.attendance;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class AttendanceStatsDto {
    private LocalDate date;
    private long totalInterns;
    private long present;
    private long absent;
    private long late;
    private long excused;
    private long halfDay;
    private double attendanceRatePercent;
}

