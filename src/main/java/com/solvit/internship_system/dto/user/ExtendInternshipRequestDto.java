package com.solvit.internship_system.dto.user;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ExtendInternshipRequestDto {

    @NotNull
    private LocalDate newEndDate;
}
