package com.solvit.internship_system.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDailyDto {
    private String date;
    private long present;
    private long absent;
    private long late;
}
