package com.csu.health.portal.module.opendata.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

public class OpenDataDto {

    @Data
    public static class Catalog {
        private List<Platform> platforms;
        private int totalDatasets;
    }

    @Data
    public static class Platform {
        private String id;
        private String name;
        private String source;
        private String sourceUrl;
        private String attribution;
        private List<DatasetSummary> datasets;
    }

    @Data
    public static class DatasetSummary {
        private String id;
        private Integer fileIndex;
        private String title;
        private String category;
        private String district;
        private String type;
        private String timeRange;
        private String openType;
        private String source;
        private String attribution;
        private int rowCount;
        private int indicatorCount;
    }

    @Data
    public static class Indicator {
        private String name;
        private Map<String, Double> values;
    }

    @Data
    public static class FeaturedChart {
        private String datasetId;
        private String datasetTitle;
        private String indicatorName;
        private Map<String, Double> values;
        private String unit;
        private String source;
        private String attribution;
    }
}
