package com.csu.health.portal.module.portaluser.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.csu.health.portal.common.BusinessException;
import com.csu.health.portal.module.portaluser.dto.ReviewRequest;
import com.csu.health.portal.module.portaluser.entity.*;
import com.csu.health.portal.module.portaluser.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortalAdminService {

    private final PortalUserMapper portalUserMapper;
    private final PortalDataApplyMapper dataApplyMapper;
    private final PortalApiApplyMapper apiApplyMapper;
    private final PortalDataResourceMapper dataResourceMapper;
    private final PortalApiServiceMapper apiServiceMapper;

    public List<Map<String, Object>> listCertifications(String status) {
        LambdaQueryWrapper<PortalUser> q = new LambdaQueryWrapper<PortalUser>()
                .ne(PortalUser::getCertifyStatus, "NONE")
                .orderByDesc(PortalUser::getUpdatedAt);
        if (status != null && !status.isBlank()) {
            q.eq(PortalUser::getCertifyStatus, status);
        }
        return portalUserMapper.selectList(q).stream().map(this::toCertVo).toList();
    }

    public void reviewCertification(Long userId, ReviewRequest request, Long adminId) {
        PortalUser user = portalUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!"PENDING".equals(user.getCertifyStatus())) {
            throw new BusinessException("当前状态不可审核");
        }
        if (request.isApproved()) {
            user.setCertifyStatus("APPROVED");
            user.setRole("RESEARCHER");
            user.setCertifiedAt(LocalDateTime.now());
            user.setCertifyRemark(request.getRemark());
        } else {
            user.setCertifyStatus("REJECTED");
            user.setCertifyRemark(request.getRemark() != null ? request.getRemark() : "未通过审核");
        }
        user.setUpdatedAt(LocalDateTime.now());
        portalUserMapper.updateById(user);
    }

    public List<Map<String, Object>> listDataApplies(String status) {
        LambdaQueryWrapper<PortalDataApply> q = new LambdaQueryWrapper<PortalDataApply>()
                .orderByDesc(PortalDataApply::getCreatedAt);
        if (status != null && !status.isBlank()) {
            q.eq(PortalDataApply::getStatus, status);
        }
        return dataApplyMapper.selectList(q).stream().map(this::toDataApplyVo).toList();
    }

    public void reviewDataApply(Long id, ReviewRequest request, Long adminId) {
        PortalDataApply apply = dataApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("申请不存在");
        }
        if (!"PENDING".equals(apply.getStatus())) {
            throw new BusinessException("该申请已处理");
        }
        apply.setStatus(request.isApproved() ? "APPROVED" : "REJECTED");
        apply.setReviewRemark(request.getRemark());
        apply.setReviewedBy(adminId);
        apply.setReviewedAt(LocalDateTime.now());
        dataApplyMapper.updateById(apply);
    }

    public List<Map<String, Object>> listApiApplies(String status) {
        LambdaQueryWrapper<PortalApiApply> q = new LambdaQueryWrapper<PortalApiApply>()
                .orderByDesc(PortalApiApply::getCreatedAt);
        if (status != null && !status.isBlank()) {
            q.eq(PortalApiApply::getStatus, status);
        }
        return apiApplyMapper.selectList(q).stream().map(this::toApiApplyVo).toList();
    }

    public void reviewApiApply(Long id, ReviewRequest request, Long adminId) {
        PortalApiApply apply = apiApplyMapper.selectById(id);
        if (apply == null) {
            throw new BusinessException("申请不存在");
        }
        if (!"PENDING".equals(apply.getStatus())) {
            throw new BusinessException("该申请已处理");
        }
        apply.setStatus(request.isApproved() ? "APPROVED" : "REJECTED");
        apply.setReviewRemark(request.getRemark());
        apply.setReviewedBy(adminId);
        apply.setReviewedAt(LocalDateTime.now());
        apiApplyMapper.updateById(apply);
    }

    private Map<String, Object> toCertVo(PortalUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getId());
        m.put("username", u.getUsername());
        m.put("realName", u.getRealName());
        m.put("organization", u.getOrganization());
        m.put("orgType", u.getOrgType());
        m.put("researchDirection", u.getResearchDirection());
        m.put("certifyReason", u.getCertifyReason());
        m.put("certifyStatus", u.getCertifyStatus());
        m.put("certifyRemark", u.getCertifyRemark());
        m.put("updatedAt", u.getUpdatedAt());
        return m;
    }

    private Map<String, Object> toDataApplyVo(PortalDataApply a) {
        PortalUser user = portalUserMapper.selectById(a.getUserId());
        PortalDataResource resource = dataResourceMapper.selectById(a.getResourceId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("username", user != null ? user.getUsername() : "");
        m.put("realName", user != null ? user.getRealName() : "");
        m.put("organization", user != null ? user.getOrganization() : "");
        m.put("resourceName", resource != null ? resource.getName() : "");
        m.put("projectName", a.getProjectName());
        m.put("purpose", a.getPurpose());
        m.put("reason", a.getReason());
        m.put("status", a.getStatus());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    private Map<String, Object> toApiApplyVo(PortalApiApply a) {
        PortalUser user = portalUserMapper.selectById(a.getUserId());
        PortalApiService api = apiServiceMapper.selectById(a.getApiId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("username", user != null ? user.getUsername() : "");
        m.put("realName", user != null ? user.getRealName() : "");
        m.put("organization", user != null ? user.getOrganization() : "");
        m.put("apiName", api != null ? api.getName() : "");
        m.put("projectName", a.getProjectName());
        m.put("purpose", a.getPurpose());
        m.put("reason", a.getReason());
        m.put("status", a.getStatus());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }
}
