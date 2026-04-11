package com.solvit.internship_system.controller;

import com.solvit.internship_system.dto.*;
import com.solvit.internship_system.entity.OtpVerification;
import com.solvit.internship_system.security.CurrentUserResolver;
import com.solvit.internship_system.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserResolver currentUser;

    @PostMapping("/login")
    public ResponseEntity<LoginOtpResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<MessageResponse> resendOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) return ResponseEntity.badRequest().build();
        String typeStr = body.get("otpType");
        OtpVerification.OtpType type = typeStr != null ? OtpVerification.OtpType.valueOf(typeStr) : OtpVerification.OtpType.LOGIN;
        return ResponseEntity.ok(authService.resendOtp(email, type));
    }

    @PostMapping("/first-login/confirm-know-password")
    public ResponseEntity<MessageResponse> confirmFirstLoginKnowPassword() {
        Long userId = currentUser.requireUserId();
        return ResponseEntity.ok(authService.confirmFirstLoginKnowPassword(userId));
    }

    @PostMapping("/first-login/set-password")
    public ResponseEntity<MessageResponse> setPasswordFirstLogin(@RequestBody Map<String, String> body) {
        Long userId = currentUser.requireUserId();
        String newPassword = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");
        return ResponseEntity.ok(authService.setPasswordFirstLogin(userId, newPassword, confirmPassword));
    }
}
