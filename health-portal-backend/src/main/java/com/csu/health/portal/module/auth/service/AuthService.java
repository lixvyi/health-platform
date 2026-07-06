package com.csu.health.portal.module.auth.service;

import com.csu.health.portal.common.BusinessException;
import com.csu.health.portal.module.auth.dto.LoginRequest;
import com.csu.health.portal.module.auth.dto.LoginResponse;
import com.csu.health.portal.module.auth.entity.SysUser;
import com.csu.health.portal.module.auth.mapper.SysUserMapper;
import com.csu.health.portal.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.findByUsername(request.getUsername());
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException("用户不存在或已禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .build();
    }
}
