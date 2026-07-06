package com.csu.health.portal.module.datapool.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

public class DataPoolDto {

    @Data
    public static class Architecture {
        private String title;
        private String description;
        private List<Layer> layers;
        private PoolStats stats;
        private List<String> techStack;
    }

    @Data
    public static class Layer {
        private String name;
        private String role;
        private List<String> components;
        private String status;
    }

    @Data
    public static class PoolStats {
        private int nbsDatasets;
        private int shanghaiDatasets;
        private int internetItems;
        private int openDataFiles;
        private int totalRecords;
        private String lastCollectTime;
    }

    @Data
    public static class CollectStatus {
        private String startedAt;
        private String finishedAt;
        private List<SourceResult> sources;
        private List<String> imports;
        private boolean running;
    }

    @Data
    public static class SourceResult {
        private String sourceId;
        private String sourceName;
        private String status;
        private int recordCount;
        private String error;
    }

    @Data
    public static class InternetFeed {
        private String sourceId;
        private String sourceName;
        private String attribution;
        private List<InternetItem> items;
    }

    @Data
    public static class InternetItem {
        private String title;
        private String url;
        private String collectedAt;
    }

    @Data
    public static class BigDataStatus {
        private boolean dockerAvailable;
        private String sparkUrl;
        private String minioUrl;
        private String storageLayer;
        private String computeLayer;
        private Map<String, Object> etlSummary;
        private String etlEngine;
        private String message;
    }
}
