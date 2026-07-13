package com.csu.health.portal.module.portaluser.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.csu.health.portal.common.BusinessException;
import com.csu.health.portal.module.portaluser.dto.*;
import com.csu.health.portal.module.portaluser.entity.PortalUser;
import com.csu.health.portal.module.portaluser.mapper.PortalUserMapper;
import com.csu.health.portal.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PortalAuthService {

    private final PortalUserMapper portalUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public PortalAuthResponse register(PortalRegisterRequest request) {
        if (portalUserMapper.findByUsername(request.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        PortalUser user = new PortalUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRealName(request.getRealName());
        user.setRole("USER");
        user.setStatus(1);
        user.setCertifyStatus("NONE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        portalUserMapper.insert(user);
        return buildResponse(user);
    }

    public PortalAuthResponse login(PortalLoginRequest request) {
        PortalUser user = portalUserMapper.findByUsername(request.getUsername());
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException("用户不存在或已禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        return buildResponse(user);
    }

    public PortalAuthResponse me(Long userId) {
        PortalUser user = portalUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toProfile(user);
    }

    public void submitCertify(Long userId, CertifyRequest request) {
        PortalUser user = portalUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if ("PENDING".equals(user.getCertifyStatus())) {
            throw new BusinessException("认证申请审核中，请耐心等待");
        }
        if ("APPROVED".equals(user.getCertifyStatus())) {
            throw new BusinessException("您已通过科研人员认证");
        }
        user.setRealName(request.getRealName());
        user.setOrganization(request.getOrganization());
        user.setOrgType(request.getOrgType());
        user.setResearchDirection(request.getResearchDirection());
        user.setCertifyReason(request.getCertifyReason());
        user.setCertifyStatus("PENDING");
        user.setCertifyRemark(null);
        user.setUpdatedAt(LocalDateTime.now());
        portalUserMapper.updateById(user);
    }

    private PortalAuthResponse buildResponse(PortalUser user) {
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole(), "PORTAL");
        return PortalAuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .certifyStatus(user.getCertifyStatus())
                .organization(user.getOrganization())
                .build();
    }

    private PortalAuthResponse toProfile(PortalUser user) {
        return PortalAuthResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .certifyStatus(user.getCertifyStatus())
                .organization(user.getOrganization())
                .build();
    }
}
