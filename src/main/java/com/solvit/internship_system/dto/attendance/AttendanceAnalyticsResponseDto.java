package com.solvit.internship_system.dto.attendance;

import com.solvit.internship_system.entity.Attendance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceAnalyticsResponseDto {

    private List<AttendanceDailyTrendDto> dailyTrend;
    private List<AttendanceInternSummaryDto> byIntern;
    /** Status → count of row-days in period (for histogram). */
    private Map<Attendance.AttendanceStatus, Long> statusHistogram;
}
