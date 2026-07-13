package com.csu.health.portal.module.datapool.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.datapool.dto.DataPoolDto;
import com.csu.health.portal.module.datapool.service.DataGovernanceService;
import com.csu.health.portal.module.datapool.service.DataPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "数据资源池")
@RestController
@RequestMapping("/api/portal/data-pool")
@RequiredArgsConstructor
public class DataPoolPortalController {

    private final DataPoolService dataPoolService;
    private final DataGovernanceService dataGovernanceService;

    @Operation(summary = "统一数据平台架构")
    @GetMapping("/architecture")
    public Result<DataPoolDto.Architecture> architecture() {
        return Result.ok(dataPoolService.architecture());
    }

    @Operation(summary = "互联网公开采集资讯")
    @GetMapping("/internet")
    public Result<List<DataPoolDto.InternetFeed>> internet() {
        return Result.ok(dataPoolService.internetFeeds());
    }

    @Operation(summary = "采集任务状态")
    @GetMapping("/collect/status")
    public Result<DataPoolDto.CollectStatus> status() {
        return Result.ok(dataPoolService.collectStatus());
    }

    @Operation(summary = "大数据扩展层状态")
    @GetMapping("/bigdata/status")
    public Result<DataPoolDto.BigDataStatus> bigDataStatus() {
        return Result.ok(dataPoolService.bigDataStatus());
    }

    @Operation(summary = "数据治理看板")
    @GetMapping("/governance")
    public Result<DataPoolDto.GovernanceDashboard> governance() {
        return Result.ok(dataGovernanceService.dashboard());
    }
}
