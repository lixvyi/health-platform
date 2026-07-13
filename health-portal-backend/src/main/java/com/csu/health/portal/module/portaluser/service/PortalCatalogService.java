package com.csu.health.portal.module.portaluser.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.csu.health.portal.common.BusinessException;
import com.csu.health.portal.module.content.entity.CmsContent;
import com.csu.health.portal.module.content.mapper.CmsContentMapper;
import com.csu.health.portal.module.opendata.service.OpenDataExportService;
import com.csu.health.portal.module.portaluser.entity.*;
import com.csu.health.portal.module.portaluser.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortalCatalogService {

    private final PortalDataResourceMapper dataResourceMapper;
    private final PortalApiServiceMapper apiServiceMapper;
    private final PortalUserMapper portalUserMapper;
    private final CmsContentMapper cmsContentMapper;
    private final OpenDataExportService openDataExportService;
    private final ObjectMapper objectMapper;

    public List<PortalDataResource> listResources() {
        return dataResourceMapper.selectList(new LambdaQueryWrapper<PortalDataResource>()
                .eq(PortalDataResource::getStatus, 1)
                .orderByAsc(PortalDataResource::getSortOrder));
    }

    public PortalDataResource getResource(Long id) {
        PortalDataResource r = dataResourceMapper.selectById(id);
        if (r == null || r.getStatus() != 1) {
            throw new BusinessException("资源不存在");
        }
        return r;
    }

    public List<PortalApiService> listApis() {
        return apiServiceMapper.selectList(new LambdaQueryWrapper<PortalApiService>()
                .eq(PortalApiService::getStatus, 1)
                .orderByAsc(PortalApiService::getSortOrder));
    }

    public PortalApiService getApi(Long id) {
        PortalApiService api = apiServiceMapper.selectById(id);
        if (api == null || api.getStatus() != 1) {
            throw new BusinessException("API 不存在");
        }
        return api;
    }

    public Map<String, Object> myCertificationProfile(Long userId) {
        PortalUser user = requireUser(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", user.getUsername());
        m.put("realName", user.getRealName());
        m.put("role", user.getRole());
        m.put("certifyStatus", user.getCertifyStatus());
        m.put("organization", user.getOrganization());
        m.put("orgType", user.getOrgType());
        m.put("researchDirection", user.getResearchDirection());
        m.put("certifyReason", user.getCertifyReason());
        m.put("certifyRemark", user.getCertifyRemark());
        m.put("certifiedAt", user.getCertifiedAt());
        m.put("accessRights", buildAccessRights(user));
        return m;
    }

    public OpenDataExportService.ExportPayload exportResourceFile(Long userId, Long resourceId) {
        PortalUser user = requireUser(userId);
        PortalDataResource resource = getResource(resourceId);
        checkDataDownloadPermission(user, resource.getPermissionLevel());
        if (resource.getOpenDataId() == null || resource.getOpenDataId().isBlank()) {
            throw new BusinessException("该资源未关联数据文件");
        }
        return openDataExportService.export(resource.getOpenDataId());
    }

    public OpenDataExportService.ExportPayload exportPolicyFile(Long userId, Long contentId) {
        PortalUser user = requireUser(userId);
        checkDataDownloadPermission(user, "STANDARD");
        CmsContent content = cmsContentMapper.selectById(contentId);
        if (content == null) {
            throw new BusinessException("政策内容不存在");
        }
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>"
                + escapeHtml(content.getTitle()) + "</title></head><body>"
                + "<h1>" + escapeHtml(content.getTitle()) + "</h1>"
                + (content.getPublishTime() != null ? "<p>发布时间：" + content.getPublishTime() + "</p>" : "")
                + (content.getContent() != null ? content.getContent() : "")
                + "</body></html>";
        String filename = sanitizeFilename(content.getTitle()) + ".html";
        return new OpenDataExportService.ExportPayload(
                html.getBytes(StandardCharsets.UTF_8), filename, "text/html");
    }

    public Map<String, Object> invokeApi(Long userId, Long apiId) {
        PortalUser user = requireUser(userId);
        requireResearcherRole(user);
        PortalApiService api = getApi(apiId);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(api.getResponseExample(), Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("api", api.getName());
            result.put("method", api.getMethod());
            result.put("path", api.getPath());
            result.put("mock", true);
            result.put("data", body);
            return result;
        } catch (Exception e) {
            throw new BusinessException("模拟调用失败");
        }
    }

    public Map<String, Object> accessStatusForResource(Long userId, Long resourceId) {
        PortalUser user = requireUser(userId);
        PortalDataResource resource = getResource(resourceId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canDownload", canDownloadResource(user, resource.getPermissionLevel()));
        m.put("requiredRole", resource.getPermissionLevel());
        return m;
    }

    public Map<String, Object> accessStatusForApi(Long userId) {
        PortalUser user = requireUser(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canInvoke", "RESEARCHER".equals(user.getRole()));
        return m;
    }

    private PortalUser requireUser(Long userId) {
        PortalUser user = portalUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private void checkDataDownloadPermission(PortalUser user, String level) {
        if (!canDownloadResource(user, level)) {
            if ("RESEARCHER".equals(level)) {
                throw new BusinessException(403, "该资源仅限科研人员下载，请先完成身份认证并等待管理员审核");
            }
            throw new BusinessException(403, "普通用户仅可下载标准级资源");
        }
    }

    private boolean canDownloadResource(PortalUser user, String level) {
        if ("PUBLIC".equals(level) || "STANDARD".equals(level)) {
            return true;
        }
        return "RESEARCHER".equals(level) && "RESEARCHER".equals(user.getRole());
    }

    private void requireResearcherRole(PortalUser user) {
        if (!"RESEARCHER".equals(user.getRole())) {
            throw new BusinessException(403, "API 调用仅限科研人员，请先完成身份认证并等待管理员审核");
        }
    }

    private List<String> buildAccessRights(PortalUser user) {
        List<String> rights = new ArrayList<>();
        rights.add("标准级数据资源下载");
        if ("RESEARCHER".equals(user.getRole())) {
            rights.add("科研级数据资源下载");
            rights.add("全部 API 接口调用");
        }
        return rights;
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String sanitizeFilename(String title) {
        if (title == null || title.isBlank()) return "policy";
        return title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
