package com.csu.health.portal.module.portaluser.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.opendata.service.OpenDataExportService;
import com.csu.health.portal.module.portaluser.dto.CertifyRequest;
import com.csu.health.portal.module.portaluser.dto.PortalAuthResponse;
import com.csu.health.portal.module.portaluser.service.PortalAuthService;
import com.csu.health.portal.module.portaluser.service.PortalCatalogService;
import com.csu.health.portal.security.AuthPrincipal;
import com.csu.health.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Tag(name = "门户用户服务")
@RestController
@RequestMapping("/api/portal/user")
@RequiredArgsConstructor
public class PortalUserController {

    private final PortalAuthService portalAuthService;
    private final PortalCatalogService catalogService;
    private final com.csu.health.portal.module.portaluser.mapper.PortalUserMapper portalUserMapper;
    private final com.csu.health.portal.module.openapi.service.ApiAppService apiAppService;
    private final com.csu.health.portal.module.medical.service.MedicalResourceService medicalResourceService;
    private final com.csu.health.portal.module.portaluser.mapper.PortalApiServiceMapper portalApiServiceMapper;

    @GetMapping("/me")
    public Result<PortalAuthResponse> me() {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        return Result.ok(portalAuthService.me(p.getUserId()));
    }

    @Operation(summary = "提交科研人员认证")
    @PostMapping("/certify")
    public Result<Void> certify(@Valid @RequestBody CertifyRequest request) {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        portalAuthService.submitCertify(p.getUserId(), request);
        return Result.ok();
    }

    @Operation(summary = "我的申请（身份认证状态）")
    @GetMapping("/applies")
    public Result<Map<String, Object>> myApplies() {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        return Result.ok(catalogService.myCertificationProfile(p.getUserId()));
    }

    @GetMapping("/resources/{id}/download-file")
    public ResponseEntity<byte[]> downloadResourceFile(@PathVariable Long id) {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        OpenDataExportService.ExportPayload payload = catalogService.exportResourceFile(p.getUserId(), id);
        return fileResponse(payload);
    }

    @GetMapping("/resources/{id}/access-status")
    public Result<Map<String, Object>> resourceAccessStatus(@PathVariable Long id) {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        return Result.ok(catalogService.accessStatusForResource(p.getUserId(), id));
    }

    @GetMapping("/apis/access-status")
    public Result<Map<String, Object>> apiAccessStatus() {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        return Result.ok(catalogService.accessStatusForApi(p.getUserId()));
    }

    @PostMapping("/apis/{id}/invoke")
    public Result<Map<String, Object>> invokeApi(@PathVariable Long id) {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        return Result.ok(catalogService.invokeApi(p.getUserId(), id));
    }

    @GetMapping("/policies/{contentId}/download-file")
    public ResponseEntity<byte[]> downloadPolicyFile(@PathVariable Long contentId) {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        OpenDataExportService.ExportPayload payload = catalogService.exportPolicyFile(p.getUserId(), contentId);
        return fileResponse(payload);
    }

    private static String fixParam(String value) {
        return (value == null || value.isBlank() || "undefined".equals(value)) ? null : value.trim();
    }

    @Operation(summary = "我的 API 密钥", description = "返回当前门户用户(科研人员)名下的所有 AppKey，仅科研人员角色可查看")
    @GetMapping("/api-keys")
    public Result<java.util.List<com.csu.health.portal.module.openapi.entity.ApiApp>> myApiKeys() {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        com.csu.health.portal.module.portaluser.entity.PortalUser user = portalUserMapper.selectById(p.getUserId());
        if (user == null) return Result.fail("用户不存在");
        if (!"RESEARCHER".equals(user.getRole())) return Result.fail(403, "仅科研人员可查看 API 密钥");
        java.util.List<com.csu.health.portal.module.openapi.entity.ApiApp> keys = apiAppService.listByOwner(user.getRealName());
        for (com.csu.health.portal.module.openapi.entity.ApiApp k : keys) k.setAppSecret(null);
        return Result.ok(keys);
    }

    @Operation(summary = "申请 API 密钥", description = "科研人员自行创建 API 密钥申请，创建后状态为待审批，管理员审核通过后方可使用")
    @PostMapping("/api-keys/apply")
    public Result<com.csu.health.portal.module.openapi.dto.ApiAppVO> applyApiKey(
            @Valid @RequestBody com.csu.health.portal.module.openapi.dto.CreateApiAppRequest req) {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        com.csu.health.portal.module.portaluser.entity.PortalUser user = portalUserMapper.selectById(p.getUserId());
        if (user == null) return Result.fail("用户不存在");
        if (!"RESEARCHER".equals(user.getRole())) return Result.fail(403, "仅科研人员可申请 API 密钥");
        // 自动填充用户信息
        if (req.getOwner() == null || req.getOwner().isBlank()) req.setOwner(user.getRealName());
        if (req.getEmail() == null || req.getEmail().isBlank()) req.setEmail(user.getEmail());
        if (req.getOrganization() == null || req.getOrganization().isBlank())
            req.setOrganization(user.getOrganization());
        com.csu.health.portal.module.openapi.entity.ApiApp app = apiAppService.create(
                req.getAppName(), req.getOwner(), req.getEmail(), req.getOrganization(),
                req.getDescription(), req.getDailyQuota(), req.getQpsLimit(), req.getTier());
        // 创建时返回完整信息（含 secret）
        com.csu.health.portal.module.openapi.dto.ApiAppVO vo = new com.csu.health.portal.module.openapi.dto.ApiAppVO(
                app.getId(), app.getAppName(), app.getAppKey(), app.getStatus(),
                app.getOwner(), app.getEmail(), app.getOrganization(), app.getDescription(),
                app.getIpWhitelist(), app.getDailyQuota(), app.getQpsLimit(), app.getTier(),
                app.getApprovedBy(), app.getApprovedAt(), app.getExpireAt(), app.getCreatedAt(),
                app.getAppSecret());
        return Result.ok(vo);
    }

    @Operation(summary = "测试调用实时数据 API", description = "科研人员内部测试用，返回真实数据库查询结果，无需 AppKey")
    @PostMapping("/external-test/{serviceCode}")
    public Result<Map<String, Object>> testExternalApi(@PathVariable String serviceCode,
                                                       @RequestParam(required = false) String province,
                                                       @RequestParam(required = false) String city,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String drugName,
                                                       @RequestParam(required = false) String categoryCode,
                                                       @RequestParam(required = false) String dosageForm,
                                                       @RequestParam(required = false) String grade,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size) {
        AuthPrincipal p = SecurityUtils.requirePortalUser();
        com.csu.health.portal.module.portaluser.entity.PortalUser user = portalUserMapper.selectById(p.getUserId());
        if (user == null) return Result.fail("用户不存在");
        if (!"RESEARCHER".equals(user.getRole())) return Result.fail(403, "仅科研人员可测试 API");
        // 过滤掉前端传过来的 "undefined" 字符串
        province = fixParam(province);
        city = fixParam(city);
        keyword = fixParam(keyword);
        drugName = fixParam(drugName);
        categoryCode = fixParam(categoryCode);
        dosageForm = fixParam(dosageForm);
        grade = fixParam(grade);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        switch (serviceCode) {
            case "hospitals" -> {
                var pr = medicalResourceService.pageHospitals(province, city, null, null, null, null, keyword, page, size);
                result.put("total", pr.getTotal());
                result.put("page", pr.getCurrent());
                result.put("size", pr.getSize());
                result.put("records", pr.getRecords());
            }
            case "tertiary-hospitals" -> {
                var pr = medicalResourceService.pagePublicTertiaryHospitals(province, grade, keyword, page, size);
                result.put("total", pr.getTotal());
                result.put("page", pr.getCurrent());
                result.put("size", pr.getSize());
                result.put("records", pr.getRecords());
            }
            case "hospital-grades" -> {
                result.put("data", medicalResourceService.listHospitalGrades());
            }
            case "drug-catalog" -> {
                var pr = medicalResourceService.pageDrugCatalog(categoryCode, null, null, drugName, dosageForm, page, size);
                result.put("total", pr.getTotal());
                result.put("page", pr.getCurrent());
                result.put("size", pr.getSize());
                result.put("records", pr.getRecords());
            }
            default -> {
                return Result.fail(404, "未知服务: " + serviceCode);
            }
        }
        return Result.ok(result);
    }

    private ResponseEntity<byte[]> fileResponse(OpenDataExportService.ExportPayload payload) {
        String encoded = URLEncoder.encode(payload.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(payload.contentType() + ";charset=UTF-8"))
                .body(payload.bytes());
    }
}
