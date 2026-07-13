package com.csu.health.portal.security;

import com.csu.health.portal.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BusinessException(401, "请先登录");
        }
        return principal;
    }

    public static AuthPrincipal requirePortalUser() {
        AuthPrincipal p = currentPrincipal();
        if (!p.isPortal()) {
            throw new BusinessException(403, "请使用门户账号登录");
        }
        return p;
    }

    public static AuthPrincipal requireAdmin() {
        AuthPrincipal p = currentPrincipal();
        if (!p.isAdmin()) {
            throw new BusinessException(403, "需要管理员权限");
        }
        return p;
    }
}
