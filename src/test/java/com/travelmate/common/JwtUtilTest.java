package com.travelmate.common;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "travelmate-unit-test-secret-please-32bytes-long", 7200, 2592000);

    @Test
    void accessTokenCarriesUserAndType() {
        String token = jwtUtil.issueAccessToken(42L, "13800000000");
        Claims claims = jwtUtil.parse(token);
        assertEquals("42", claims.getSubject());
        assertEquals("access", claims.get("type", String.class));
        assertEquals("13800000000", claims.get("username", String.class));
    }

    @Test
    void refreshTokenBindsSession() {
        String token = jwtUtil.issueRefreshToken(7L, "13900000000", "sess-1");
        Claims claims = jwtUtil.parse(token);
        assertEquals("refresh", claims.get("type", String.class));
        assertEquals("sess-1", claims.get("sid", String.class));
    }

    @Test
    void tamperedTokenRejected() {
        String token = jwtUtil.issueAccessToken(1L, "u");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThrows(Exception.class, () -> jwtUtil.parse(tampered));
    }
}
