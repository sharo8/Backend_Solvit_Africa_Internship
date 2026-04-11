package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findTopByEmailAndOtpTypeAndVerifiedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            String email, OtpVerification.OtpType otpType, Instant now);

    Optional<OtpVerification> findTopByEmailAndOtpAndOtpTypeAndVerifiedFalseAndExpiresAtAfter(
            String email, String otp, OtpVerification.OtpType otpType, Instant now);

    @Modifying
    @Query("DELETE FROM OtpVerification o WHERE o.email = ?1 AND o.otpType = ?2")
    void deleteByEmailAndOtpType(String email, OtpVerification.OtpType otpType);
}
