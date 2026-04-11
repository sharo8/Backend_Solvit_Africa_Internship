package com.solvit.internship_system.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PeerComparisonDto {
    private Long internId;
    private String internName;
    private double compositeScore;
    private double cohortAverage;
    private double peerPercentile;
    private List<String> identifiedGaps;
    private List<String> earlyWarnings;
}
