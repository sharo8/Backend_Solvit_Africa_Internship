package com.solvit.internship_system.dto.evaluation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateEvaluationDraftDto {
    @Min(0)
    @Max(100)
    private Integer technicalScore;
    @Min(0)
    @Max(100)
    private Integer communicationScore;
    @Min(0)
    @Max(100)
    private Integer attendanceScore;
    @Min(0)
    @Max(100)
    private Integer initiativeScore;
    private String strengthsNote;
    private String improvementNote;
    private String supervisorComment;
    private LocalDate evaluationDate;
}
