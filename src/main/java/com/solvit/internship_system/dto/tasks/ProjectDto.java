package com.solvit.internship_system.dto.tasks;

import com.solvit.internship_system.entity.Project;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ProjectDto {
    private Long id;
    private String title;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private Project.ProjectStatus status;
    private Long groupId;
    private String groupName;
    private Long supervisorId;
    private String supervisorName;
    private UserSummaryDto createdBy;
    private List<UserSummaryDto> assignedInterns;
    private boolean active;
    private Instant createdAt;
}

