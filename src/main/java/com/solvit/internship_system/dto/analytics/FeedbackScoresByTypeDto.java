package com.solvit.internship_system.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackScoresByTypeDto {
    private String type;
    private double avgRating;
    private long count;
}
