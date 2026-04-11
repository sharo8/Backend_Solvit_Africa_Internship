package com.solvit.internship_system.dto.attendance.dynamicqr;

import lombok.Data;

@Data
public class ScanRequestDto {
    private String token;
    private Long locationId;
    /** Optional GPS from scanner device (degrees). */
    private Double latitude;
    private Double longitude;
}
