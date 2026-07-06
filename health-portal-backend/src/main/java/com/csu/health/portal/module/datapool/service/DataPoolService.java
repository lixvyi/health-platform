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
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DataPoolService {

    private final OpenDataService openDataService;
    private final ObjectMapper objectMapper;

    @Value("${app.data-pool.project-root:..}")
    private String projectRoot;

    private final AtomicBoolean collecting = new AtomicBoolean(false);
    private JsonNode lastRun;

    public DataPoolService(OpenDataService openDataService, ObjectMapper objectMapper) {
        this.openDataService = openDataService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadLastRun() {
        lastRun = readJsonResource("data/crawl/last-run.json");
    }

    public DataPoolDto.Architecture architecture() {
        DataPoolDto.Architecture arch = new DataPoolDto.Architecture();
        arch.setTitle("健康大数据统一数据资源池与计算平台");
        arch.setDescription(
                "依托政务数据共享授权与政府开放数据平台，结合互联网公开信息采集，形成分层存储、批处理 ETL、缓存加速的统一数据服务架构。");
        arch.setLayers(List.of(
                layer("采集层", "互联网爬虫 + 开放数据同步",
                        List.of("Python 合规采集脚本", "中国政府网数据栏目", "国家统计局官网公开页", "上海/国家统计 CSV·Excel 同步"),
                        "已实现"),
                layer("存储层", "多源数据资源池",
                        List.of("MySQL 业务库", "Redis 热点缓存", "文件湖 data/open-data/", "JSON 资源目录"),
                        "已实现"),
                layer("计算层", "ETL 批处理与聚合",
                        List.of("Excel/CSV 解析入库", "指标聚合 API", "定时采集任务", "Spring Boot 统计服务"),
                        "已实现"),
                layer("服务层", "统一数据 API",
                        List.of("/api/portal/open-data", "/api/portal/data-pool", "JWT 管理接口"),
                        "已实现"),
                layer("展示层", "门户与可视化",
                        List.of("Vue3 + ECharts", "数据资源目录", "互联网资讯池", "管理后台"),
                        "已实现"),
                layer("扩展层", "大数据组件（Docker Profile: bigdata）",
                        List.of("MinIO 对象存储(数据湖)", "Spark Standalone 批计算", "ETL 批处理脚本", "Hadoop HDFS(生产扩展)"),
                        "Docker 配置就绪")
        ));
        arch.setStats(buildStats());
        arch.setTechStack(List.of("Spring Boot", "MyBatis", "Redis", "MySQL", "Python", "Vue3", "ECharts", "Hadoop/Spark(扩展)"));
        return arch;
    }

    public DataPoolDto.PoolStats buildStats() {
        OpenDataDto.Catalog catalog = openDataService.catalog();
        int nbs = 0, sh = 0, records = 0;
        if (catalog.getPlatforms() != null) {
            for (OpenDataDto.Platform p : catalog.getPlatforms()) {
                int c = p.getDatasets() == null ? 0 : p.getDatasets().size();
                if ("nbs".equals(p.getId())) {
                    nbs = c;
                } else if ("shanghai".equals(p.getId())) {
                    sh = c;
                }
                if (p.getDatasets() != null) {
                    records += p.getDatasets().stream().mapToInt(OpenDataDto.DatasetSummary::getRowCount).sum();
                }
            }
        }
        int internet = countInternetItems();
        int files = countOpenFiles();
        DataPoolDto.PoolStats stats = new DataPoolDto.PoolStats();
        stats.setNbsDatasets(nbs);
        stats.setShanghaiDatasets(sh);
        stats.setInternetItems(internet);
        stats.setOpenDataFiles(files);
        stats.setTotalRecords(records + internet);
        if (lastRun != null && lastRun.has("finishedAt")) {
            stats.setLastCollectTime(lastRun.get("finishedAt").asText());
        }
        return stats;
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
        s.setStorageLayer("MinIO / 文件湖 data/");
        s.setComputeLayer("Spark ETL + Spring 聚合");
        s.setDockerAvailable(checkDockerHint());
        JsonNode etl = readJsonResource("data/processed/pool-summary.json");
        if (etl != null) {
            s.setEtlEngine(etl.path("engine").asText());
            s.setEtlSummary(objectMapper.convertValue(etl, Map.class));
        }
        s.setMessage(s.isDockerAvailable()
                ? "大数据扩展层可通过 docker compose --profile bigdata 启动"
                : "Docker 未运行：使用本地 ETL 脚本 + MySQL/文件湖，功能不受影响");
        return s;
    }

    public DataPoolDto.BigDataStatus triggerEtl() {
        try {
            Path root = resolveProjectRoot();
            ProcessBuilder pb = new ProcessBuilder("python", root.resolve("scripts").resolve("spark_etl_batch.py").toString());
            pb.directory(root.toFile());
            Process p = pb.start();
            int code = p.waitFor();
            if (code != 0) {
                throw new RuntimeException("ETL 退出码 " + code);
            }
        } catch (Exception e) {
            throw new RuntimeException("ETL 执行失败: " + e.getMessage());
        }
        return bigDataStatus();
    }

    private boolean checkDockerHint() {
        String spark = System.getenv("APP_BIGDATA_SPARK_URL");
        return spark != null && spark.contains("spark:");
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
