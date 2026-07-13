package com.csu.health.portal.config;

import com.csu.health.portal.module.opendata.dto.OpenDataDto;
import com.csu.health.portal.module.opendata.service.OpenDataExportService;
import com.csu.health.portal.module.opendata.service.OpenDataService;
import com.csu.health.portal.module.portaluser.entity.PortalDataResource;
import com.csu.health.portal.module.portaluser.mapper.PortalDataResourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class PortalResourceSyncRunner implements CommandLineRunner {

    private static final Set<String> RESEARCHER_KEYWORDS = Set.of(
            "医疗指标", "体质", "孕产妇", "预防接种", "艾滋病", "母婴", "卡介苗", "从业人员预防性"
    );

    private final OpenDataService openDataService;
    private final OpenDataExportService openDataExportService;
    private final PortalDataResourceMapper resourceMapper;

    @Override
    public void run(String... args) {
        try {
            resourceMapper.delete(new LambdaQueryWrapper<PortalDataResource>()
                    .isNull(PortalDataResource::getOpenDataId)
                    .or()
                    .in(PortalDataResource::getCode, List.of(
                            "nbs-health-stats", "shanghai-medical", "shanghai-population",
                            "env-air-quality", "chronic-disease-sample")));
            syncOpenDatasets();
            syncProcessedResources();
            log.info("Portal data resources synced from open data catalog");
        } catch (Exception e) {
            log.warn("Portal resource sync skipped: {}", e.getMessage());
        }
    }

    private void syncOpenDatasets() {
        OpenDataDto.Catalog catalog = openDataService.catalog();
        if (catalog.getPlatforms() == null) return;
        int order = 0;
        for (OpenDataDto.Platform platform : catalog.getPlatforms()) {
            if (platform.getDatasets() == null) continue;
            for (OpenDataDto.DatasetSummary ds : platform.getDatasets()) {
                order++;
                upsertResource(ds, platform, order);
            }
        }
    }

    private void syncProcessedResources() {
        upsertProcessed("health-resources/HOSPITAL_DIRECTORY_2024.json",
                "全国医院目录（2024）", "医疗数据", "国家卫健委公开信息", "RESEARCHER", 901);
        upsertProcessed("health-resources/NATIONAL_DRUG_CATALOG_2025.json",
                "国家医保药品目录（2025）", "医疗数据", "国家医保局", "RESEARCHER", 902);
        upsertProcessed("health-resources/PUBLIC_TERTIARY_HOSPITALS.json",
                "全国公立三甲医院名录", "医疗数据", "国家卫健委", "RESEARCHER", 903);
    }

    private void upsertResource(OpenDataDto.DatasetSummary ds, OpenDataDto.Platform platform, int sortOrder) {
        String openId = ds.getId();
        PortalDataResource existing = resourceMapper.selectOne(new LambdaQueryWrapper<PortalDataResource>()
                .eq(PortalDataResource::getOpenDataId, openId)
                .last("LIMIT 1"));
        PortalDataResource r = existing != null ? existing : new PortalDataResource();
        r.setCode(openId);
        r.setOpenDataId(openId);
        r.setName(ds.getTitle());
        r.setCategory(ds.getCategory() != null && !ds.getCategory().isBlank() ? ds.getCategory() : "统计数据");
        r.setSource(platform.getSource());
        r.setDescription(buildDescription(ds, platform));
        r.setDataType("table".equals(ds.getType()) ? "CSV" : "结构化");
        r.setSizeLabel(OpenDataExportService.formatFileSize(openDataExportService.estimateExportBytes(openId)));
        r.setPermissionLevel(resolvePermission(openId, ds.getTitle()));
        r.setStatus(1);
        r.setSortOrder(sortOrder);
        if (existing == null) {
            r.setCreatedAt(LocalDateTime.now());
            resourceMapper.insert(r);
        } else {
            resourceMapper.updateById(r);
        }
    }

    private void upsertProcessed(String relativePath, String name, String category, String source,
                                 String permission, int sortOrder) {
        String openId = "processed:" + relativePath;
        String code = "processed-" + relativePath.replace("/", "-").replace(".json", "");
        PortalDataResource existing = resourceMapper.selectOne(new LambdaQueryWrapper<PortalDataResource>()
                .eq(PortalDataResource::getOpenDataId, openId)
                .last("LIMIT 1"));
        PortalDataResource r = existing != null ? existing : new PortalDataResource();
        r.setCode(code);
        r.setOpenDataId(openId);
        r.setName(name);
        r.setCategory(category);
        r.setSource(source);
        r.setDescription("项目已导入的加工数据资源（JSON）");
        r.setDataType("JSON");
        r.setSizeLabel(OpenDataExportService.formatFileSize(openDataExportService.estimateExportBytes(openId)));
        r.setPermissionLevel(permission);
        r.setStatus(1);
        r.setSortOrder(sortOrder);
        if (existing == null) {
            r.setCreatedAt(LocalDateTime.now());
            resourceMapper.insert(r);
        } else {
            resourceMapper.updateById(r);
        }
    }

    private String resolvePermission(String openId, String title) {
        if (openId != null && openId.startsWith("nbs-")) {
            return "STANDARD";
        }
        if (title != null) {
            for (String kw : RESEARCHER_KEYWORDS) {
                if (title.contains(kw)) return "RESEARCHER";
            }
        }
        return "STANDARD";
    }

    private String buildDescription(OpenDataDto.DatasetSummary ds, OpenDataDto.Platform platform) {
        StringBuilder sb = new StringBuilder();
        sb.append(platform.getName());
        if (ds.getDistrict() != null && !ds.getDistrict().isBlank()) {
            sb.append(" · ").append(ds.getDistrict());
        }
        if (ds.getTimeRange() != null && !ds.getTimeRange().isBlank()) {
            sb.append(" · ").append(ds.getTimeRange());
        } else if (ds.getOpenType() != null && !ds.getOpenType().isBlank()) {
            sb.append(" · ").append(ds.getOpenType());
        }
        return sb.toString();
    }
}
