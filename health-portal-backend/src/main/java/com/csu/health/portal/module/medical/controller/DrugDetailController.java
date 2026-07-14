package com.csu.health.portal.module.medical.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.medical.dto.DrugDetailVO;
import com.csu.health.portal.module.medical.dto.DrugSearchResult;
import com.csu.health.portal.module.medical.service.DrugDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "药品详情")
@RestController
@RequestMapping("/api/drug")
@RequiredArgsConstructor
public class DrugDetailController {

    private final DrugDetailService drugDetailService;

    @Operation(summary = "综合搜索")
    @GetMapping("/search")
    public Result<Page<DrugSearchResult>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(drugDetailService.search(keyword, page, size));
    }

    @Operation(summary = "药品详情")
    @GetMapping("/{id}")
    public Result<DrugDetailVO> detail(@PathVariable Long id) {
        DrugDetailVO vo = drugDetailService.getDetail(id);
        return vo == null ? Result.fail("药品不存在") : Result.ok(vo);
    }

    @Operation(summary = "非处方药推荐")
    @GetMapping("/recommend")
    public Result<?> recommend(@RequestParam String query) {
        List<DrugSearchResult> list = drugDetailService.recommendOtc(query);
        return Result.ok(Map.of(
                "recommendations", list,
                "disclaimer", "以上推荐仅供参考，不能替代专业诊断。用药前请仔细阅读说明书或咨询医生/药师。"
        ));
    }

    @Operation(summary = "分类统计")
    @GetMapping("/stats/category-distribution")
    public Result<List<Map<String, Object>>> categoryStats() {
        return Result.ok(drugDetailService.getCategoryDistribution());
    }

    @Operation(summary = "剂型统计")
    @GetMapping("/stats/dosage-form-stats")
    public Result<List<Map<String, Object>>> dosageFormStats() {
        return Result.ok(drugDetailService.getDosageFormStats());
    }
}
