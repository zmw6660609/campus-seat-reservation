package com.sr.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    @Value("${sr.jwt.secret}")
    private String secret;

    @Value("${sr.jwt.expire-minutes}")
    private Long expireMinutes;

    private SecretKey getSecretKey()
    {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId) {
        long ttl = expireMinutes * 60 * 1000;
        Date now = new Date();
        Date expireTime = new Date(now.getTime() + ttl);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expireTime)
                .signWith(getSecretKey())
                .compact();
    }
    public Long parseUserId(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token);

        Claims payload = jws.getPayload();
        return Long.valueOf(payload.getSubject());
    }
}
