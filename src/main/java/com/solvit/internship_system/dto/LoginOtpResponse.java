package com.solvit.internship_system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginOtpResponse {

    private boolean requiresOtp;
    private String email;
    private String message;
}
