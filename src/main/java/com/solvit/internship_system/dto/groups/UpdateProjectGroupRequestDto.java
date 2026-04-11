package com.solvit.internship_system.dto.groups;

import com.solvit.internship_system.entity.GroupStatus;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProjectGroupRequestDto {
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private GroupStatus status;
}
