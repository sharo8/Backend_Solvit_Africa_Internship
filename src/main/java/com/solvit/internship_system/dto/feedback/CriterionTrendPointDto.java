package com.solvit.internship_system.dto.feedback;

import lombok.Builder;

import java.time.LocalDate;
import java.util.Map;

@Builder
public record CriterionTrendPointDto(
        LocalDate date,
        Map<String, Double> criteria,
        double overallScore
) {
}

