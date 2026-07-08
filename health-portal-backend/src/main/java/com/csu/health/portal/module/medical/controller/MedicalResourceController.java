package com.csu.health.portal.module.medical.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.medical.entity.MedicalDrugCatalog;
import com.csu.health.portal.module.medical.entity.MedicalHospital;
import com.csu.health.portal.module.medical.entity.MedicalHospitalGrade;
import com.csu.health.portal.module.medical.entity.MedicalPublicTertiaryHospital;
import com.csu.health.portal.module.medical.service.MedicalResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "医疗资源")
@RestController
@RequestMapping("/api/portal/medical")
@RequiredArgsConstructor
public class MedicalResourceController {

    private final MedicalResourceService medicalResourceService;

    @Operation(summary = "获取医院省份列表")
    @GetMapping("/provinces")
    public Result<List<String>> provinces() {
        return Result.ok(medicalResourceService.getProvinces());
    }

    @Operation(summary = "获取指定省份的城市列表")
    @GetMapping("/cities/{province}")
    public Result<List<String>> cities(@PathVariable String province) {
        return Result.ok(medicalResourceService.getCities(province));
    }

    @Operation(summary = "医院分页查询")
    @GetMapping("/hospitals")
    public Result<Page<MedicalHospital>> hospitals(
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean insurance,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(medicalResourceService.pageHospitals(
                province, city, district, level, type, insurance, keyword, page, size));
    }

    @Operation(summary = "医院详情")
    @GetMapping("/hospitals/{id}")
    public Result<MedicalHospital> hospitalDetail(@PathVariable Long id) {
        MedicalHospital hospital = medicalResourceService.getHospital(id);
        return hospital == null ? Result.fail("医院不存在") : Result.ok(hospital);
    }

    @Operation(summary = "全国三级公立综合医院名单")
    @GetMapping("/public-tertiary-hospitals")
    public Result<Page<MedicalPublicTertiaryHospital>> publicTertiaryHospitals(
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(medicalResourceService.pagePublicTertiaryHospitals(
                province, grade, keyword, page, size));
    }

    @Operation(summary = "医院等级分档名单")
    @GetMapping("/hospital-grades")
    public Result<Map<String, List<MedicalHospitalGrade>>> hospitalGrades() {
        return Result.ok(medicalResourceService.listHospitalGrades());
    }

    @Operation(summary = "真实专科排行榜；无数据时返回空列表")
    @GetMapping("/specialty-rankings")
    public Result<List<Map<String, Object>>> specialtyRankings(
            @RequestParam(required = false) Integer year) {
        return Result.ok(medicalResourceService.getSpecialtyRankings(year));
    }

    @Operation(summary = "国家医保药品目录检索")
    @GetMapping("/drugs")
    public Result<Page<MedicalDrugCatalog>> drugs(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) String drugNumber,
            @RequestParam(required = false) String drugName,
            @RequestParam(required = false) String dosageForm,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(medicalResourceService.pageDrugCatalog(
                categoryCode, categoryName, drugNumber, drugName, dosageForm, page, size));
    }
}

