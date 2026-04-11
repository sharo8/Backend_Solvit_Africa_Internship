package com.solvit.internship_system.dto.attendance.dynamicqr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceLocationDto {
    private Long id;
    private String name;
    private String address;
    private String checkInStartTime;
    private String checkInDeadline;
    private String checkOutDeadline;
    private Integer expectedHoursPerDay;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean active;
}
