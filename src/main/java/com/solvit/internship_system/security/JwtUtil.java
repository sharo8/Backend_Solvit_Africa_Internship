package com.solvit.internship_system.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh.expiration}")
    private long refreshExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * @param role enum name only (e.g. {@code ADMIN}), never a Spring Security {@code ROLE_*} prefix
     */
    public String generateAccessToken(String email, Long userId, String role, String sessionId) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("type", "access")
                .claim("sid", sessionId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String email, String sessionId) {
        return Jwts.builder()
                .subject(email)
                .claim("type", "refresh")
                .claim("sid", sessionId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Object uid = parseClaims(token).get("userId");
        if (uid == null) {
            throw new JwtException("JWT missing userId claim");
        }
        if (uid instanceof Number n) {
            return n.longValue();
        }
        if (uid instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                throw new JwtException("JWT userId claim is not a valid number");
            }
        }
        throw new JwtException("JWT userId claim has unsupported type: " + uid.getClass().getName());
    }

    /** Role name as stored in JWT (e.g. ADMIN); strips a leading {@code ROLE_} if present. */
    public String getRoleFromToken(String token) {
        Object r = parseClaims(token).get("role");
        if (r == null) {
            return null;
        }
        String s = r.toString().trim();
        if (s.length() > 5 && s.regionMatches(true, 0, "ROLE_", 0, 5)) {
            return s.substring(5);
        }
        return s;
    }

    public String getTokenType(String token) {
        Object t = parseClaims(token).get("type");
        return t != null ? t.toString() : null;
    }

    public String getSessionIdFromToken(String token) {
        Object s = parseClaims(token).get("sid");
        return s != null ? s.toString() : null;
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            String email = getEmailFromToken(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            Date exp = parseClaims(token).getExpiration();
            return exp.before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    public long getExpirationMs() {
        return expiration;
    }
}
