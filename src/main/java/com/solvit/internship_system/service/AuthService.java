package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.*;
import com.solvit.internship_system.entity.HrApprovalStatus;
import com.solvit.internship_system.entity.Notification;
import com.solvit.internship_system.entity.OtpVerification;
import com.solvit.internship_system.entity.PasswordResetToken;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.PasswordResetTokenRepository;
import com.solvit.internship_system.repository.UserRepository;
import com.solvit.internship_system.security.JwtUtil;
import com.solvit.internship_system.validation.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.jsonwebtoken.JwtException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Value("${password.reset.token.expiration.hours:24}")
    private int resetTokenExpirationHours;

    @Value("${otp.dev.mode:false}")
    private boolean otpDevMode;

    /**
     * Step 1: Login with email + password. If valid, sends OTP to email and returns requiresOtp=true.
     */
    private void ensureAccountMayAuthenticate(User user) {
        if (user.getHrApprovalStatus() == HrApprovalStatus.PENDING) {
            throw new BadRequestException(
                    "Your registration is pending approval by HR or an administrator. You cannot sign in yet.");
        }
        if (user.getHrApprovalStatus() == HrApprovalStatus.REJECTED) {
            throw new BadRequestException(
                    "Your registration was not approved. Contact your organization if you need help.");
        }
        if (!user.isActive()) {
            throw new BadRequestException("Your account is deactivated. Contact your administrator.");
        }
    }

    @Transactional
    public LoginOtpResponse login(LoginRequest request) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }
        ensureAccountMayAuthenticate(user);
        if (request.getRole() != null && user.getRole() != request.getRole()) {
            throw new BadRequestException("Role does not match. Please select the correct role.");
        }
        String otp = otpService.generateAndSave(user.getEmail(), OtpVerification.OtpType.LOGIN);
        if (!otpDevMode) {
            emailService.sendOtpEmail(user.getEmail(), otp, "login");
        }
        notificationService.create(user.getId(), "Login OTP Sent",
                "A one-time password was sent to your email for login.", com.solvit.internship_system.entity.Notification.NotificationType.OTP_SENT,
                null, null, false);
        auditService.log(user.getId(), "LOGIN_OTP_REQUESTED", "User", user.getId(), null, null, null, null);
        return LoginOtpResponse.builder()
                .requiresOtp(true)
                .email(user.getEmail())
                .message("OTP sent to your email. Please verify to complete login.")
                .build();
    }

    /**
     * Step 2: Verify OTP and return JWT tokens.
     */
    @Transactional
    public AuthResponse verifyOtp(OtpVerifyRequest request) {
        if (request.getOtpType() == null) request.setOtpType(OtpVerification.OtpType.LOGIN);
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        boolean valid = otpService.verify(email, request.getOtp(), request.getOtpType());
        if (!valid) throw new BadRequestException("Invalid or expired OTP.");
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        ensureAccountMayAuthenticate(user);
        String sessionId = UUID.randomUUID().toString();
        user.setAuthSessionId(sessionId);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getRole().name(), sessionId);
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail(), sessionId);
        auditService.log(user.getId(), "LOGIN_SUCCESS", "User", user.getId(), null, null, null, null);
        boolean requiresFirstLoginSetup = Boolean.TRUE.equals(user.getFirstLogin()) && user.getCreatedById() != null;
        AuthResponse.AuthResponseBuilder b = AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtil.getExpirationMs() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .profileCompleted(user.isProfileCompleted())
                .emailVerified(user.isEmailVerified());
        if (requiresFirstLoginSetup) b.requiresFirstLoginSetup(true);
        return b.build();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        PasswordPolicy.validate(request.getPassword());
        if (request.getRole() != Role.INTERN) {
            throw new BadRequestException("Public registration is only available for interns.");
        }
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already registered.");
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .role(Role.INTERN)
                .universityId(request.getUniversityId() != null && !request.getUniversityId().isBlank()
                        ? request.getUniversityId().trim() : null)
                .emailVerified(false)
                .profileCompleted(false)
                .consentGiven(false)
                .active(false)
                .hrApprovalStatus(HrApprovalStatus.PENDING)
                .createdById(null)
                .firstLogin(false)
                .build();
        user = userRepository.save(user);
        emailService.sendInternRegistrationPendingEmail(user.getFirstName(), user.getEmail());
        String notifyTitle = "New intern pending approval";
        String notifyMessage = String.format("%s %s (%s) registered and awaits HR approval.",
                user.getFirstName(), user.getLastName(), user.getEmail());
        List<User> notifyRecipients = new ArrayList<>(userRepository.findByRoleAndActiveTrue(Role.HR));
        if (notifyRecipients.isEmpty()) {
            notifyRecipients.addAll(userRepository.findByRoleAndActiveTrue(Role.ADMIN));
        }
        for (User recipient : notifyRecipients) {
            notificationService.create(
                    recipient.getId(),
                    notifyTitle,
                    notifyMessage,
                    Notification.NotificationType.PENDING_INTERN_REGISTRATION,
                    "User",
                    user.getId(),
                    false);
            emailService.sendHrNewPendingInternEmail(
                    recipient.getFirstName(), recipient.getEmail(),
                    user.getFirstName(), user.getLastName(), user.getEmail());
        }
        auditService.log(user.getId(), "REGISTER_PENDING", "User", user.getId(), null, null, null, null);
        return AuthResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .expiresIn(0)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .profileCompleted(false)
                .emailVerified(false)
                .pendingApproval(true)
                .build();
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        Optional<User> opt = userRepository.findByEmail(request.getEmail());
        if (opt.isEmpty()) {
            return MessageResponse.of("If an account exists with this email, you will receive a reset link.");
        }
        User user = opt.get();
        passwordResetTokenRepository.deleteByUser_Id(user.getId());
        String token = UUID.randomUUID().toString();
        PasswordResetToken prt = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(Instant.now().plusSeconds(resetTokenExpirationHours * 3600L))
                .used(false)
                .build();
        passwordResetTokenRepository.save(prt);
        String resetLink = "http://localhost:3000/reset-password?token=" + token;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        notificationService.create(user.getId(), "Password Reset Requested",
                "You requested a password reset. Check your email for the link.", Notification.NotificationType.PASSWORD_CHANGED,
                null, null, false);
        auditService.log(user.getId(), "FORGOT_PASSWORD", "User", user.getId(), null, null, null, null);
        return MessageResponse.of("If an account exists with this email, you will receive a reset link.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        PasswordPolicy.validate(request.getNewPassword());
        PasswordResetToken prt = passwordResetTokenRepository.findByTokenAndUsedFalseAndExpiresAtAfter(
                request.getToken(), Instant.now()).orElseThrow(() -> new BadRequestException("Invalid or expired reset token."));
        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setAuthSessionId(null);
        userRepository.save(user);
        prt.setUsed(true);
        passwordResetTokenRepository.save(prt);
        emailService.sendNotificationEmail(user.getEmail(), "Password Changed",
                "Your password was changed successfully. If you did not do this, please contact support.");
        notificationService.create(user.getId(), "Password Changed",
                "Your password was changed successfully.", Notification.NotificationType.PASSWORD_CHANGED,
                null, null, false);
        auditService.log(user.getId(), "PASSWORD_RESET", "User", user.getId(), null, null, null, null);
        return MessageResponse.of("Password reset successfully. You can now login.");
    }

    public AuthResponse refreshToken(String refreshToken) {
        try {
            if (!"refresh".equals(jwtUtil.getTokenType(refreshToken))) {
                throw new BadRequestException("Invalid refresh token.");
            }
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadRequestException("Invalid refresh token.");
        }
        String email = jwtUtil.getEmailFromToken(refreshToken).trim().toLowerCase();
        String sid = jwtUtil.getSessionIdFromToken(refreshToken);
        if (sid == null || sid.isBlank()) {
            throw new BadRequestException("Invalid refresh token.");
        }
        User user = userRepository.findByEmailAndActiveTrue(email).orElseThrow(() -> new BadRequestException("Invalid refresh token."));
        ensureAccountMayAuthenticate(user);
        if (!sid.equals(user.getAuthSessionId())) {
            throw new BadRequestException("Session expired. Please sign in again.");
        }
        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getId(), user.getRole().name(), sid);
        String newRefresh = jwtUtil.generateRefreshToken(user.getEmail(), sid);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefresh)
                .expiresIn(jwtUtil.getExpirationMs() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .profileCompleted(user.isProfileCompleted())
                .emailVerified(user.isEmailVerified())
                .build();
    }

    @Transactional
    public MessageResponse confirmFirstLoginKnowPassword(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!Boolean.TRUE.equals(user.getFirstLogin()) || user.getCreatedById() == null) {
            throw new BadRequestException("Not applicable for this account");
        }
        user.setFirstLogin(false);
        userRepository.save(user);
        auditService.log(userId, "FIRST_LOGIN_CONFIRMED", "User", userId, null, null, null, null);
        return MessageResponse.of("Welcome! You can proceed to the dashboard.");
    }

    @Transactional
    public MessageResponse setPasswordFirstLogin(Long userId, String newPassword, String confirmPassword) {
        PasswordPolicy.validate(newPassword);
        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("Passwords do not match");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!Boolean.TRUE.equals(user.getFirstLogin()) || user.getCreatedById() == null) {
            throw new BadRequestException("Not applicable for this account");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);
        user.setAuthSessionId(null);
        userRepository.save(user);
        auditService.log(userId, "FIRST_LOGIN_PASSWORD_SET", "User", userId, null, null, null, null);
        return MessageResponse.of("Password set successfully. You can proceed to the dashboard.");
    }

    @Transactional
    public MessageResponse resendOtp(String email, OtpVerification.OtpType type) {
        String normalized = email != null ? email.trim().toLowerCase() : "";
        User user = userRepository.findByEmail(normalized).orElseThrow(() -> new BadRequestException("User not found."));
        String otp = otpService.generateAndSave(normalized, type);
        if (!otpDevMode) {
            emailService.sendOtpEmail(normalized, otp, type.name().toLowerCase());
        }
        return MessageResponse.of("OTP sent to your email.");
    }
}
