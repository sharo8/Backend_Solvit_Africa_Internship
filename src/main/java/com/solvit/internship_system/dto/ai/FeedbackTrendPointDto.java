package com.solvit.internship_system.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeedbackTrendPointDto {
    private String period;
    private double averageSentiment;
    private int count;
}
