package com.solvit.internship_system.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceByCohortDto {
    private String cohort;
    private double averageScore;
}
