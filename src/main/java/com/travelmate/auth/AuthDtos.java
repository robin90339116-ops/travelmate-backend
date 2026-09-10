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

    public record SmsCodeRequest(@NotBlank(message = "手机号不能为空") @jakarta.validation.constraints.Pattern(regexp="\\+?[0-9]{7,15}") String phone) {
    }

    public record SmsCodeResponse(String phone, String devCode, String message) {
    }

    public record LoginRequest(
            @NotBlank(message = "手机号不能为空") @jakarta.validation.constraints.Pattern(regexp="\\+?[0-9]{7,15}") String phone,
            @NotBlank(message = "验证码不能为空") String code,
            @jakarta.validation.constraints.Size(max=64) String deviceName) {
    }

    public record RefreshRequest(@NotBlank(message = "refreshToken 不能为空") String refreshToken) {
    }

    public record PasswordRequest(@NotBlank @jakarta.validation.constraints.Pattern(regexp="\\+?[0-9]{7,15}") String phone,
            @NotBlank @jakarta.validation.constraints.Size(min=12,max=128) String password,
            @jakarta.validation.constraints.Size(max=64) String deviceName) {}

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
