package com.csu.health.portal.module.openapi.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.openapi.dto.UpdateQuotaRequest;
import com.csu.health.portal.module.openapi.entity.ApiApp;
import com.csu.health.portal.module.openapi.service.ApiAppService;
import com.csu.health.portal.security.AuthPrincipal;
import com.csu.health.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "API 凭证管理（后台）")
@RestController
@RequestMapping("/api/admin/api-apps")
@RequiredArgsConstructor
public class ApiAppAdminController {

    private final ApiAppService apiAppService;

    @Operation(summary = "应用凭证列表（含待审批）")
    @GetMapping
    public Result<List<ApiApp>> list() {
        return Result.ok(apiAppService.listAll());
    }

    @Operation(summary = "应用凭证详情")
    @GetMapping("/{id}")
    public Result<ApiApp> detail(@PathVariable Long id) {
        ApiApp app = apiAppService.getById(id);
        if (app == null) return Result.fail("应用不存在");
        return Result.ok(app);
    }

    @Operation(summary = "审批通过")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        AuthPrincipal admin = SecurityUtils.requireAdmin();
        apiAppService.approve(id, admin.getUserId());
        return Result.ok();
    }

    @Operation(summary = "启用/禁用")
    @PutMapping("/{id}/toggle-status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        apiAppService.toggleStatus(id);
        return Result.ok();
    }

    @Operation(summary = "重置 AppSecret")
    @PutMapping("/{id}/rotate-secret")
    public Result<Map<String, String>> rotateSecret(@PathVariable Long id) {
        String secret = apiAppService.rotateSecret(id);
        return Result.ok(Map.of("appSecret", secret));
    }

    @Operation(summary = "更新配额")
    @PutMapping("/{id}/quota")
    public Result<Void> updateQuota(@PathVariable Long id, @RequestBody UpdateQuotaRequest req) {
        apiAppService.updateQuota(id, req.getDailyQuota(), req.getQpsLimit());
        return Result.ok();
    }
}
