package com.solvit.internship_system.dto.attendance.dynamicqr;

/**
 * Supervisor decision. When {@code approve} is true, {@code bonusGenerations} must be 1 or 2 (extra QR generations for that day).
 */
public record QrExtraAccessResolveDto(boolean approve, Integer bonusGenerations) {}
