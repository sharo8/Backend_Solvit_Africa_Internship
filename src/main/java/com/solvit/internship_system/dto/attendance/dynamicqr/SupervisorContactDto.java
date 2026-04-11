package com.solvit.internship_system.dto.attendance.dynamicqr;

public record SupervisorContactDto(
        String supervisorName,
        String supervisorEmail,
        boolean available
) {}
