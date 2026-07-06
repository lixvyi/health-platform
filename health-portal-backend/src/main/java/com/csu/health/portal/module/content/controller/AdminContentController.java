package com.csu.health.portal.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.auth.entity.SysUser;
import com.csu.health.portal.module.content.dto.ContentSaveRequest;
import com.csu.health.portal.module.content.entity.CmsApp;
import com.csu.health.portal.module.content.entity.CmsBanner;
import com.csu.health.portal.module.content.entity.CmsContent;
import com.csu.health.portal.module.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "后台管理")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminContentController {

    private final ContentService contentService;

    @GetMapping("/contents")
    public Result<Page<CmsContent>> page(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.pageContent(categoryCode, keyword, status, page, size));
    }

    @PostMapping("/contents")
    public Result<Long> create(@Valid @RequestBody ContentSaveRequest req,
                               @AuthenticationPrincipal SysUser user) {
        return Result.ok(contentService.save(req, user.getId()));
    }

    @PutMapping("/contents/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ContentSaveRequest req) {
        contentService.update(id, req);
        return Result.ok();
    }

    @DeleteMapping("/contents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        contentService.delete(id);
        return Result.ok();
    }

    @GetMapping("/banners")
    public Result<?> banners() {
        return Result.ok(contentService.listAllBanners());
    }

    @PostMapping("/banners")
    public Result<Long> createBanner(@RequestBody CmsBanner banner) {
        return Result.ok(contentService.saveBanner(banner));
    }

    @PutMapping("/banners/{id}")
    public Result<Void> updateBanner(@PathVariable Long id, @RequestBody CmsBanner banner) {
        banner.setId(id);
        contentService.updateBanner(banner);
        return Result.ok();
    }

    @DeleteMapping("/banners/{id}")
    public Result<Void> deleteBanner(@PathVariable Long id) {
        contentService.deleteBanner(id);
        return Result.ok();
    }

    @GetMapping("/apps")
    public Result<?> apps() {
        return Result.ok(contentService.listAllApps());
    }

    @PostMapping("/apps")
    public Result<Long> createApp(@RequestBody CmsApp app) {
        return Result.ok(contentService.saveApp(app));
    }

    @PutMapping("/apps/{id}")
    public Result<Void> updateApp(@PathVariable Long id, @RequestBody CmsApp app) {
        app.setId(id);
        contentService.updateApp(app);
        return Result.ok();
    }

    @DeleteMapping("/apps/{id}")
    public Result<Void> deleteApp(@PathVariable Long id) {
        contentService.deleteApp(id);
        return Result.ok();
    }

    @Operation(summary = "关于我们配置")
    @GetMapping("/about")
    public Result<Map<String, Object>> about() {
        return Result.ok(contentService.getAbout());
    }

    @PutMapping("/about")
    public Result<Void> updateAbout(@RequestBody Map<String, String> body) {
        contentService.updateAbout(body.get("title"), body.get("content"));
        return Result.ok();
    }

    @PutMapping("/home-intro")
    public Result<Void> updateHomeIntro(@RequestBody Map<String, String> body) {
        contentService.updateHomeIntro(body.get("intro"));
        return Result.ok();
    }

    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.ok(contentService.stats());
    }
}
