package com.csu.health.portal.module.datapool.service;

import com.csu.health.portal.module.datapool.dto.DataPoolDto;
import com.csu.health.portal.module.opendata.dto.OpenDataDto;
import com.csu.health.portal.module.opendata.service.OpenDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DataPoolService {

    private final OpenDataService openDataService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.data-pool.project-root:..}")
    private String projectRoot;

    private final AtomicBoolean collecting = new AtomicBoolean(false);
    private JsonNode lastRun;

    public DataPoolService(OpenDataService openDataService, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.openDataService = openDataService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void loadLastRun() {
        lastRun = readJsonResource("data/crawl/last-run.json");
    }

    public DataPoolDto.Architecture architecture() {
        DataPoolDto.Architecture arch = new DataPoolDto.Architecture();
        arch.setTitle("统一数据资源池与计算平台");
        arch.setDescription(
                "整合政府开放数据、合规互联网采集数据和外部导入表格，形成可追溯、可审计、可检索的健康大数据资源池。");
        arch.setLayers(List.of(
                layer("采集层", "开放数据同步 + 合规公开网页采集 + 外部Excel导入",
                        List.of("Python合规采集脚本", "国家/地方开放数据", "权威医疗健康公开信息", "人工审核后的外部导入表格"),
                        "已实现"),
                layer("存储层", "多源数据统一落库与文件归档",
                        List.of("MySQL业务库", "data/external-import原始表格", "data/processed标准化JSON", "data/crawl采集结果"),
                        "已实现"),
                layer("治理层", "来源追溯、导入日志、错误记录和状态展示",
                        List.of("data_resource_dataset", "data_resource_import_run", "source_file/source_row字段", "导入前dry-run审计"),
                        "已实现"),
                layer("服务层", "统一数据API",
                        List.of("/api/portal/open-data", "/api/portal/data-pool", "/api/portal/medical", "/api/portal/contents"),
                        "已实现"),
                layer("展示层", "门户与可视化",
                        List.of("健康百科", "医疗资源", "数据资源池", "互联网资讯池"),
                        "已实现"),
                layer("扩展层", "大数据组件预留",
                        List.of("MinIO对象存储", "Spark批处理", "ETL批量脚本", "Hadoop HDFS生产扩展"),
                        "预留")
        ));
        arch.setStats(buildStats());
        arch.setDatasets(listResourceDatasets());
        arch.setTechStack(List.of("Spring Boot", "MyBatis", "MySQL", "Redis", "Python", "Vue3", "Element Plus", "ECharts", "Spark/MinIO(扩展)"));
        return arch;
    }

    public DataPoolDto.PoolStats buildStats() {
        OpenDataDto.Catalog catalog = openDataService.catalog();
        int nbs = 0;
        int shanghai = 0;
        int openRecords = 0;
        if (catalog.getPlatforms() != null) {
            for (OpenDataDto.Platform platform : catalog.getPlatforms()) {
                int datasetCount = platform.getDatasets() == null ? 0 : platform.getDatasets().size();
                if ("nbs".equals(platform.getId())) {
                    nbs = datasetCount;
                } else if ("shanghai".equals(platform.getId())) {
                    shanghai = datasetCount;
                }
                if (platform.getDatasets() != null) {
                    openRecords += platform.getDatasets().stream()
                            .mapToInt(OpenDataDto.DatasetSummary::getRowCount)
                            .sum();
                }
            }
        }

        int internet = countInternetItems();
        int files = countOpenFiles();
        List<DataPoolDto.ResourceDataset> datasets = listResourceDatasets();
        int healthRecords = datasets.stream().mapToInt(DataPoolDto.ResourceDataset::getRecordCount).sum();

        DataPoolDto.PoolStats stats = new DataPoolDto.PoolStats();
        stats.setNbsDatasets(nbs);
        stats.setShanghaiDatasets(shanghai);
        stats.setInternetItems(internet);
        stats.setOpenDataFiles(files);
        stats.setHealthResourceDatasets(datasets.size());
        stats.setHealthResourceRecords(healthRecords);
        stats.setTotalRecords(openRecords + internet + healthRecords);
        if (lastRun != null && lastRun.has("finishedAt")) {
            stats.setLastCollectTime(lastRun.get("finishedAt").asText());
        }
        return stats;
    }

    public List<DataPoolDto.ResourceDataset> listResourceDatasets() {
        try {
            String sql = """
                    SELECT dataset_code, dataset_name, dataset_type AS source_type, source_name, source_file,
                           update_status AS status, record_count, error_count, last_imported_at, source_url AS official_url,
                           failure_reason AS description
                    FROM data_resource_dataset
                    ORDER BY
                        CASE update_status
                            WHEN 'SUCCESS' THEN 1
                            WHEN 'IMPORTED' THEN 1
                            WHEN 'EXPORTED' THEN 2
                            WHEN 'DRY_RUN' THEN 3
                            ELSE 4
                        END,
                        dataset_name
                    """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                DataPoolDto.ResourceDataset d = new DataPoolDto.ResourceDataset();
                d.setDatasetCode(rs.getString("dataset_code"));
                d.setDatasetName(rs.getString("dataset_name"));
                d.setSourceType(rs.getString("source_type"));
                d.setSourceName(rs.getString("source_name"));
                d.setSourceFile(rs.getString("source_file"));
                d.setStatus(rs.getString("status"));
                d.setRecordCount(rs.getInt("record_count"));
                d.setErrorCount(rs.getInt("error_count"));
                d.setLastImportedAt(rs.getString("last_imported_at"));
                d.setOfficialUrl(rs.getString("official_url"));
                d.setDescription(rs.getString("description"));
                return d;
            });
        } catch (Exception e) {
            log.debug("data_resource_dataset is not available yet: {}", e.getMessage());
            return List.of();
        }
    }

    public List<DataPoolDto.InternetFeed> internetFeeds() {
        List<DataPoolDto.InternetFeed> feeds = new ArrayList<>();
        for (String id : List.of("gov_cn_shuju", "stats_gov_cn_sj")) {
            JsonNode node = readJsonResource("data/crawl/" + id + ".json");
            if (node == null || !node.has("items")) {
                continue;
            }
            DataPoolDto.InternetFeed feed = new DataPoolDto.InternetFeed();
            feed.setSourceId(node.path("sourceId").asText());
            feed.setSourceName(node.path("sourceName").asText());
            feed.setAttribution(node.path("items").isEmpty() ? "" :
                    node.path("items").get(0).path("attribution").asText());
            List<DataPoolDto.InternetItem> items = new ArrayList<>();
            for (JsonNode item : node.get("items")) {
                DataPoolDto.InternetItem i = new DataPoolDto.InternetItem();
                i.setTitle(item.path("title").asText());
                i.setUrl(item.path("url").asText());
                i.setCollectedAt(item.path("collectedAt").asText());
                items.add(i);
            }
            feed.setItems(items);
            feeds.add(feed);
        }
        return feeds;
    }

    public DataPoolDto.CollectStatus collectStatus() {
        DataPoolDto.CollectStatus status = new DataPoolDto.CollectStatus();
        status.setRunning(collecting.get());
        if (lastRun != null) {
            status.setStartedAt(text(lastRun, "startedAt"));
            status.setFinishedAt(text(lastRun, "finishedAt"));
            status.setImports(readStringList(lastRun.get("imports")));
            List<DataPoolDto.SourceResult> sources = new ArrayList<>();
            if (lastRun.has("sources")) {
                for (JsonNode s : lastRun.get("sources")) {
                    DataPoolDto.SourceResult r = new DataPoolDto.SourceResult();
                    r.setSourceId(s.path("sourceId").asText());
                    r.setSourceName(s.path("sourceName").asText());
                    r.setStatus(s.path("status").asText());
                    r.setRecordCount(s.path("recordCount").asInt());
                    if (s.has("error")) {
                        r.setError(s.path("error").asText());
                    }
                    sources.add(r);
                }
            }
            status.setSources(sources);
        }
        return status;
    }

    public DataPoolDto.BigDataStatus bigDataStatus() {
        DataPoolDto.BigDataStatus s = new DataPoolDto.BigDataStatus();
        s.setSparkUrl(System.getenv().getOrDefault("APP_BIGDATA_SPARK_URL", "http://localhost:8081"));
        s.setMinioUrl(System.getenv().getOrDefault("APP_BIGDATA_MINIO_URL", "http://localhost:9000"));
        s.setStorageLayer("MySQL + data/文件资源池");
        s.setComputeLayer("Python ETL + Spring聚合服务");
        s.setDockerAvailable(checkDockerHint());
        JsonNode etl = readJsonResource("data/processed/pool-summary.json");
        if (etl != null) {
            s.setEtlEngine(etl.path("engine").asText());
            s.setEtlSummary(objectMapper.convertValue(etl, Map.class));
        }
        s.setMessage(s.isDockerAvailable()
                ? "大数据扩展层可通过 docker compose --profile bigdata 启动"
                : "Docker未运行：当前使用本地ETL脚本 + MySQL/文件资源池，核心功能不受影响");
        return s;
    }

    public DataPoolDto.BigDataStatus triggerEtl() {
        try {
            Path root = resolveProjectRoot();
            ProcessBuilder pb = new ProcessBuilder("python", root.resolve("scripts").resolve("spark_etl_batch.py").toString());
            pb.directory(root.toFile());
            int code = pb.start().waitFor();
            if (code != 0) {
                throw new RuntimeException("ETL退出码 " + code);
            }
        } catch (Exception e) {
            throw new RuntimeException("ETL执行失败: " + e.getMessage());
        }
        return bigDataStatus();
    }

    public DataPoolDto.CollectStatus triggerCollect() {
        if (!collecting.compareAndSet(false, true)) {
            return collectStatus();
        }
        try {
            Path root = resolveProjectRoot();
            Path script = root.resolve("scripts").resolve("crawl_open_data.py");
            if (!Files.exists(script)) {
                throw new IllegalStateException("采集脚本不存在: " + script);
            }
            ProcessBuilder pb = new ProcessBuilder("python", script.toString());
            pb.directory(root.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output;
            try (InputStream in = p.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            int code = p.waitFor();
            log.info("Crawl finished code={} output={}", code, output);
            openDataService.reload();
            loadLastRun();
        } catch (Exception e) {
            log.error("Crawl failed", e);
            throw new RuntimeException("数据采集失败: " + e.getMessage());
        } finally {
            collecting.set(false);
        }
        return collectStatus();
    }

    private boolean checkDockerHint() {
        String spark = System.getenv("APP_BIGDATA_SPARK_URL");
        return spark != null && spark.contains("spark:");
    }

    private Path resolveProjectRoot() {
        Path candidate = Paths.get(projectRoot).toAbsolutePath().normalize();
        if (Files.exists(candidate.resolve("scripts").resolve("crawl_open_data.py"))) {
            return candidate;
        }
        return Paths.get(System.getProperty("user.dir")).getParent();
    }

    private static DataPoolDto.Layer layer(String name, String role, List<String> components, String status) {
        DataPoolDto.Layer l = new DataPoolDto.Layer();
        l.setName(name);
        l.setRole(role);
        l.setComponents(components);
        l.setStatus(status);
        return l;
    }

    private int countInternetItems() {
        int n = 0;
        for (String id : List.of("gov_cn_shuju", "stats_gov_cn_sj")) {
            JsonNode node = readJsonResource("data/crawl/" + id + ".json");
            if (node != null && node.has("recordCount")) {
                n += node.get("recordCount").asInt();
            }
        }
        return n;
    }

    private int countOpenFiles() {
        int n = 0;
        if (lastRun != null && lastRun.has("sources")) {
            for (JsonNode s : lastRun.get("sources")) {
                if (s.has("files")) {
                    n += s.get("files").size();
                }
            }
        }
        return n;
    }

    private JsonNode readJsonResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        node.forEach(n -> list.add(n.asText()));
        return list;
    }
}
