package com.solvit.internship_system.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiPerformanceEvaluationDto {
    private Long internId;
    private String internName;
    private Double attendanceScore;
    private Double taskCompletionScore;
    private Double skillDevelopmentScore;
    private Double engagementScore;
    private Double compositeScore;
    /** Cohort mean for the same pillar (active interns), for objective comparison. */
    private Double cohortAvgAttendance;
    private Double cohortAvgTasks;
    private Double cohortAvgSkills;
    private Double cohortAvgEngagement;
    private String riskLevel;
    private List<String> skillGaps;
    private List<String> recommendations;
    /** Rule-based early signals (attendance momentum, overdue load, feedback drought, etc.). */
    private List<String> earlyWarnings;
    /** Short transparency note on how the score is produced. */
    private String modelSummary;
}

