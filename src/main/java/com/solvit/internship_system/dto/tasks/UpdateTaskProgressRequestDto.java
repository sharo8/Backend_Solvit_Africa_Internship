package com.solvit.internship_system.dto.tasks;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTaskProgressRequestDto {
    @NotNull
    @Min(0)
    @Max(100)
    private Integer progressPercent;

    /**
     * Required when {@code progressPercent == 100}: links, notes, or description of work completed (min 10 chars in service).
     */
    @Size(max = 4000)
    private String completionEvidence;
}
