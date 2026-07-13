package com.csu.health.portal.security;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthPrincipal {
    private Long userId;
    private String username;
    private String role;
    /** ADMIN 管理后台账号；PORTAL 门户公众用户 */
    private String userType;

    public boolean isPortal() {
        return "PORTAL".equals(userType);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(userType);
    }

    public boolean isResearcher() {
        return "RESEARCHER".equals(role);
    }
}
