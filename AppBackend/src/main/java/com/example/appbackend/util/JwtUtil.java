package com.example.appbackend.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import com.example.appbackend.service.SystemConfigService;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String DEFAULT_SECRET = "smart-campus-jwt-secret-key-please-change-this-seed-value";
    private static final long DEFAULT_EXPIRATION = 86400000L;

    private final SystemConfigService systemConfigService;

    public JwtUtil(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    private SecretKey getSigningKey() {
        String secret = systemConfigService.getValue("jwt.secret", DEFAULT_SECRET);
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, Long userId, String role) {
        Date now = new Date();

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(new Date(System.currentTimeMillis() + systemConfigService.getLongValue("jwt.expiration", DEFAULT_EXPIRATION)))
                .signWith(getSigningKey())
                .compact();
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("role", String.class);
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("userId", Long.class);
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
