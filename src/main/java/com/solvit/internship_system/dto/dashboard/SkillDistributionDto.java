package com.solvit.internship_system.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillDistributionDto {
    private String skill;
    private double averageScore;
}
