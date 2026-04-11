package com.solvit.internship_system.dto.tasks;

import com.solvit.internship_system.entity.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateProjectRequestDto {
    @NotBlank
    private String title;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    @NotNull
    private Project.ProjectStatus status;

    /**
     * Optional cohort. If null, the project is standalone (no group, no project-level interns).
     */
    private Long groupId;

    /**
     * When {@code groupId} is set: interns to attach (must already be in the cohort).
     * If null or empty, every intern currently in the cohort is assigned.
     * Ignored for standalone projects.
     */
    private List<Long> assignedInternIds;
}

