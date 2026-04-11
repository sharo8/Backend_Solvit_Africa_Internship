package com.solvit.internship_system.dto.attendance.dynamicqr;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ScanResultDto {
    String action;
    Long internId;
    String internName;
    String time;
    String status;
    Integer lateMinutes;
    Integer durationMinutes;
    String message;
}
