package com.solvit.internship_system.dto.feedback;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Builder
public record FeedbackSummaryDto(
        Long internId,
        String internName,
        LocalDate from,
        LocalDate to,
        int feedbackCount,
        double overallAverage,
        double progressionPercent,
        Map<String, Double> criteriaAverages,
        List<CriterionTrendPointDto> criteriaTrend,
        List<String> topStrengths,
        List<String> topImprovements,
        List<String> consolidatedRecommendations
) {
}

