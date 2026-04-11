package com.solvit.internship_system.dto.attendance.dynamicqr;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class AttendanceRecordApiDto {
    Long id;
    String internName;
    LocalDate date;
    String checkInTime;
    String checkOutTime;
    Integer durationMinutes;
    String status;
    Integer lateMinutes;
    String locationName;
    String scannedByName;
}
