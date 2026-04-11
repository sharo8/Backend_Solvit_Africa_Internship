package com.solvit.internship_system.dto.tasks;

import com.solvit.internship_system.entity.Task;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateTaskRequestDto {
    @Size(max = 500)
    private String title;

    @Size(max = 2000)
    private String description;

    @Min(0)
    @Max(100)
    private Integer progressPercent;

    @Size(max = 2000)
    private String feedbackNote;

    private LocalDate dueDate;

    private Task.TaskPriority priority;

    private Long projectId;
}

