package com.solvit.internship_system.dto.attendance;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreatePublicHolidayRequestDto {
    @NotNull
    private LocalDate date;
    private String name;
}
