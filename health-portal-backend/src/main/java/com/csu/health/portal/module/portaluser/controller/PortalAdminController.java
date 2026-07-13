package com.csu.health.portal.module.portaluser.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.portaluser.dto.ReviewRequest;
import com.csu.health.portal.module.portaluser.service.PortalAdminService;
import com.csu.health.portal.security.AuthPrincipal;
import com.csu.health.portal.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "门户用户管理")
@RestController
@RequestMapping("/api/admin/portal")
@RequiredArgsConstructor
public class PortalAdminController {

    private final PortalAdminService portalAdminService;

    @GetMapping("/certifications")
    public Result<?> listCertifications(@RequestParam(required = false) String status) {
        return Result.ok(portalAdminService.listCertifications(status));
    }

    @PutMapping("/certifications/{userId}/review")
    public Result<Void> reviewCertification(@PathVariable Long userId, @RequestBody ReviewRequest request) {
        AuthPrincipal admin = SecurityUtils.requireAdmin();
        portalAdminService.reviewCertification(userId, request, admin.getUserId());
        return Result.ok();
    }

    @GetMapping("/data-applies")
    public Result<?> listDataApplies(@RequestParam(required = false) String status) {
        return Result.ok(portalAdminService.listDataApplies(status));
    }

    @PutMapping("/data-applies/{id}/review")
    public Result<Void> reviewDataApply(@PathVariable Long id, @RequestBody ReviewRequest request) {
        AuthPrincipal admin = SecurityUtils.requireAdmin();
        portalAdminService.reviewDataApply(id, request, admin.getUserId());
        return Result.ok();
    }

    @GetMapping("/api-applies")
    public Result<?> listApiApplies(@RequestParam(required = false) String status) {
        return Result.ok(portalAdminService.listApiApplies(status));
    }

    @PutMapping("/api-applies/{id}/review")
    public Result<Void> reviewApiApply(@PathVariable Long id, @RequestBody ReviewRequest request) {
        AuthPrincipal admin = SecurityUtils.requireAdmin();
        portalAdminService.reviewApiApply(id, request, admin.getUserId());
        return Result.ok();
    }
}
