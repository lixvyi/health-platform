package com.csu.health.portal.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.content.entity.CmsContent;
import com.csu.health.portal.module.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import com.csu.health.portal.module.content.entity.KnowledgeCategory;

@Tag(name = "公众门户")
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalController {

    private final ContentService contentService;

    @Operation(summary = "首页数据")
    @GetMapping("/home")
    public Result<Map<String, Object>> home() {
        return Result.ok(contentService.homeData());
    }

    @Operation(summary = "内容分页")
    @GetMapping("/contents")
    public Result<Page<CmsContent>> contents(
            @RequestParam String categoryCode,
            @RequestParam(required = false) String knowledgeCategoryCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.pagePublished(
                categoryCode, knowledgeCategoryCode, keyword, page, size));
    }

    @Operation(summary = "内容详情")
    @GetMapping("/contents/{id}")
    public Result<CmsContent> detail(@PathVariable Long id) {
        return Result.ok(contentService.getPublishedDetail(id));
    }

    @Operation(summary = "健康百科分类")
    @GetMapping("/knowledge/categories")
    public Result<List<KnowledgeCategory>> knowledgeCategories() {
        return Result.ok(contentService.listKnowledgeCategories());
    }

    @Operation(summary = "相关推荐")
    @GetMapping("/contents/{id}/related")
    public Result<List<CmsContent>> related(
            @PathVariable Long id,
            @RequestParam(defaultValue = "6") int limit) {
        return Result.ok(contentService.relatedPublished(id, limit));
    }

    @Operation(summary = "轮播图")
    @GetMapping("/banners")
    public Result<?> banners() {
        return Result.ok(contentService.listBanners());
    }

    @Operation(summary = "应用中心")
    @GetMapping("/apps")
    public Result<?> apps() {
        return Result.ok(contentService.listApps());
    }

    @Operation(summary = "关于我们")
    @GetMapping("/about")
    public Result<Map<String, Object>> about() {
        return Result.ok(contentService.getAbout());
    }

    @Operation(summary = "统计数据")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.ok(contentService.stats());
    }
}
