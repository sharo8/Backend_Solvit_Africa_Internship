package com.solvit.internship_system.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiInsightsSummaryDto {
    private long feedbackAnalyzed;
    private long positiveCount;
    private long negativeCount;
    private long neutralCount;
    private double averageSentimentScore;
}
