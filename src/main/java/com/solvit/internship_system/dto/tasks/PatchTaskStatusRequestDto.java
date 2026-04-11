package com.solvit.internship_system.dto.tasks;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.solvit.internship_system.entity.Task;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PatchTaskStatusRequestDto {
    /** Target status; allowed transitions depend on role (see TaskService). */
    @NotNull
    @JsonDeserialize(using = TaskStatusPatchDeserializer.class)
    private Task.TaskStatus status;
    /** Required when rejecting review (IN_REVIEW → IN_PROGRESS). */
    @Size(max = 2000)
    private String feedbackNote;
    /** Required when intern submits for review (IN_PROGRESS → IN_REVIEW) unless already set. */
    @Size(max = 4000)
    private String completionEvidence;

    /** Optional URL proof (e.g. uploaded file link). */
    @Size(max = 1024)
    private String evidenceUrl;

    /** Short notes accompanying submission. */
    @Size(max = 2000)
    private String evidenceNotes;
}
