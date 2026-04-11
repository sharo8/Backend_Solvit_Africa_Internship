package com.solvit.internship_system.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;

/**
 * HMAC-based daily QR token so on-site presence can be proven without storing per-row secrets.
 */
@Service
public class QrAttendanceService {

    private final String secret;

    public QrAttendanceService(@Value("${jwt.secret}") String secret) {
        this.secret = secret;
    }

    public String generateTokenForDate(LocalDate date) {
        byte[] sig = sign(date);
        String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (date + "|" + sigB64).getBytes(StandardCharsets.UTF_8));
    }

    public boolean isValidToken(LocalDate expectedDate, String token) {
        if (token == null || token.isBlank() || expectedDate == null) return false;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int pipe = decoded.indexOf('|');
            if (pipe < 0) return false;
            String datePart = decoded.substring(0, pipe);
            String sigPart = decoded.substring(pipe + 1);
            if (!expectedDate.toString().equals(datePart)) return false;
            byte[] expectedSig = sign(expectedDate);
            String expectedB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSig);
            return constantTimeEquals(sigPart, expectedB64);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] sign(LocalDate date) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return m.doFinal(date.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC init failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
