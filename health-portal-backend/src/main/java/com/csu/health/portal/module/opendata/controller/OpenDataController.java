package com.csu.health.portal.module.opendata.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.opendata.dto.OpenDataDto;
import com.csu.health.portal.module.opendata.service.OpenDataService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@Tag(name = "开放数据")
@RestController
@RequestMapping("/api/portal/open-data")
@RequiredArgsConstructor
public class OpenDataController {

    private final OpenDataService openDataService;

    @Operation(summary = "开放数据目录（多平台）")
    @GetMapping
    public Result<OpenDataDto.Catalog> catalog() {
        return Result.ok(openDataService.catalog());
    }

    @Operation(summary = "首页精选统计图表")
    @GetMapping("/featured")
    public Result<List<OpenDataDto.FeaturedChart>> featured() {
        return Result.ok(openDataService.featured());
    }

    @Operation(summary = "数据集详情")
    @GetMapping("/{id}")
    public Result<JsonNode> detail(@PathVariable String id) {
        try {
            return Result.ok(openDataService.getDataset(id));
        } catch (NoSuchElementException e) {
            return Result.fail(404, e.getMessage());
        }
    }
}
