package com.travelmate.auth;
import com.travelmate.common.ApiException;
import com.travelmate.common.JwtUtil;
import com.travelmate.repository.DeviceSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

@Service
@RequiredArgsConstructor
public class SessionAuthenticator {
    private final JwtUtil jwt;
    private final DeviceSessionRepository sessions;
    public UsernamePasswordAuthenticationToken authenticate(String token) {
        try {
            var c = jwt.parse(token);
            if (!"access".equals(c.get("type", String.class))) throw new IllegalArgumentException();
            String sid = c.get("sid", String.class);
            var s = sessions.findBySessionId(sid).orElseThrow();
            Long uid = Long.valueOf(c.getSubject());
            if (!s.isActive() || !s.getUserId().equals(uid)) throw new IllegalArgumentException();
            var auth = new UsernamePasswordAuthenticationToken(uid, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
            auth.setDetails(sid);
            return auth;
        } catch (Exception e) { throw ApiException.unauthorized("会话已失效，请重新登录"); }
    }
}
