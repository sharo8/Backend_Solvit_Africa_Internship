package com.solvit.internship_system.dto.user;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkIdsRequestDTO {

    @NotEmpty(message = "At least one ID is required")
    private List<Long> ids;
}
