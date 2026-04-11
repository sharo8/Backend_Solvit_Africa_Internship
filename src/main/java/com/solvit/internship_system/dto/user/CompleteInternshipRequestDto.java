package com.solvit.internship_system.dto.user;

import lombok.Data;

import java.time.LocalDate;

/**
 * If {@code endDate} is null, the contract ends today (Kigali).
 */
@Data
public class CompleteInternshipRequestDto {

    private LocalDate endDate;
}
