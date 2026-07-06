package com.csu.health.portal.module.opendata.service;

import com.csu.health.portal.module.opendata.dto.OpenDataDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OpenDataService {

    private static final List<String> NBS_FEATURED = List.of(
            "nbs-01:卫生技术人员数 (万人)",
            "nbs-04:医院床位数 (万张)",
            "nbs-25:卫生总费用 (亿元)"
    );

    private final ObjectMapper objectMapper;
    private final Map<String, JsonNode> datasetMap = new LinkedHashMap<>();
    private OpenDataDto.Catalog catalog = new OpenDataDto.Catalog();

    public OpenDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        reload();
    }

    public void reload() {
        datasetMap.clear();
        catalog = new OpenDataDto.Catalog();
        catalog.setPlatforms(new ArrayList<>());
        loadBundle("data/nbs-health-stats.json", "nbs", "国家统计数据库");
        loadBundle("data/shanghai-health-open-data.json", "shanghai", "上海市公共数据开放平台");
        catalog.setTotalDatasets(datasetMap.size());
        log.info("Open data catalog reloaded: {} datasets", datasetMap.size());
    }

    private void loadBundle(String resource, String platformId, String platformName) {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            ArrayNode datasets = (ArrayNode) root.get("datasets");
            if (datasets == null) {
                return;
            }
            OpenDataDto.Platform platform = new OpenDataDto.Platform();
            platform.setId(platformId);
            platform.setName(platformName);
            platform.setSource(text(root, "source"));
            platform.setSourceUrl(text(root, "sourceUrl"));
            platform.setAttribution(text(root, "attribution"));
            List<OpenDataDto.DatasetSummary> summaries = new ArrayList<>();

            for (JsonNode ds : datasets) {
                String id = text(ds, "id");
                datasetMap.put(id, ds);
                summaries.add(toSummary(ds, platform));
            }
            platform.setDatasets(summaries);
            if (catalog.getPlatforms() == null) {
                catalog.setPlatforms(new ArrayList<>());
            }
            catalog.getPlatforms().add(platform);
            log.info("Loaded {} datasets from {}", datasets.size(), resource);
        } catch (Exception e) {
            log.warn("Failed to load {}: {}", resource, e.getMessage());
        }
    }

    public OpenDataDto.Catalog catalog() {
        return catalog;
    }

    /** @deprecated use catalog() */
    public OpenDataDto.Catalog meta() {
        return catalog();
    }

    public JsonNode getDataset(String id) {
        JsonNode dataset = datasetMap.get(id);
        if (dataset == null) {
            throw new NoSuchElementException("数据集不存在: " + id);
        }
        return dataset;
    }

    public List<OpenDataDto.FeaturedChart> featured() {
        List<OpenDataDto.FeaturedChart> charts = new ArrayList<>();
        for (String spec : NBS_FEATURED) {
            String[] parts = spec.split(":", 2);
            JsonNode dataset = datasetMap.get(parts[0]);
            if (dataset == null || !dataset.has("indicators")) {
                continue;
            }
            String indicatorName = parts[1];
            for (JsonNode ind : dataset.get("indicators")) {
                if (!indicatorName.equals(text(ind, "name"))) {
                    continue;
                }
                OpenDataDto.FeaturedChart chart = new OpenDataDto.FeaturedChart();
                chart.setDatasetId(parts[0]);
                chart.setDatasetTitle(text(dataset, "title"));
                chart.setIndicatorName(indicatorName);
                chart.setValues(readValues(ind.get("values")));
                chart.setUnit(extractUnit(indicatorName));
                chart.setSource(text(dataset, "source"));
                chart.setAttribution(text(dataset, "sourceUrl").isEmpty()
                        ? "来源：国家统计局" : "来源：国家统计局");
                charts.add(chart);
            }
        }
        return charts;
    }

    private OpenDataDto.DatasetSummary toSummary(JsonNode ds, OpenDataDto.Platform platform) {
        OpenDataDto.DatasetSummary s = new OpenDataDto.DatasetSummary();
        s.setId(text(ds, "id"));
        if (ds.hasNonNull("fileIndex")) {
            s.setFileIndex(ds.get("fileIndex").asInt());
        }
        s.setTitle(text(ds, "title"));
        s.setCategory(text(ds, "category"));
        s.setDistrict(text(ds, "district"));
        s.setType(text(ds, "type").isEmpty() ? "chart" : text(ds, "type"));
        s.setTimeRange(text(ds, "timeRange"));
        s.setOpenType(text(ds, "openType"));
        s.setSource(platform.getSource());
        s.setAttribution(platform.getAttribution());
        s.setRowCount(ds.has("rowCount") ? ds.get("rowCount").asInt() : 0);
        if (ds.has("indicators")) {
            s.setIndicatorCount(ds.get("indicators").size());
        } else if (ds.has("rows")) {
            s.setRowCount(ds.get("rows").size());
        }
        return s;
    }

    private static Map<String, Double> readValues(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Double> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asDouble()));
        return map;
    }

    private static String extractUnit(String name) {
        int start = name.indexOf('(');
        int end = name.indexOf(')');
        if (start >= 0 && end > start) {
            return name.substring(start + 1, end);
        }
        return "";
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }
}
