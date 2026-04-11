package com.solvit.internship_system.dto.evaluation;

import com.solvit.internship_system.entity.Evaluation;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class EvaluationDto {
    private Long id;
    private Long internId;
    private String internName;
    private Long evaluatorId;
    private String evaluatorName;
    private Long groupId;
    private String groupName;
    private Evaluation.EvaluationType type;
    private Evaluation.EvaluationStatus status;
    private Integer technicalScore;
    private Integer communicationScore;
    private Integer attendanceScore;
    private Integer initiativeScore;
    private Integer overallScore;
    private String strengthsNote;
    private String improvementNote;
    private String supervisorComment;
    private String internResponse;
    private boolean internAcknowledged;
    private Instant acknowledgedAt;
    private LocalDate evaluationDate;
}
