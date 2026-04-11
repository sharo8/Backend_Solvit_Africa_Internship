package com.solvit.internship_system.dto.evaluation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AcknowledgeEvaluationRequestDto {
    @NotBlank
    private String internResponse;
}
