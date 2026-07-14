package com.csu.health.portal.module.medical.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.module.medical.dto.DrugDetailVO;
import com.csu.health.portal.module.medical.dto.DrugSearchResult;
import com.csu.health.portal.module.medical.entity.DrugBasic;
import com.csu.health.portal.module.medical.entity.DrugDetail;
import com.csu.health.portal.module.medical.mapper.DrugBasicMapper;
import com.csu.health.portal.module.medical.mapper.DrugDetailMapper;
import com.csu.health.portal.module.medical.mapper.SymptomOtcMapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugDetailService {

    private static final int MAX_PAGE_SIZE = 100;

    private final DrugBasicMapper drugBasicMapper;
    private final DrugDetailMapper drugDetailMapper;
    private final SymptomOtcMapMapper symptomOtcMapMapper;

    // ========== 综合搜索 ==========
    public Page<DrugSearchResult> search(String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        List<DrugBasic> basics;
        try {
            basics = drugBasicMapper.searchByFulltext(keyword, safePage * safeSize);
        } catch (Exception e) {
            log.warn("全文索引不可用，降级为 LIKE 搜索: {}", e.getMessage());
            basics = drugBasicMapper.searchByLike(keyword, safePage * safeSize);
        }

        List<DrugSearchResult> results = basics.stream()
                .skip((long) (safePage - 1) * safeSize)
                .limit(safeSize)
                .map(b -> {
                    DrugDetail detail = drugDetailMapper.selectById(b.getId());
                    return DrugSearchResult.builder()
                            .id(b.getId())
                            .genericName(b.getGenericName())
                            .brandName(b.getBrandName())
                            .category(b.getCategory())
                            .dosageForm(b.getDosageForm())
                            .prescriptionType(b.getPrescriptionType())
                            .indicationsSummary(truncate(detail != null ? detail.getIndications() : null, 100))
                            .build();
                })
                .collect(Collectors.toList());

        Page<DrugSearchResult> resultPage = new Page<>(safePage, safeSize, basics.size());
        resultPage.setRecords(results);
        return resultPage;
    }

    // ========== 药品详情 ==========
    public DrugDetailVO getDetail(Long id) {
        DrugBasic basic = drugBasicMapper.selectById(id);
        if (basic == null) return null;
        DrugDetail detail = drugDetailMapper.selectById(id);

        return DrugDetailVO.builder()
                .id(basic.getId())
                .approvalNumber(basic.getApprovalNumber())
                .genericName(basic.getGenericName())
                .brandName(basic.getBrandName())
                .manufacturer(basic.getManufacturer())
                .category(basic.getCategory())
                .dosageForm(basic.getDosageForm())
                .prescriptionType(basic.getPrescriptionType())
                .atcCode(basic.getAtcCode())
                .indications(detail != null ? detail.getIndications() : null)
                .contraindications(detail != null ? detail.getContraindications() : null)
                .adverseReactions(detail != null ? detail.getAdverseReactions() : null)
                .usageDosage(detail != null ? detail.getUsageDosage() : null)
                .warnings(detail != null ? detail.getWarnings() : null)
                .composition(detail != null ? detail.getComposition() : null)
                .storage(detail != null ? detail.getStorage() : null)
                .validity(detail != null ? detail.getValidity() : null)
                .build();
    }

    // ========== 非处方药推荐（基于 symptom_otc_map） ==========
    public List<DrugSearchResult> recommendOtc(String query) {
        if (query == null || query.isBlank()) return List.of();
        List<Long> drugIds = symptomOtcMapMapper.findDrugIdsBySymptom(query.trim());
        if (drugIds.isEmpty()) return List.of();
        List<DrugBasic> basics = drugBasicMapper.selectBatchIds(drugIds);
        return basics.stream().map(b -> {
            DrugDetail detail = drugDetailMapper.selectById(b.getId());
            return DrugSearchResult.builder()
                    .id(b.getId())
                    .genericName(b.getGenericName())
                    .brandName(b.getBrandName())
                    .category(b.getCategory())
                    .dosageForm(b.getDosageForm())
                    .prescriptionType(b.getPrescriptionType())
                    .indicationsSummary(truncate(detail != null ? detail.getIndications() : null, 150))
                    .build();
        }).collect(Collectors.toList());
    }

    // ========== 分类统计 ==========
    public List<Map<String, Object>> getCategoryDistribution() {
        return drugBasicMapper.countByCategory();
    }

    // ========== 剂型统计 ==========
    public List<Map<String, Object>> getDosageFormStats() {
        return drugBasicMapper.countByDosageForm();
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
