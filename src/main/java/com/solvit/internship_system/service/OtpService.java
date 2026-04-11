package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.OtpVerification;
import com.solvit.internship_system.repository.OtpVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpVerificationRepository otpRepository;

    @Value("${otp.expiration.minutes:10}")
    private int expirationMinutes;

    @Value("${otp.length:6}")
    private int otpLength;

    @Value("${otp.dev.mode:false}")
    private boolean devMode;

    private static final String DIGITS = "0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public String generateAndSave(String email, OtpVerification.OtpType type) {
        String otp = devMode ? "123456" : generateOtp();
        Instant expiresAt = Instant.now().plusSeconds(expirationMinutes * 60L);
        otpRepository.deleteByEmailAndOtpType(email, type);
        OtpVerification entity = OtpVerification.builder()
                .email(email)
                .otp(otp)
                .otpType(type)
                .expiresAt(expiresAt)
                .verified(false)
                .build();
        otpRepository.save(entity);
        return otp;
    }

    public Optional<OtpVerification> findValidOtp(String email, String otp, OtpVerification.OtpType type) {
        return otpRepository.findTopByEmailAndOtpAndOtpTypeAndVerifiedFalseAndExpiresAtAfter(
                email, otp, type, Instant.now());
    }

    @Transactional
    public boolean verify(String email, String otp, OtpVerification.OtpType type) {
        Optional<OtpVerification> opt = findValidOtp(email, otp, type);
        if (opt.isEmpty()) return false;
        OtpVerification o = opt.get();
        o.setVerified(true);
        otpRepository.save(o);
        return true;
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(otpLength);
        for (int i = 0; i < otpLength; i++) {
            sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        }
        return sb.toString();
    }
}
