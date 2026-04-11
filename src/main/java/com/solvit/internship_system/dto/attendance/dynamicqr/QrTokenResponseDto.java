package com.solvit.internship_system.dto.attendance.dynamicqr;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QrTokenResponseDto {
    String token;
    long expiresAt;
    String firstName;
    String lastName;
    Long internId;
}
