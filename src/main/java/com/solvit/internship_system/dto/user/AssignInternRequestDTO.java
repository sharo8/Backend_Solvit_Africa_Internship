package com.solvit.internship_system.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignInternRequestDTO {

    @NotNull(message = "Intern ID is required")
    private Long internId;
}
