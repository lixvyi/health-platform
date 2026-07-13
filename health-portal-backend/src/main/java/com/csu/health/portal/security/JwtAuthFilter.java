package com.csu.health.portal.security;

import com.csu.health.portal.module.auth.entity.SysUser;
import com.csu.health.portal.module.auth.mapper.SysUserMapper;
import com.csu.health.portal.module.portaluser.entity.PortalUser;
import com.csu.health.portal.module.portaluser.mapper.PortalUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final SysUserMapper sysUserMapper;
    private final PortalUserMapper portalUserMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                var claims = jwtTokenProvider.parse(token);
                String username = claims.getSubject();
                String userType = claims.get("userType", String.class);
                if (userType == null) {
                    userType = "ADMIN";
                }
                String role = claims.get("role", String.class);

                if ("PORTAL".equals(userType)) {
                    PortalUser user = portalUserMapper.findByUsername(username);
                    if (user != null && user.getStatus() == 1) {
                        setAuth(user.getId(), user.getUsername(), user.getRole(), "PORTAL");
                    }
                } else {
                    SysUser user = sysUserMapper.findByUsername(username);
                    if (user != null && user.getStatus() == 1) {
                        setAuth(user.getId(), user.getUsername(), user.getRole(), "ADMIN");
                    }
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private void setAuth(Long userId, String username, String role, String userType) {
        AuthPrincipal principal = new AuthPrincipal(userId, username, role, userType);
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
