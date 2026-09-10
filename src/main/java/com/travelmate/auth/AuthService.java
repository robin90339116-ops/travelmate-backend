package com.travelmate.auth;

import com.travelmate.auth.AuthDtos.*;
import com.travelmate.common.ApiException;
import com.travelmate.common.JwtUtil;
import com.travelmate.domain.DeviceSession;
import com.travelmate.domain.User;
import com.travelmate.repository.DeviceSessionRepository;
import com.travelmate.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final DeviceSessionRepository sessionRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.sms.dev-code:246810}")
    private String devCode;

    @Value("${app.sms.dev-enabled:true}")
    private boolean devEnabled;

    private void requireDevLogin() {
        if (!devEnabled) throw ApiException.serviceUnavailable("短信服务尚未配置，开发验证码登录已禁用");
    }

    static String digest(String token) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public SmsCodeResponse requestSmsCode(SmsCodeRequest request) {
        requireDevLogin();
        // 生产环境应调用阿里云 SMS OpenAPI 下发随机验证码;开发态返回固定 devCode。
        return new SmsCodeResponse(request.phone(), devCode, "开发环境验证码,生产将通过短信下发");
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        requireDevLogin();
        if (!devCode.equals(request.code())) {
            throw ApiException.badRequest("验证码错误");
        }
        User user = userRepository.findByPhone(request.phone()).orElseGet(() -> {
            User created = new User();
            created.setPhone(request.phone());
            created.setDisplayName("旅行者" + request.phone().substring(Math.max(0, request.phone().length() - 4)));
            return userRepository.save(created);
        });

        return createSession(user, request.deviceName());
    }

    @Transactional
    public TokenResponse register(PasswordRequest request) {
        if (userRepository.findByPhone(request.phone()).isPresent())
            throw ApiException.badRequest("该账号无法注册");
        User user=new User();user.setPhone(request.phone());user.setDisplayName("旅行者");
        user.setPasswordHash(passwordEncoder.encode(digest(request.password())));
        return createSession(userRepository.save(user),request.deviceName());
    }

    @Transactional
    public TokenResponse passwordLogin(PasswordRequest request) {
        User user=userRepository.findByPhone(request.phone()).orElseThrow(()->ApiException.unauthorized("账号或密码错误"));
        if(user.getPasswordHash()==null || !passwordEncoder.matches(digest(request.password()),user.getPasswordHash()))
            throw ApiException.unauthorized("账号或密码错误");
        return createSession(user,request.deviceName());
    }

    private TokenResponse createSession(User user,String deviceName) {
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = jwtUtil.issueRefreshToken(user.getId(), user.getPhone(), sessionId);

        DeviceSession session = new DeviceSession();
        session.setSessionId(sessionId);
        session.setUserId(user.getId());
        session.setDeviceName(deviceName == null ? "未知设备" : deviceName);
        session.setRefreshTokenHash(digest(refreshToken));
        sessionRepository.save(session);

        String accessToken = jwtUtil.issueAccessToken(user.getId(), user.getPhone(), sessionId);
        return new TokenResponse(accessToken, refreshToken, jwtUtil.getAccessTtlSeconds(), sessionId, toView(user));
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = parseRefresh(request.refreshToken());
        String sessionId = claims.get("sid", String.class);
        Long userId = Long.valueOf(claims.getSubject());

        DeviceSession session = sessionRepository.lockBySessionId(sessionId)
                .orElseThrow(() -> ApiException.unauthorized("会话不存在或已失效"));
        if (!session.isActive() || session.getRefreshTokenHash() == null
                || !java.security.MessageDigest.isEqual(digest(request.refreshToken()).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    session.getRefreshTokenHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw ApiException.unauthorized("refresh token 已失效,请重新登录");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("用户不存在"));

        // 轮换:签发新 refresh 并更新哈希,旧 refresh 立即失效。
        String newRefresh = jwtUtil.issueRefreshToken(userId, user.getPhone(), sessionId);
        session.setRefreshTokenHash(digest(newRefresh));
        session.setLastActiveAt(Instant.now());
        sessionRepository.save(session);

        String accessToken = jwtUtil.issueAccessToken(userId, user.getPhone(), sessionId);
        return new TokenResponse(accessToken, newRefresh, jwtUtil.getAccessTtlSeconds(), sessionId, toView(user));
    }

    @Transactional
    public void logout(Long userId, LogoutRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            revokeSession(userId, com.travelmate.common.CurrentUser.sessionId());
            return;
        }
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            Claims claims = parseRefresh(request.refreshToken());
            revokeSession(userId, claims.get("sid", String.class));
        }
    }

    @Transactional
    public void revokeSession(Long userId, String sessionId) {
        DeviceSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> ApiException.notFound("会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw ApiException.forbidden("无权操作该会话");
        }
        session.setActive(false);
        session.setRefreshTokenHash(null);
        sessionRepository.save(session);
    }

    @Transactional
    public void logoutAll(Long userId) {
        List<DeviceSession> sessions = sessionRepository.findByUserIdAndActiveTrue(userId);
        for (DeviceSession session : sessions) {
            session.setActive(false);
            session.setRefreshTokenHash(null);
        }
        sessionRepository.saveAll(sessions);
    }

    public SessionListResponse listSessions(Long userId, String currentSessionId) {
        List<SessionView> views = sessionRepository.findByUserIdOrderByLastActiveAtDesc(userId).stream()
                .map(s -> new SessionView(
                        s.getSessionId(), s.getDeviceName(), s.isActive(),
                        s.getSessionId().equals(currentSessionId), s.getCreatedAt(), s.getLastActiveAt()))
                .toList();
        return new SessionListResponse(views);
    }

    private Claims parseRefresh(String token) {
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (Exception e) {
            throw ApiException.unauthorized("refresh token 无效");
        }
        if (!"refresh".equals(claims.get("type", String.class)) || claims.get("sid", String.class) == null) {
            throw ApiException.unauthorized("refresh token 类型错误");
        }
        return claims;
    }

    private UserView toView(User user) {
        return new UserView(user.getId(), user.getPhone(), user.getDisplayName());
    }
}
