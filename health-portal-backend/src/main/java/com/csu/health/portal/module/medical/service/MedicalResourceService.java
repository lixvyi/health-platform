package com.csu.health.portal.module.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.module.medical.entity.MedicalDrugCatalog;
import com.csu.health.portal.module.medical.entity.MedicalHospital;
import com.csu.health.portal.module.medical.entity.MedicalHospitalGrade;
import com.csu.health.portal.module.medical.entity.MedicalPublicTertiaryHospital;
import com.csu.health.portal.module.medical.entity.MedicalSpecialtyRanking;
import com.csu.health.portal.module.medical.mapper.MedicalDrugCatalogMapper;
import com.csu.health.portal.module.medical.mapper.MedicalHospitalGradeMapper;
import com.csu.health.portal.module.medical.mapper.MedicalHospitalMapper;
import com.csu.health.portal.module.medical.mapper.MedicalPublicTertiaryHospitalMapper;
import com.csu.health.portal.module.medical.mapper.MedicalSpecialtyRankingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicalResourceService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MedicalHospitalMapper hospitalMapper;
    private final MedicalPublicTertiaryHospitalMapper publicTertiaryHospitalMapper;
    private final MedicalHospitalGradeMapper hospitalGradeMapper;
    private final MedicalSpecialtyRankingMapper specialtyRankingMapper;
    private final MedicalDrugCatalogMapper drugCatalogMapper;

    public List<String> getProvinces() {
        return hospitalMapper.selectDistinctProvinces();
    }

    public List<String> getCities(String province) {
        return isBlank(province) ? List.of() : hospitalMapper.selectCitiesByProvince(province.trim());
    }

    public Page<MedicalHospital> pageHospitals(
            String province, String city, String district, String level, String type,
            Boolean insurance, String keyword, int page, int size) {
        LambdaQueryWrapper<MedicalHospital> wrapper = new LambdaQueryWrapper<>();
        eqIfPresent(wrapper, MedicalHospital::getProvince, province);
        eqIfPresent(wrapper, MedicalHospital::getCity, city);
        eqIfPresent(wrapper, MedicalHospital::getDistrict, district);
        eqIfPresent(wrapper, MedicalHospital::getLevel, level);
        eqIfPresent(wrapper, MedicalHospital::getType, type);
        if (insurance != null) {
            wrapper.eq(MedicalHospital::getIsInsurance, insurance ? 1 : 0);
        }
        if (!isBlank(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(MedicalHospital::getName, value)
                    .or().like(MedicalHospital::getAliasName, value)
                    .or().like(MedicalHospital::getAddress, value));
        }
        wrapper.orderByAsc(MedicalHospital::getProvince, MedicalHospital::getCity,
                MedicalHospital::getDistrict, MedicalHospital::getName, MedicalHospital::getId);
        return hospitalMapper.selectPage(page(page, size), wrapper);
    }

    public MedicalHospital getHospital(Long id) {
        return id == null || id <= 0 ? null : hospitalMapper.selectById(id);
    }

    public Page<MedicalPublicTertiaryHospital> pagePublicTertiaryHospitals(
            String province, String grade, String keyword, int page, int size) {
        LambdaQueryWrapper<MedicalPublicTertiaryHospital> wrapper = new LambdaQueryWrapper<>();
        eqIfPresent(wrapper, MedicalPublicTertiaryHospital::getProvince, province);
        eqIfPresent(wrapper, MedicalPublicTertiaryHospital::getGrade, grade);
        if (!isBlank(keyword)) {
            wrapper.like(MedicalPublicTertiaryHospital::getHospitalName, keyword.trim());
        }
        wrapper.orderByAsc(MedicalPublicTertiaryHospital::getGrade,
                MedicalPublicTertiaryHospital::getProvince,
                MedicalPublicTertiaryHospital::getHospitalName,
                MedicalPublicTertiaryHospital::getId);
        return publicTertiaryHospitalMapper.selectPage(page(page, size), wrapper);
    }

    public Map<String, List<MedicalHospitalGrade>> listHospitalGrades() {
        LambdaQueryWrapper<MedicalHospitalGrade> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(MedicalHospitalGrade::getSourceRow, MedicalHospitalGrade::getId);
        List<MedicalHospitalGrade> records = hospitalGradeMapper.selectList(wrapper);
        return records.stream().collect(Collectors.groupingBy(
                MedicalHospitalGrade::getGrade,
                LinkedHashMap::new,
                Collectors.toList()));
    }

    public List<Map<String, Object>> getSpecialtyRankings(Integer requestedYear) {
        Integer year = requestedYear;
        if (year == null) {
            List<Object> years = specialtyRankingMapper.selectObjs(
                    new LambdaQueryWrapper<MedicalSpecialtyRanking>()
                            .select(MedicalSpecialtyRanking::getRankYear)
                            .orderByDesc(MedicalSpecialtyRanking::getRankYear)
                            .last("LIMIT 1"));
            if (years.isEmpty()) {
                return List.of();
            }
            year = Integer.valueOf(String.valueOf(years.get(0)));
        }

        LambdaQueryWrapper<MedicalSpecialtyRanking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MedicalSpecialtyRanking::getRankYear, year)
                .orderByAsc(MedicalSpecialtyRanking::getSpecialtyName,
                        MedicalSpecialtyRanking::getRanking);
        Map<String, List<MedicalSpecialtyRanking>> grouped = specialtyRankingMapper
                .selectList(wrapper).stream()
                .collect(Collectors.groupingBy(
                        MedicalSpecialtyRanking::getSpecialtyName,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<MedicalSpecialtyRanking>> entry : grouped.entrySet()) {
            List<MedicalSpecialtyRanking> topTen = entry.getValue().stream().limit(10).toList();
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("specialtyName", entry.getKey());
            group.put("rankYear", year);
            group.put("sourceName", topTen.isEmpty() ? "" : topTen.get(0).getSourceName());
            group.put("hospitals", topTen);
            result.add(group);
        }
        return result;
    }

    public Page<MedicalDrugCatalog> pageDrugCatalog(
            String categoryCode, String categoryName, String drugNumber, String drugName,
            String dosageForm, int page, int size) {
        LambdaQueryWrapper<MedicalDrugCatalog> wrapper = new LambdaQueryWrapper<>();
        if (!isBlank(categoryCode)) {
            wrapper.likeRight(MedicalDrugCatalog::getCategoryCode, categoryCode.trim());
        }
        if (!isBlank(categoryName)) {
            wrapper.like(MedicalDrugCatalog::getCategoryName, categoryName.trim());
        }
        if (!isBlank(drugNumber)) {
            wrapper.eq(MedicalDrugCatalog::getDrugNumber, drugNumber.trim());
        }
        if (!isBlank(drugName)) {
            wrapper.like(MedicalDrugCatalog::getDrugName, drugName.trim());
        }
        if (!isBlank(dosageForm)) {
            wrapper.like(MedicalDrugCatalog::getDosageForm, dosageForm.trim());
        }
        wrapper.orderByAsc(MedicalDrugCatalog::getCategoryCode,
                MedicalDrugCatalog::getDrugNumber, MedicalDrugCatalog::getSourceRow,
                MedicalDrugCatalog::getId);
        return drugCatalogMapper.selectPage(page(page, size), wrapper);
    }

    private static <T> void eqIfPresent(
            LambdaQueryWrapper<T> wrapper,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column,
            String value) {
        if (!isBlank(value)) {
            wrapper.eq(column, value.trim());
        }
    }

    private static <T> Page<T> page(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        return new Page<>(safePage, safeSize);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
