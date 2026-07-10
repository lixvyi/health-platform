package com.csu.health.portal.module.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.common.Result;
import com.csu.health.portal.module.content.entity.CmsContent;
import com.csu.health.portal.module.content.entity.KnowledgeCategory;
import com.csu.health.portal.module.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "公众门户")
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalController {

    private final ContentService contentService;

    @Operation(summary = "首页数据")
    @GetMapping("/home")
    public Result<Map<String, Object>> home() {
        return Result.ok(contentService.homeData());
    }

    @Operation(summary = "内容分页")
    @GetMapping("/contents")
    public Result<Page<CmsContent>> contents(
            @RequestParam String categoryCode,
            @RequestParam(required = false) String knowledgeCategoryCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(contentService.pagePublished(
                categoryCode, knowledgeCategoryCode, keyword, page, size));
    }

    @Operation(summary = "内容详情")
    @GetMapping("/contents/{id}")
    public Result<CmsContent> detail(@PathVariable Long id) {
        return Result.ok(contentService.getPublishedDetail(id));
    }

    @Operation(summary = "健康百科分类")
    @GetMapping("/knowledge/categories")
    public Result<List<KnowledgeCategory>> knowledgeCategories() {
        return Result.ok(contentService.listKnowledgeCategories());
    }

    @Operation(summary = "相关推荐")
    @GetMapping("/contents/{id}/related")
    public Result<List<CmsContent>> related(
            @PathVariable Long id,
            @RequestParam(defaultValue = "6") int limit) {
        return Result.ok(contentService.relatedPublished(id, limit));
    }

    @Operation(summary = "轮播图")
    @GetMapping("/banners")
    public Result<?> banners() {
        return Result.ok(contentService.listBanners());
    }

    @Operation(summary = "应用中心")
    @GetMapping("/apps")
    public Result<?> apps() {
        return Result.ok(contentService.listApps());
    }

    @Operation(summary = "关于我们")
    @GetMapping("/about")
    public Result<Map<String, Object>> about() {
        return Result.ok(contentService.getAbout());
    }

    @Operation(summary = "统计数据")
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.ok(contentService.stats());
    }

    @Operation(summary = "按关键词搜索政策")
    @GetMapping("/policies-by-word")
    public Result<List<Map<String, String>>> policiesByWord(@RequestParam String word) {
        List<Map<String, String>> result = new ArrayList<>();
        // 从项目根目录读取政策 CSV 文件
        File csvDir = new File("../data/policies");
        if (!csvDir.exists()) csvDir = new File("data/policies");
        File[] csvFiles = csvDir.listFiles((dir, name) -> name.endsWith(".csv"));
        if (csvFiles == null) return Result.ok(result);
        for (File csvFile : csvFiles) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    new FileInputStream(csvFile), StandardCharsets.UTF_8))) {
                String header = br.readLine(); // skip header
                if (header == null) continue;
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    // 解析 CSV：title,date,link,...
                    // title 可能被引号包裹
                    String title = "", date = "", link = "";
                    int idx = 0;
                    if (line.startsWith("\"")) {
                        int end = line.indexOf('"', 1);
                        if (end > 0) {
                            title = line.substring(1, end);
                            idx = end + 2; // skip ",
                        }
                    } else {
                        int comma = line.indexOf(',');
                        if (comma > 0) {
                            title = line.substring(0, comma);
                            idx = comma + 1;
                        }
                    }
                    // 提取 date
                    if (idx < line.length()) {
                        int nextComma = line.indexOf(',', idx);
                        if (nextComma > idx) {
                            date = line.substring(idx, nextComma);
                            idx = nextComma + 1;
                            // 提取 link
                            int linkComma = line.indexOf(',', idx);
                            if (linkComma > idx) {
                                link = line.substring(idx, linkComma);
                            } else {
                                link = line.substring(idx);
                            }
                        }
                    }
                    if (!title.isEmpty() && title.toLowerCase().contains(word.toLowerCase())) {
                        Map<String, String> item = new LinkedHashMap<>();
                        item.put("title", title);
                        item.put("date", date);
                        item.put("link", link);
                        result.add(item);
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return Result.ok(result);
    }

    // 热词列表（已在 Python 脚本中维护）

    @Operation(summary = "热词共现网络")
    @GetMapping("/cooccurrence")
    public Result<Map<String, Object>> cooccurrence(@RequestParam int year) {
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", Collections.emptyList());
        result.put("edges", Collections.emptyList());

        try {
            // 定位 Python 脚本
            String scriptPath = findScriptPath();
            if (scriptPath == null) return Result.ok(result);

            // 构建进程：python compute_cooccurrence.py <year>
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, String.valueOf(year));
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            int exitCode = process.waitFor();

            if (exitCode == 0 && !output.isEmpty()) {
                // 解析 JSON
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(output, Map.class);
                result = parsed;
            }
        } catch (Exception e) {
            // 返回空结果
        }
        return Result.ok(result);
    }

    private String findScriptPath() {
        // 尝试多个可能的脚本路径
        String[] candidates = {
                "../scripts/compute_cooccurrence.py",
                "scripts/compute_cooccurrence.py",
                "compute_cooccurrence.py"
        };
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) return f.getAbsolutePath();
        }
        return null;
    }
}
