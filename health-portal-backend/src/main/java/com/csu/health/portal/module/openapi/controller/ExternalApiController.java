package com.csu.health.portal.module.openapi.controller;

import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.medical.entity.MedicalHospitalGrade;
import com.csu.health.portal.module.medical.service.MedicalResourceService;
import com.csu.health.portal.module.portaluser.entity.PortalApiService;
import com.csu.health.portal.module.portaluser.mapper.PortalApiServiceMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部 API 动态路由控制器。<br>
 * 固定端点（如 /api/external/hospitals）直接查询真实数据库；<br>
 * 通用端点 /api/external/{serviceCode} 回退到 portal_api_service 的 response_example 模拟数据。
 * <p>
 * 请求已由 ApiAuthFilter 完成 AppKey 鉴权 + 签名校验 + 限流。
 */
@Slf4j
@RestController
@RequestMapping("/api/external")
@RequiredArgsConstructor
public class ExternalApiController {

    private final PortalApiServiceMapper apiServiceMapper;
    private final MedicalResourceService medicalResourceService;
    private final ObjectMapper objectMapper;

    // ═══════════════════════════════════════════
    //  真实数据查询端点
    // ═══════════════════════════════════════════

    /**
     * 全国医院目录分页查询
     */
    @GetMapping("/hospitals")
    public Result<Map<String, Object>> hospitals(
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageResult = medicalResourceService.pageHospitals(
                province, city, null, null, null, null, keyword, page, size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("records", pageResult.getRecords());
        return Result.ok(result);
    }

    /**
     * 三级公立综合医院名单
     */
    @GetMapping("/tertiary-hospitals")
    public Result<Map<String, Object>> tertiaryHospitals(
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageResult = medicalResourceService.pagePublicTertiaryHospitals(
                province, grade, keyword, page, size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("records", pageResult.getRecords());
        return Result.ok(result);
    }

    /**
     * 复旦医院等级分档（按等级分组）
     */
    @GetMapping("/hospital-grades")
    public Result<Map<String, List<MedicalHospitalGrade>>> hospitalGrades() {
        return Result.ok(medicalResourceService.listHospitalGrades());
    }

    /**
     * 国家药品目录查询（含医保分类、剂型）
     */
    @GetMapping("/drug-catalog")
    public Result<Map<String, Object>> drugCatalog(
            @RequestParam(required = false) String drugName,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String dosageForm,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageResult = medicalResourceService.pageDrugCatalog(
                categoryCode, null, null, drugName, dosageForm, page, size);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", pageResult.getTotal());
        result.put("page", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("records", pageResult.getRecords());
        return Result.ok(result);
    }

    // ═══════════════════════════════════════════
    //  通用模拟数据端点（回退）
    // ═══════════════════════════════════════════

    @RequestMapping("/{serviceCode}")
    public Result<Map<String, Object>> handleMock(
            @PathVariable String serviceCode,
            HttpServletRequest request) throws JsonProcessingException {
        String appKey = (String) request.getAttribute("_appKey");
        String appName = (String) request.getAttribute("_appName");

        PortalApiService service = apiServiceMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PortalApiService>()
                        .eq(PortalApiService::getCode, serviceCode)
                        .eq(PortalApiService::getStatus, 1));
        if (service == null) {
            return Result.fail(404, "API 服务不存在: " + serviceCode);
        }

        Map<String, Object> data;
        if (service.getResponseExample() != null && !service.getResponseExample().isBlank()) {
            data = objectMapper.readValue(service.getResponseExample(), LinkedHashMap.class);
        } else {
            data = new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", service.getName());
        result.put("description", service.getDescription());
        result.put("data", data);

        log.info("外部API模拟调用: appKey={}, appName={}, service={}", appKey, appName, serviceCode);
        return Result.ok(result);
    }
}
