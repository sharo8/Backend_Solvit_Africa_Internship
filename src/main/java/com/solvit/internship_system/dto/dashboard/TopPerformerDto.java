package com.solvit.internship_system.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopPerformerDto {
    private Long internId;
    private String name;
    private double score;
    private double attendance;
    private long tasksCompleted;
}
