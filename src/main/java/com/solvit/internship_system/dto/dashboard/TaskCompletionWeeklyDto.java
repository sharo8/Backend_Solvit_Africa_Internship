package com.solvit.internship_system.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCompletionWeeklyDto {
    private String week;
    private long completed;
    private long pending;
    private long overdue;
}
