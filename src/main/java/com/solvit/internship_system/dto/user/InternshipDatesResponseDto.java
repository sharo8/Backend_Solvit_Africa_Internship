package com.solvit.internship_system.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternshipDatesResponseDto {

    private LocalDate internshipStartDate;
    private LocalDate internshipEndDate;
    private String internshipStatus;
}
