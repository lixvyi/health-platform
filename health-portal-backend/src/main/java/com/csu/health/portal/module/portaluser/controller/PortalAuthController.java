package com.csu.health.portal.module.portaluser.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.portaluser.dto.*;
import com.csu.health.portal.module.portaluser.service.PortalAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "门户用户认证")
@RestController
@RequestMapping("/api/portal/auth")
@RequiredArgsConstructor
public class PortalAuthController {

    private final PortalAuthService portalAuthService;

    @Operation(summary = "公众用户注册")
    @PostMapping("/register")
    public Result<PortalAuthResponse> register(@Valid @RequestBody PortalRegisterRequest request) {
        return Result.ok(portalAuthService.register(request));
    }

    @Operation(summary = "公众用户登录")
    @PostMapping("/login")
    public Result<PortalAuthResponse> login(@Valid @RequestBody PortalLoginRequest request) {
        return Result.ok(portalAuthService.login(request));
    }
}
