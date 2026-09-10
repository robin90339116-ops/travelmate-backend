package com.travelmate.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * HMAC JWT 签发与校验。access / refresh 双令牌。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public JwtUtil(
            @Value("${app.jwt.secret:travelmate-dev-secret-change-me-please-32bytes}") String secret,
            @Value("${app.jwt.access-ttl-seconds:7200}") long accessTtlSeconds,
            @Value("${app.jwt.refresh-ttl-seconds:2592000}") long refreshTtlSeconds) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 要求至少 256bit 密钥,不足时右补零,保证开发环境可用。
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String issueAccessToken(Long userId, String username) {
        return build(userId, username, "access", accessTtlSeconds, null);
    }

    public String issueAccessToken(Long userId, String username, String sessionId) {
        return build(userId, username, "access", accessTtlSeconds, sessionId);
    }

    /** refresh token 绑定 sessionId,用于轮换与撤销。 */
    public String issueRefreshToken(Long userId, String username, String sessionId) {
        return build(userId, username, "refresh", refreshTtlSeconds, sessionId);
    }

    private String build(Long userId, String username, String type, long ttlSeconds, String sessionId) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", type)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000));
        if (sessionId != null) {
            builder.claim("sid", sessionId);
        }
        return builder.signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlSeconds;
    }
}
