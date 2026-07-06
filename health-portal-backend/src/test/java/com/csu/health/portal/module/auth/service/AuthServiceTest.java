package com.csu.health.portal.module.auth.service;

import com.csu.health.portal.common.BusinessException;
import com.csu.health.portal.module.auth.dto.LoginRequest;
import com.csu.health.portal.module.auth.entity.SysUser;
import com.csu.health.portal.module.auth.mapper.SysUserMapper;
import com.csu.health.portal.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @InjectMocks
    private AuthService authService;

    @Test
    void loginSuccess() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("enc");
        user.setRole("ADMIN");
        user.setStatus(1);
        user.setRealName("管理员");

        when(sysUserMapper.findByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("Admin@123", "enc")).thenReturn(true);
        when(jwtTokenProvider.generateToken(1L, "admin", "ADMIN")).thenReturn("token");

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("Admin@123");

        var resp = authService.login(req);
        assertEquals("token", resp.getToken());
        assertEquals("admin", resp.getUsername());
    }

    @Test
    void loginFailWrongPassword() {
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setPassword("enc");
        user.setStatus(1);
        when(sysUserMapper.findByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");

        assertThrows(BusinessException.class, () -> authService.login(req));
    }
}
