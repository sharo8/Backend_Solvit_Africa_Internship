package com.solvit.internship_system.dto.attendance.dynamicqr;

public record QrTokenClaims(
        Long internId,
        String nonce,
        String type,
        long expEpochSeconds
) {}
