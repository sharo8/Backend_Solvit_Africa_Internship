package com.solvit.internship_system.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackScoresTrendDto {
    private String week;
    private double supervisorScore;
    private double selfScore;
    private double peerScore;
}
