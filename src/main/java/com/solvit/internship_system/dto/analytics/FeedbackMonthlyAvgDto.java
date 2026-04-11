package com.solvit.internship_system.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackMonthlyAvgDto {
    private String month;
    private double supervisor;
    private double self;
    private double peer;
}
