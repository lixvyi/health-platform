package com.csu.health.portal.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.csu.health.portal.module.ai.dto.AiKnowledgeRef;
import com.csu.health.portal.module.content.entity.CmsApp;
import com.csu.health.portal.module.content.entity.CmsContent;
import com.csu.health.portal.module.content.mapper.CmsAppMapper;
import com.csu.health.portal.module.content.mapper.CmsContentMapper;
import com.csu.health.portal.module.opendata.dto.OpenDataDto;
import com.csu.health.portal.module.opendata.service.OpenDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiKnowledgeContextService {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern NOT_CJK = Pattern.compile("[^\\u4e00-\\u9fff\\w]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "什么", "怎么", "如何", "哪些", "是否", "可以", "请问", "关于", "有关", "一下", "告诉", "介绍"
    );

    private static final List<PortalLink> DEFAULT_LINKS = List.of(
            new PortalLink("国家统计数据库", "https://data.stats.gov.cn", "国家统计局官方开放统计数据"),
            new PortalLink("国家统计局数据发布", "https://www.stats.gov.cn/sj/", "最新统计公报与数据解读"),
            new PortalLink("中国政府网数据栏目", "http://www.gov.cn/shuju/index.htm", "宏观经济与社会发展数据"),
            new PortalLink("上海公共数据开放平台", "https://data.sh.gov.cn/", "上海市开放健康相关数据集"),
            new PortalLink("门户数据资源目录", "/data", "本门户 45 类开放健康数据"),
            new PortalLink("统一数据资源池", "/data-pool", "爬虫采集 + 开放数据 + ETL 汇总")
    );

    private final CmsContentMapper contentMapper;
    private final CmsAppMapper appMapper;
    private final OpenDataService openDataService;
    private final ObjectMapper objectMapper;

    @Data
    public static class KnowledgeBundle {
        private String contextText = "";
        private String linkText = "";
        private List<AiKnowledgeRef> references = new ArrayList<>();
        private boolean hasPolicyMatch;
    }

    public KnowledgeBundle retrieve(String question) {
        List<String> terms = extractTerms(question);
        List<ScoredRef> scored = new ArrayList<>();

        scored.addAll(searchCms("POLICY", "policy", terms, 3.0));
        scored.addAll(searchCms("KNOWLEDGE", "knowledge", terms, 2.5));
        scored.addAll(searchCms("NOTICE", "notice", terms, 1.5));
        scored.addAll(searchCms("NEWS", "news", terms, 1.0));
        scored.addAll(searchInternetFeeds(terms));
        scored.addAll(searchOpenData(terms));
        scored.addAll(searchApps(terms));

        scored.sort(Comparator.comparingDouble(ScoredRef::score).reversed());

        LinkedHashMap<String, ScoredRef> deduped = new LinkedHashMap<>();
        for (ScoredRef item : scored) {
            String key = item.ref().getTitle() + "|" + nullSafe(item.ref().getUrl());
            deduped.putIfAbsent(key, item);
        }

        List<ScoredRef> top = deduped.values().stream().limit(8).toList();
        boolean hasPolicy = top.stream().anyMatch(s -> "policy".equals(s.ref().getType()));

        KnowledgeBundle bundle = new KnowledgeBundle();
        bundle.setHasPolicyMatch(hasPolicy);
        bundle.setReferences(top.stream().map(ScoredRef::ref).toList());

        StringBuilder ctx = new StringBuilder();
        for (ScoredRef s : top) {
            AiKnowledgeRef ref = s.ref();
            if ("policy".equals(ref.getType()) || "knowledge".equals(ref.getType())
                    || "notice".equals(ref.getType()) || "news".equals(ref.getType())) {
                ctx.append("- [").append(ref.getType()).append("] ")
                        .append(ref.getTitle()).append("\n  摘要：")
                        .append(truncate(ref.getExcerpt(), 200)).append("\n");
            } else if ("open-data".equals(ref.getType())) {
                ctx.append("- [开放数据] ").append(ref.getTitle())
                        .append("（").append(ref.getSource()).append("）\n");
            } else if ("internet".equals(ref.getType())) {
                ctx.append("- [互联网公开] ").append(ref.getTitle())
                        .append(" — ").append(ref.getUrl()).append("\n");
            }
        }
        if (ctx.isEmpty()) {
            ctx.append("（门户知识库未检索到高度相关内容，请结合下方推荐链接作答）\n");
        }
        bundle.setContextText(ctx.toString());

        List<PortalLink> links = selectFallbackLinks(question, terms, hasPolicy, top);
        StringBuilder linkBlock = new StringBuilder();
        for (PortalLink link : links) {
            linkBlock.append("- ").append(link.name()).append("：").append(link.url())
                    .append("（").append(link.desc()).append("）\n");
        }
        bundle.setLinkText(linkBlock.toString());
        return bundle;
    }

    private List<ScoredRef> searchCms(String categoryCode, String type, List<String> terms, double weight) {
        List<CmsContent> rows = contentMapper.selectPageByCondition(categoryCode, null, null, 1, 0, 80);
        List<ScoredRef> result = new ArrayList<>();
        for (CmsContent row : rows) {
            String plain = stripHtml(nullSafe(row.getSummary()) + " " + nullSafe(row.getContent()));
            double score = scoreText(row.getTitle() + " " + plain, terms) * weight;
            if (score <= 0 && terms.stream().noneMatch(t -> row.getTitle().contains(t))) {
                continue;
            }
            AiKnowledgeRef ref = new AiKnowledgeRef();
            ref.setType(type);
            ref.setTitle(row.getTitle());
            ref.setUrl("/content/" + row.getId());
            ref.setExcerpt(firstNonBlank(row.getSummary(), plain));
            ref.setSource("门户" + categoryLabel(categoryCode));
            result.add(new ScoredRef(score, ref));
        }
        return result;
    }

    private List<ScoredRef> searchInternetFeeds(List<String> terms) {
        List<ScoredRef> result = new ArrayList<>();
        for (String id : List.of("gov_cn_shuju", "stats_gov_cn_sj")) {
            JsonNode node = readJson("data/crawl/" + id + ".json");
            if (node == null || !node.has("items")) {
                continue;
            }
            String sourceName = node.path("sourceName").asText();
            for (JsonNode item : node.get("items")) {
                String title = item.path("title").asText();
                double score = scoreText(title, terms);
                if (score <= 0) {
                    continue;
                }
                AiKnowledgeRef ref = new AiKnowledgeRef();
                ref.setType("internet");
                ref.setTitle(title);
                ref.setUrl(item.path("url").asText());
                ref.setExcerpt(item.path("attribution").asText());
                ref.setSource(sourceName);
                result.add(new ScoredRef(score * 1.8, ref));
            }
        }
        return result;
    }

    private List<ScoredRef> searchOpenData(List<String> terms) {
        List<ScoredRef> result = new ArrayList<>();
        OpenDataDto.Catalog catalog = openDataService.catalog();
        if (catalog.getPlatforms() == null) {
            return result;
        }
        for (OpenDataDto.Platform platform : catalog.getPlatforms()) {
            if (platform.getDatasets() == null) {
                continue;
            }
            for (OpenDataDto.DatasetSummary ds : platform.getDatasets()) {
                String text = ds.getTitle() + " " + nullSafe(ds.getCategory()) + " " + nullSafe(ds.getDistrict());
                double score = scoreText(text, terms);
                if (score <= 0) {
                    continue;
                }
                AiKnowledgeRef ref = new AiKnowledgeRef();
                ref.setType("open-data");
                ref.setTitle(ds.getTitle());
                ref.setUrl("/data?tab=" + platform.getId());
                ref.setExcerpt(ds.getCategory() + " · " + ds.getRowCount() + " 条记录");
                ref.setSource(platform.getName());
                result.add(new ScoredRef(score * 1.2, ref));
            }
        }
        return result;
    }

    private List<ScoredRef> searchApps(List<String> terms) {
        List<CmsApp> apps = appMapper.selectList(new LambdaQueryWrapper<CmsApp>()
                .eq(CmsApp::getStatus, 1)
                .orderByAsc(CmsApp::getSortOrder));
        List<ScoredRef> result = new ArrayList<>();
        for (CmsApp app : apps) {
            String text = app.getName() + " " + nullSafe(app.getDescription());
            double score = scoreText(text, terms);
            if (score <= 0) {
                continue;
            }
            AiKnowledgeRef ref = new AiKnowledgeRef();
            ref.setType("portal-link");
            ref.setTitle(app.getName());
            ref.setUrl(app.getLinkUrl());
            ref.setExcerpt(app.getDescription());
            ref.setSource("门户应用中心");
            result.add(new ScoredRef(score * 1.5, ref));
        }
        return result;
    }

    private List<PortalLink> selectFallbackLinks(String question, List<String> terms,
                                                   boolean hasPolicy, List<ScoredRef> top) {
        LinkedHashMap<String, PortalLink> links = new LinkedHashMap<>();
        for (PortalLink link : DEFAULT_LINKS) {
            double score = scoreText(link.name() + " " + link.desc() + " " + question, terms);
            if (score > 0 || linkMatchesQuestion(link, question)) {
                links.put(link.url(), link.withScore(score));
            }
        }
        for (ScoredRef s : top) {
            AiKnowledgeRef ref = s.ref();
            if (ref.getUrl() != null && !ref.getUrl().isBlank()) {
                links.putIfAbsent(ref.getUrl(), new PortalLink(ref.getTitle(), ref.getUrl(),
                        nullSafe(ref.getSource()), s.score()));
            }
        }
        if (!hasPolicy) {
            links.putIfAbsent("https://www.gov.cn/zhengce/", new PortalLink(
                    "中国政府网政策库", "https://www.gov.cn/zhengce/", "检索国家级政策文件", 10));
            links.putIfAbsent("https://www.nhc.gov.cn/", new PortalLink(
                    "国家卫生健康委员会", "https://www.nhc.gov.cn/", "卫生健康政策与权威信息", 10));
        }
        return links.values().stream()
                .sorted(Comparator.comparingDouble(PortalLink::score).reversed())
                .limit(6)
                .toList();
    }

    private static boolean linkMatchesQuestion(PortalLink link, String question) {
        String q = question.toLowerCase();
        if (q.contains("统计") || q.contains("gdp") || q.contains("指标")) {
            return link.url().contains("stats.gov.cn");
        }
        if (q.contains("上海") || q.contains("医疗机构")) {
            return link.url().contains("sh.gov.cn") || link.name().contains("上海");
        }
        if (q.contains("政策") || q.contains("规划") || q.contains("纲要")) {
            return link.url().contains("gov.cn");
        }
        if (q.contains("数据") || q.contains("资源池")) {
            return link.url().contains("/data");
        }
        return false;
    }

    private JsonNode readJson(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (Exception e) {
            log.debug("Skip missing resource {}", path);
            return null;
        }
    }

    private static List<String> extractTerms(String question) {
        Set<String> terms = new LinkedHashSet<>();
        String cleaned = NOT_CJK.matcher(question).replaceAll(" ").trim();
        if (cleaned.length() >= 2) {
            terms.add(cleaned);
        }
        for (String part : question.split("\\s+")) {
            if (part.length() >= 2 && !STOP_WORDS.contains(part)) {
                terms.add(part);
            }
        }
        String cn = question.replaceAll("[^\\u4e00-\\u9fff]", "");
        for (int len = Math.min(4, cn.length()); len >= 2; len--) {
            for (int i = 0; i <= cn.length() - len; i++) {
                String slice = cn.substring(i, i + len);
                if (!STOP_WORDS.contains(slice)) {
                    terms.add(slice);
                }
            }
        }
        return terms.stream().limit(24).collect(Collectors.toList());
    }

    private static double scoreText(String text, List<String> terms) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        double score = 0;
        for (String term : terms) {
            if (term.length() < 2) {
                continue;
            }
            if (text.contains(term)) {
                score += term.length() * (term.length() >= 3 ? 1.5 : 1.0);
            }
        }
        return score;
    }

    private static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return HTML_TAG.matcher(html).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return stripHtml(a);
        }
        return truncate(b, 160);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String categoryLabel(String code) {
        return switch (code) {
            case "POLICY" -> "卫生政策";
            case "KNOWLEDGE" -> "健康知识库";
            case "NOTICE" -> "通知公告";
            case "NEWS" -> "新闻中心";
            default -> code;
        };
    }

    private record ScoredRef(double score, AiKnowledgeRef ref) {}

    private record PortalLink(String name, String url, String desc, double score) {
        PortalLink(String name, String url, String desc) {
            this(name, url, desc, 0);
        }

        PortalLink withScore(double score) {
            return new PortalLink(name, url, desc, score);
        }
    }
}
