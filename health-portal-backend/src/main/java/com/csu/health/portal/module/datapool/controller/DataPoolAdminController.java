package com.csu.health.portal.module.datapool.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.datapool.dto.DataPoolDto;
import com.csu.health.portal.module.datapool.service.DataGovernanceService;
import com.csu.health.portal.module.datapool.service.DataPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "数据采集管理")
@RestController
@RequestMapping("/api/admin/data-pool")
@RequiredArgsConstructor
public class DataPoolAdminController {

    private final DataPoolService dataPoolService;
    private final DataGovernanceService dataGovernanceService;

    @Operation(summary = "触发数据采集（爬虫+开放数据同步）")
    @PostMapping("/collect")
    public Result<DataPoolDto.CollectStatus> collect() {
        return Result.ok(dataPoolService.triggerCollect());
    }

    @Operation(summary = "采集状态")
    @GetMapping("/collect/status")
    public Result<DataPoolDto.CollectStatus> status() {
        return Result.ok(dataPoolService.collectStatus());
    }

    @Operation(summary = "执行 Spark ETL 批处理")
    @PostMapping("/etl/run")
    public Result<DataPoolDto.BigDataStatus> runEtl() {
        return Result.ok(dataPoolService.triggerEtl());
    }

    @Operation(summary = "更新数据治理异常处理状态")
    @PatchMapping("/governance/issues/{id}")
    public Result<DataPoolDto.GovernanceIssue> updateGovernanceIssue(
            @PathVariable long id,
            @RequestBody DataPoolDto.GovernanceIssueUpdate request) {
        return Result.ok(dataGovernanceService.updateIssue(id, request));
    }
}
