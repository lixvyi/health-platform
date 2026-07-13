package com.csu.health.portal.module.portaluser.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.portaluser.dto.CertifyRequest;
import com.csu.health.portal.module.portaluser.dto.PortalAuthResponse;
import com.csu.health.portal.module.opendata.service.OpenDataExportService;
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

    private ResponseEntity<byte[]> fileResponse(OpenDataExportService.ExportPayload payload) {
        String encoded = URLEncoder.encode(payload.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(payload.contentType() + ";charset=UTF-8"))
                .body(payload.bytes());
    }
}
