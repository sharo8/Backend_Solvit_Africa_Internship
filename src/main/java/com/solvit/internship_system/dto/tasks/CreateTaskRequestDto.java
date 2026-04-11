package com.solvit.internship_system.dto.tasks;

import com.solvit.internship_system.entity.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateTaskRequestDto {
    @NotBlank
    @Size(max = 500)
    private String title;

    @Size(max = 2000)
    private String description;

    /** Detailed instructions for the intern (optional). */
    @Size(max = 5000)
    private String instructions;

    @NotNull
    private LocalDate dueDate;

    @NotNull
    private Task.TaskPriority priority;

    /** Required for {@link TaskAssignmentMode#INDIVIDUAL}; omit for {@link TaskAssignmentMode#GROUP_COHORT}. */
    private Long assigneeId;

    @NotNull
    private Long projectId;

    /** Responsible supervisor for the task (required). Must align with project/group supervisor when applicable. */
    @NotNull
    private Long supervisorId;

    /** Defaults to {@link TaskAssignmentMode#INDIVIDUAL}. */
    private TaskAssignmentMode assignmentMode;

    /** Required when {@code assignmentMode == GROUP_COHORT}: cohort whose interns each receive a copy of the task. */
    private Long groupId;

    /** Optional: this task cannot be submitted for review until the prerequisite is VALIDATED. */
    private Long dependsOnTaskId;

    /** Estimated effort in hours (creator). */
    private Integer estimatedHours;

    /** Expected proof type: FILE, URL, or TEXT (optional). */
    @Size(max = 32)
    private String evidenceType;
}

