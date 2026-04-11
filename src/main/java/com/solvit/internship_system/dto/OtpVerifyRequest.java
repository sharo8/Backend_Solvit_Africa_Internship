package com.solvit.internship_system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpVerifyRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String otp;

    private com.solvit.internship_system.entity.OtpVerification.OtpType otpType;
}
