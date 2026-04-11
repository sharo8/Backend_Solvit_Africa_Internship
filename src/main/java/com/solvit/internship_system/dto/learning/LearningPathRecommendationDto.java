package com.solvit.internship_system.dto.learning;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LearningPathRecommendationDto {
    private String skillGap;
    private String title;
    private String description;
    private String externalUrl;
}

