package com.travelmate.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 读取当前登录用户 ID。
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof Long)) {
            throw ApiException.unauthorized("未登录或登录已过期");
        }
        return (Long) auth.getPrincipal();
    }

    public static Long idOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    public static String sessionId() {
        id();
        return (String) SecurityContextHolder.getContext().getAuthentication().getDetails();
    }
}
