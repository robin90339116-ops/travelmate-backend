package com.travelmate.auth;

import com.travelmate.auth.AuthDtos.*;
import com.travelmate.common.CurrentUser;
import com.travelmate.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/sms/code")
    public Result<SmsCodeResponse> smsCode(@Valid @RequestBody SmsCodeRequest request) {
        return Result.ok(authService.requestSmsCode(request));
    }

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return Result.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(CurrentUser.id(), request);
        return Result.ok();
    }

    @PostMapping("/logout-all")
    public Result<Void> logoutAll() {
        authService.logoutAll(CurrentUser.id());
        return Result.ok();
    }

    @GetMapping("/sessions")
    public Result<SessionListResponse> sessions() {
        return Result.ok(authService.listSessions(CurrentUser.id(), null));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> revoke(@PathVariable String sessionId) {
        authService.revokeSession(CurrentUser.id(), sessionId);
        return Result.ok();
    }
}
