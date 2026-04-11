package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenAndUsedFalseAndExpiresAtAfter(String token, Instant now);

    @Modifying
    void deleteByUser_Id(Long userId);

    @Modifying
    void deleteByExpiresAtBefore(Instant instant);
}
