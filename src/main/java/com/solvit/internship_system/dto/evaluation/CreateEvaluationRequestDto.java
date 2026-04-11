package com.solvit.internship_system.dto.evaluation;

import com.solvit.internship_system.entity.Evaluation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateEvaluationRequestDto {
    @NotNull
    private Long internId;

    /** Optional cohort; uniqueness is per (intern, type, group). */
    private Long groupId;

    @NotNull
    private Evaluation.EvaluationType type;

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
