package com.solvit.internship_system.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceDailyTrendDto {

    private LocalDate date;
    private long totalInterns;
    private long present;
    private long absent;
    private long late;
    private long excused;
    private double attendanceRatePercent;
}
