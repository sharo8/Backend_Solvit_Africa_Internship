package com.solvit.internship_system.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LearningCompletionByPathDto {
    private String pathName;
    private double avgCompletion;
    private long enrolledCount;
}
