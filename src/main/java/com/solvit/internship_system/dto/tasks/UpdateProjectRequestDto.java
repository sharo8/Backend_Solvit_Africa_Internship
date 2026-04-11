package com.solvit.internship_system.dto.tasks;

import com.solvit.internship_system.entity.Project;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class UpdateProjectRequestDto {
    private String title;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private Project.ProjectStatus status;
    private List<Long> assignedInternIds;
}

