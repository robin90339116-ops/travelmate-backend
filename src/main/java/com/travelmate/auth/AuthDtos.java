package com.travelmate.auth;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

/**
 * 认证相关 DTO 集合。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SmsCodeRequest(@NotBlank(message = "手机号不能为空") String phone) {
    }

    public record SmsCodeResponse(String phone, String devCode, String message) {
    }

    public record LoginRequest(
            @NotBlank(message = "手机号不能为空") String phone,
            @NotBlank(message = "验证码不能为空") String code,
            String deviceName) {
    }

    public record RefreshRequest(@NotBlank(message = "refreshToken 不能为空") String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record UserView(Long id, String phone, String displayName) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long accessTtlSeconds,
            String sessionId,
            UserView user) {
    }

    public record SessionView(
            String sessionId,
            String deviceName,
            boolean active,
            boolean current,
            Instant createdAt,
            Instant lastActiveAt) {
    }

    public record SessionListResponse(List<SessionView> sessions) {
    }
}
