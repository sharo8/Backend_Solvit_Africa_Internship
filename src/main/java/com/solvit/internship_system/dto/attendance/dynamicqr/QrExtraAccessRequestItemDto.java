package com.solvit.internship_system.dto.attendance.dynamicqr;

import java.time.Instant;
import java.time.LocalDate;

public record QrExtraAccessRequestItemDto(
        long id,
        long internId,
        String internName,
        LocalDate requestDate,
        String status,
        String message,
        Instant createdAt
) {}
