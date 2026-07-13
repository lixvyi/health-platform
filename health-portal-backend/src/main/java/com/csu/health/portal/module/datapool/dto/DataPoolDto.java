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
        private List<ResourceDataset> datasets;
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
        private int healthResourceDatasets;
        private int healthResourceRecords;
        private int totalRecords;
        private String lastCollectTime;
    }

    @Data
    public static class ResourceDataset {
        private String datasetCode;
        private String datasetName;
        private String sourceType;
        private String sourceName;
        private String sourceFile;
        private String status;
        private int recordCount;
        private int errorCount;
        private String lastImportedAt;
        private String officialUrl;
        private String description;
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

    @Data
    public static class GovernanceDashboard {
        private String generatedAt;
        private GovernanceSummary summary;
        private List<GovernanceTrendPoint> trends;
        private List<GovernanceSource> sources;
        private List<GovernanceIssue> issues;
        private Map<String, Integer> freshnessDistribution;
    }

    @Data
    public static class GovernanceSummary {
        private int sourceCount;
        private int healthySourceCount;
        private long totalRecords;
        private long latestAddedRecords;
        private double averageSuccessRate;
        private Double duplicateRatio;
        private Double missingFieldRatio;
        private int freshSourceCount;
        private int openIssueCount;
    }

    @Data
    public static class GovernanceTrendPoint {
        private String date;
        private long totalRecords;
        private long addedRecords;
    }

    @Data
    public static class GovernanceSource {
        private String datasetCode;
        private String datasetName;
        private String sourceType;
        private String sourceName;
        private String lastCollectedAt;
        private double successRate;
        private long totalRecords;
        private long latestAddedRecords;
        private long processedRecords;
        private long duplicateRecords;
        private long errorRecords;
        private Double duplicateRatio;
        private Double missingFieldRatio;
        private String freshnessLevel;
        private String freshnessText;
        private Long freshnessDays;
        private String licenseName;
        private String originalUrl;
        private String sourceFile;
        private String anomalyStatus;
        private int anomalyCount;
    }

    @Data
    public static class GovernanceIssue {
        private Long id;
        private String datasetCode;
        private String sourceName;
        private String issueType;
        private String description;
        private String detectedAt;
        private String status;
        private String handlerNote;
        private String handledBy;
        private String handledAt;
    }

    @Data
    public static class GovernanceIssueUpdate {
        private String status;
        private String handlerNote;
        private String handledBy;
    }
}
