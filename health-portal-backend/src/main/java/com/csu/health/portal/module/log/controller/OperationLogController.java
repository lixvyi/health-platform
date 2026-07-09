package com.csu.health.portal.module.log.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.log.entity.SysOperationLog;
import com.csu.health.portal.module.log.mapper.SysOperationLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final SysOperationLogMapper logMapper;

    @Operation(summary = "日志分页列表")
    @GetMapping
    public Result<Page<SysOperationLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        if (module != null && !module.isBlank()) {
            wrapper.eq(SysOperationLog::getModule, module);
        }
        wrapper.orderByDesc(SysOperationLog::getCreatedAt);
        return Result.ok(logMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Operation(summary = "日志统计")
    @GetMapping("/stats")
    public Result<?> stats() {
        long total = logMapper.selectCount(null);
        long today = logMapper.selectCount(new LambdaQueryWrapper<SysOperationLog>()
                .ge(SysOperationLog::getCreatedAt, java.time.LocalDate.now().atStartOfDay()));
        return Result.ok(java.util.Map.of("total", total, "today", today));
    }
}
