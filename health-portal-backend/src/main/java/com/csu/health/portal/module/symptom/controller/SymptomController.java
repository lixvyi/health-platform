package com.csu.health.portal.module.symptom.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.symptom.dto.*;
import com.csu.health.portal.module.symptom.service.SymptomKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * 症状自查API控制器
 */
@Tag(name = "症状自查", description = "症状知识库查询与自查接口")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SymptomController {

    private final SymptomKnowledgeService symptomKnowledgeService;

    /**
     * 获取身体部位树
     */
    @Operation(summary = "获取身体部位树", description = "返回所有身体部位的树形结构，包含children嵌套")
    @GetMapping("/body-parts")
    public Result<List<BodyPartDto>> getBodyParts() {
        return Result.ok(symptomKnowledgeService.getBodyPartTree());
    }

    /**
     * 获取指定部位下的症状列表
     */
    @Operation(summary = "获取部位症状", description = "根据部位ID获取该部位下的所有症状")
    @GetMapping("/symptoms")
    public Result<List<SymptomDto>> getSymptomsByPartId(
            @RequestParam(required = false) Integer partId) {
        if (partId == null) {
            // 如果没有指定部位，返回所有症状
            return Result.ok(symptomKnowledgeService.getSymptomsByPartId(1)); // 默认返回"全身"的症状
        }
        return Result.ok(symptomKnowledgeService.getSymptomsByPartId(partId));
    }

    /**
     * 症状自查主接口
     */
    @Operation(summary = "症状自查", description = "根据用户选择的症状进行自查，返回推荐科室、紧急警示和提醒信息")
    @PostMapping("/check")
    public Result<SymptomCheckResponse> checkSymptoms(
            @Valid @RequestBody SymptomCheckRequest request) {
        return Result.ok(symptomKnowledgeService.checkSymptoms(request.getSymptomIds()));
    }

    /**
     * 热重载知识库（运维接口）
     */
    @Operation(summary = "热重载知识库", description = "重新加载症状知识库JSON文件")
    @PostMapping("/reload")
    public Result<String> reloadKnowledge() throws IOException {
        symptomKnowledgeService.reload();
        return Result.ok("知识库重载成功");
    }
}
