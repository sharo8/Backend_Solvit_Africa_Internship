package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.attendance.dynamicqr.QrTokenClaims;
import com.solvit.internship_system.exception.BadRequestException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.solvit.internship_system.service.qr.QrRedisSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * Rotating JWT QR for interns (anti-replay via Redis). Independent from {@link QrAttendanceService} (daily office HMAC).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QrTokenService {

    @Value("${qr.secret:SOLVIT_QR_SECRET_CHANGE_IN_PRODUCTION_2026}")
    private String qrSecret;

    @Value("${qr.validity-minutes:5}")
    private int validityMinutes;

    private final QrRedisSupport qrRedisSupport;

    private SecretKey signingKey() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(qrSecret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String generateQrToken(Long internId) {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        Instant exp = Instant.now().plus(validityMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .claim("internId", internId)
                .claim("nonce", nonce)
                .claim("typ", "ATTENDANCE")
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }

    public QrTokenClaims validateAndConsume(String token) {
        if (token == null || token.isBlank()) {
            throw new BadRequestException("Missing QR token");
        }
        try {
            Claims c = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String nonce = c.get("nonce", String.class);
            String typ = c.get("typ", String.class);
            Object internRaw = c.get("internId");
            Long internId = internRaw instanceof Number ? ((Number) internRaw).longValue() : null;
            if (!"ATTENDANCE".equals(typ) || nonce == null || internId == null) {
                throw new BadRequestException("Invalid QR payload");
            }
            String redisKey = "qr:nonce:" + nonce;
            if (qrRedisSupport.hasKey(redisKey)) {
                throw new BadRequestException("QR code already used — replay prevented");
            }
            qrRedisSupport.set(redisKey, "1", Duration.ofMinutes(Math.max(validityMinutes * 2L, 10)));
            long expSec = c.getExpiration() != null ? c.getExpiration().getTime() / 1000 : Instant.now().getEpochSecond();
            log.info("[QrToken] Valid scan nonce internId={} nonce={}", internId, nonce);
            return new QrTokenClaims(internId, nonce, typ, expSec);
        } catch (ExpiredJwtException e) {
            throw new BadRequestException("QR code expired — intern must refresh");
        } catch (JwtException e) {
            throw new BadRequestException("Invalid QR signature");
        }
    }
}
