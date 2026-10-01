package com.raghu.pilliongo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiry}")
    private Long expiry;

    // How long a password-reset token stays valid. Deliberately much
    // shorter than the login token's expiry, and read from its own
    // property (with a 15-minute fallback) so it can be tuned separately.
    @Value("${jwt.reset-expiry:900000}")
    private Long resetExpiry;

    public static final String PURPOSE_LOGIN = "login";
    public static final String PURPOSE_PASSWORD_RESET = "password-reset";

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim("purpose", PURPOSE_LOGIN)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiry))
                .signWith(getKey())
                .compact();
    }

    // Short-lived token proving the user just verified their password-reset
    // OTP. Has no "role" claim on purpose — it's not a login credential —
    // and carries purpose=password-reset so it's unmistakably a different
    // kind of token from generateToken()'s output.
    public String generateResetToken(String email) {
        return Jwts.builder()
                .subject(email)
                .claim("purpose", PURPOSE_PASSWORD_RESET)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + resetExpiry))
                .signWith(getKey())
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public String extractRole(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public String extractPurpose(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("purpose", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    // Used only by /api/auth/reset-password. Confirms the token is
    // cryptographically valid AND is specifically a password-reset token
    // (purpose=password-reset) issued for this exact email — a login JWT
    // (purpose=login, or an older token with no purpose claim at all)
    // fails this check and gets rejected, same as an expired or tampered
    // token would.
    public boolean validateResetToken(String token, String email) {
        if (!validateToken(token)) return false;
        try {
            String purpose = extractPurpose(token);
            String subject = extractEmail(token);
            return PURPOSE_PASSWORD_RESET.equals(purpose)
                    && email != null
                    && email.equalsIgnoreCase(subject);
        } catch (JwtException e) {
            return false;
        }
    }
}