package com.csu.health.portal.module.opendata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenDataExportService {

    private final OpenDataService openDataService;
    private final ObjectMapper objectMapper;

    @Value("${app.data-pool.project-root:..}")
    private String projectRoot;

    public ExportPayload export(String datasetId) {
        if (datasetId != null && datasetId.startsWith("processed:")) {
            return exportProcessedFile(datasetId.substring("processed:".length()));
        }
        JsonNode dataset = openDataService.getDataset(datasetId);
        String fileName = text(dataset, "fileName");
        if (!fileName.isBlank()) {
            byte[] fromDisk = tryReadShanghaiCsv(fileName);
            if (fromDisk != null) {
                return new ExportPayload(fromDisk, fileName, contentTypeFor(fileName));
            }
        }
        if (dataset.has("rows") && dataset.has("columns")) {
            String name = sanitizeFileName(text(dataset, "title")) + ".csv";
            return new ExportPayload(exportTableCsv(dataset), name, "text/csv");
        }
        if (dataset.has("indicators")) {
            String name = sanitizeFileName(text(dataset, "title")) + ".csv";
            return new ExportPayload(exportIndicatorsCsv(dataset), name, "text/csv");
        }
        try {
            String name = sanitizeFileName(text(dataset, "title")) + ".json";
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(dataset).getBytes(StandardCharsets.UTF_8);
            return new ExportPayload(json, name, "application/json");
        } catch (Exception e) {
            throw new IllegalStateException("导出失败: " + datasetId, e);
        }
    }

    public String resolveFilename(String datasetId) {
        return export(datasetId).filename();
    }

    /** 估算下载文件字节数（优先读磁盘原文件，否则按导出结果计算） */
    public long estimateExportBytes(String datasetId) {
        try {
            if (datasetId != null && datasetId.startsWith("processed:")) {
                Path path = Paths.get(projectRoot, "data", "processed", datasetId.substring("processed:".length())).normalize();
                if (Files.isRegularFile(path)) {
                    return Files.size(path);
                }
            }
            JsonNode dataset = openDataService.getDataset(datasetId);
            String fileName = text(dataset, "fileName");
            if (!fileName.isBlank()) {
                Path path = Paths.get(projectRoot, "data", "open-data", "shanghai", fileName);
                if (Files.isRegularFile(path)) {
                    return Files.size(path);
                }
            }
            return export(datasetId).bytes().length;
        } catch (Exception e) {
            log.debug("Estimate export size failed for {}: {}", datasetId, e.getMessage());
            return 0;
        }
    }

    public static String formatFileSize(long bytes) {
        if (bytes <= 0) {
            return "—";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private byte[] tryReadShanghaiCsv(String fileName) {
        Path path = Paths.get(projectRoot, "data", "open-data", "shanghai", fileName);
        if (!Files.isRegularFile(path)) {
            log.debug("CSV not on disk: {}", path);
            return null;
        }
        try {
            log.info("Export open data from file: {}", path);
            return Files.readAllBytes(path);
        } catch (Exception e) {
            log.warn("Read CSV failed {}: {}", path, e.getMessage());
            return null;
        }
    }

    private ExportPayload exportProcessedFile(String relativePath) {
        Path base = Paths.get(projectRoot, "data", "processed").normalize();
        Path path = Paths.get(projectRoot, "data", "processed", relativePath).normalize();
        if (!path.startsWith(base) || !Files.isRegularFile(path)) {
            throw new IllegalStateException("加工数据文件不存在: " + relativePath);
        }
        try {
            String name = path.getFileName().toString();
            return new ExportPayload(Files.readAllBytes(path), name, contentTypeFor(name));
        } catch (Exception e) {
            throw new IllegalStateException("读取加工数据失败: " + relativePath, e);
        }
    }

    private byte[] exportTableCsv(JsonNode dataset) {
        List<String> columns = new ArrayList<>();
        for (JsonNode col : dataset.get("columns")) {
            columns.add(col.asText());
        }
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(String.join(",", columns.stream().map(this::escapeCsv).toList())).append('\n');
        JsonNode rows = dataset.get("rows");
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                List<String> cells = new ArrayList<>();
                if (row.isObject()) {
                    for (String col : columns) {
                        JsonNode v = row.get(col);
                        cells.add(escapeCsv(v == null || v.isNull() ? "" : v.asText()));
                    }
                }
                sb.append(String.join(",", cells)).append('\n');
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] exportIndicatorsCsv(JsonNode dataset) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("dataset_id,title,indicator,year,value\n");
        String id = text(dataset, "id");
        String title = text(dataset, "title");
        for (JsonNode ind : dataset.get("indicators")) {
            String indicator = text(ind, "name");
            JsonNode values = ind.get("values");
            if (values != null && values.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = values.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    sb.append(escapeCsv(id)).append(',')
                            .append(escapeCsv(title)).append(',')
                            .append(escapeCsv(indicator)).append(',')
                            .append(escapeCsv(e.getKey())).append(',')
                            .append(e.getValue().asText()).append('\n');
                }
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String sanitizeFileName(String title) {
        if (title == null || title.isBlank()) return "dataset";
        return title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }

    private String contentTypeFor(String fileName) {
        if (fileName.endsWith(".json")) return "application/json";
        return "text/csv";
    }

    public record ExportPayload(byte[] bytes, String filename, String contentType) {}
}
