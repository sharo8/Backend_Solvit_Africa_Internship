package com.solvit.internship_system.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveStatusMonthlyDto {
    private String month;
    private long approved;
    private long rejected;
    private long pending;
}
