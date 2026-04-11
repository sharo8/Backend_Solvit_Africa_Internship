package com.solvit.internship_system.dto.ai;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AtRiskInternBriefDto {
    private Long internId;
    private String name;
    private Double overallScore;
    private Double peerPercentile;
    private String periodValue;
}
