package com.solvit.internship_system.dto.groups;

import com.solvit.internship_system.entity.GroupStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class ProjectGroupDto {
    private Long id;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private GroupStatus status;
    private Long supervisorId;
    private String supervisorName;
    private long internCount;
    private Instant createdAt;
}
