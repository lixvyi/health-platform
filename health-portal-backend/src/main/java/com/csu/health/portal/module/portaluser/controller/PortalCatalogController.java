package com.csu.health.portal.module.portaluser.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.portaluser.entity.PortalApiService;
import com.csu.health.portal.module.portaluser.entity.PortalDataResource;
import com.csu.health.portal.module.portaluser.service.PortalCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "门户资源目录")
@RestController
@RequestMapping("/api/portal/catalog")
@RequiredArgsConstructor
public class PortalCatalogController {

    private final PortalCatalogService catalogService;

    @GetMapping("/resources")
    public Result<?> listResources() {
        return Result.ok(catalogService.listResources());
    }

    @GetMapping("/resources/{id}")
    public Result<PortalDataResource> getResource(@PathVariable Long id) {
        return Result.ok(catalogService.getResource(id));
    }

    @GetMapping("/apis")
    public Result<?> listApis() {
        return Result.ok(catalogService.listApis());
    }

    @GetMapping("/apis/{id}")
    public Result<PortalApiService> getApi(@PathVariable Long id) {
        return Result.ok(catalogService.getApi(id));
    }
}
