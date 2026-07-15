package com.csu.health.portal.module.openapi.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.openapi.service.ApiAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API 用量统计看板 — 管理后台用
 */
@Tag(name = "API 用量统计")
@RestController
@RequestMapping("/api/admin/api-usage")
@RequiredArgsConstructor
public class ApiUsageStatsController {

    private final ApiAppService apiAppService;

    @Operation(summary = "今日各 AppKey 调用排行")
    @GetMapping("/today")
    public Result<List<Map<String, Object>>> today() {
        return Result.ok(apiAppService.todayUsage());
    }

    @Operation(summary = "所有 AppKey 近N天每日趋势")
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(
            @RequestParam(defaultValue = "30") int days) {
        return Result.ok(apiAppService.allDailyUsage(days));
    }

    @Operation(summary = "指定 AppKey 近N天每日趋势")
    @GetMapping("/trend/{appKey}")
    public Result<List<Map<String, Object>>> trendByKey(
            @PathVariable String appKey,
            @RequestParam(defaultValue = "30") int days) {
        return Result.ok(apiAppService.dailyUsage(appKey, days));
    }

    @Operation(summary = "总体概览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(
            @RequestParam(defaultValue = "30") int days) {
        return Result.ok(apiAppService.overallStats(days));
    }
}
