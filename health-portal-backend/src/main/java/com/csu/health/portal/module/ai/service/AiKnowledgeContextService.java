package com.csu.health.portal.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.module.ai.dto.AiFeatureAction;
import com.csu.health.portal.module.ai.dto.AiKnowledgeRef;
import com.csu.health.portal.module.content.entity.CmsApp;
import com.csu.health.portal.module.content.entity.CmsContent;
import com.csu.health.portal.module.content.mapper.CmsAppMapper;
import com.csu.health.portal.module.content.mapper.CmsContentMapper;
import com.csu.health.portal.module.medical.entity.MedicalDrugCatalog;
import com.csu.health.portal.module.medical.entity.MedicalHospital;
import com.csu.health.portal.module.medical.service.MedicalResourceService;
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
            "什么", "怎么", "如何", "哪些", "是否", "可以", "请问", "关于", "有关", "一下", "告诉", "介绍",
            "怎么办", "怎样", "吗", "呢", "啊", "了", "的", "是", "有", "在", "和", "与"
    );

    private static final List<String> SYMPTOM_KEYWORDS = List.of(
            "发烧", "发热", "高烧", "低烧", "体温", "头痛", "头晕", "头昏", "恶心", "呕吐", "腹痛", "肚子疼",
            "咳嗽", "咳痰", "喉咙痛", "咽痛", "流鼻涕", "鼻塞", "胸闷", "气短", "呼吸困难", "心慌", "心悸",
            "乏力", "无力", "失眠", "出疹", "皮疹", "关节痛", "腰痛", "腹泻", "便秘", "尿频", "看不清",
            "视力模糊", "耳鸣", "眩晕", "抽筋", "浮肿", "水肿", "出血", "症状", "不舒服", "难受", "疼", "痛"
    );

    private static final List<PortalLink> DEFAULT_LINKS = List.of(
            new PortalLink("国家统计数据库", "https://data.stats.gov.cn", "国家统计局官方开放统计数据"),
            new PortalLink("国家统计局数据发布", "https://www.stats.gov.cn/sj/", "最新统计公报与数据解读"),
            new PortalLink("中国政府网数据栏目", "http://www.gov.cn/shuju/index.htm", "宏观经济与社会发展数据"),
            new PortalLink("上海公共数据开放平台", "https://data.sh.gov.cn/", "上海市开放健康相关数据集"),
            new PortalLink("门户数据资源目录", "/data", "本门户开放健康数据"),
            new PortalLink("统一数据资源池", "/data-pool", "爬虫采集 + 开放数据 + ETL 汇总")
    );

    private final CmsContentMapper contentMapper;
    private final CmsAppMapper appMapper;
    private final OpenDataService openDataService;
    private final MedicalResourceService medicalResourceService;
    private final ObjectMapper objectMapper;

    @Data
    public static class KnowledgeBundle {
        private String contextText = "";
        private String linkText = "";
        private List<AiKnowledgeRef> references = new ArrayList<>();
        private List<AiFeatureAction> actions = new ArrayList<>();
        private boolean hasPolicyMatch;
        private boolean hasContentMatch;
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
        scored.addAll(searchMedical(terms, question));
        scored.addAll(searchApps(terms));

        scored.sort(Comparator.comparingDouble(ScoredRef::score).reversed());

        LinkedHashMap<String, ScoredRef> deduped = new LinkedHashMap<>();
        for (ScoredRef item : scored) {
            String key = item.ref().getTitle() + "|" + nullSafe(item.ref().getUrl());
            deduped.putIfAbsent(key, item);
        }

        List<ScoredRef> top = deduped.values().stream()
                .filter(s -> s.score() > 0)
                .limit(8)
                .toList();
        boolean hasPolicy = top.stream().anyMatch(s -> "policy".equals(s.ref().getType()));
        boolean hasContent = !top.isEmpty();
        List<AiFeatureAction> actions = detectFeatureActions(question, top);

        KnowledgeBundle bundle = new KnowledgeBundle();
        bundle.setHasPolicyMatch(hasPolicy);
        bundle.setHasContentMatch(hasContent);
        bundle.setReferences(top.stream().map(ScoredRef::ref).toList());
        bundle.setActions(actions);

        StringBuilder ctx = new StringBuilder();
        for (ScoredRef s : top) {
            AiKnowledgeRef ref = s.ref();
            String section = sectionLabel(ref.getType(), ref.getSource());
            if ("policy".equals(ref.getType()) || "knowledge".equals(ref.getType())
                    || "notice".equals(ref.getType()) || "news".equals(ref.getType())) {
                ctx.append("【").append(section).append("】")
                        .append(ref.getTitle()).append("\n摘要：")
                        .append(truncate(ref.getExcerpt(), 200)).append("\n");
            } else if ("open-data".equals(ref.getType())) {
                ctx.append("【开放数据·").append(nullSafe(ref.getSource())).append("】")
                        .append(ref.getTitle()).append("\n");
            } else if ("internet".equals(ref.getType())) {
                ctx.append("【互联网公开】").append(ref.getTitle())
                        .append(" 链接：").append(ref.getUrl()).append("\n");
            } else if ("medical".equals(ref.getType())) {
                ctx.append("【医疗资源】").append(ref.getTitle())
                        .append(" 摘要：").append(truncate(ref.getExcerpt(), 160)).append("\n");
            } else if ("portal-link".equals(ref.getType())) {
                ctx.append("【应用中心】").append(ref.getTitle())
                        .append(" 链接：").append(nullSafe(ref.getUrl())).append("\n");
            }
        }
        // 无匹配时保持空白：由系统提示约束模型直接给建议，不要说「未检索到」
        bundle.setContextText(ctx.toString());

        List<PortalLink> links = selectFallbackLinks(question, terms, hasPolicy, top);
        StringBuilder linkBlock = new StringBuilder();
        for (PortalLink link : links) {
            linkBlock.append(link.name()).append("：").append(link.url())
                    .append("（").append(link.desc()).append("）\n");
        }
        bundle.setLinkText(linkBlock.isEmpty() ? "（本题暂无额外推荐链接）\n" : linkBlock.toString());
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

    private List<ScoredRef> searchMedical(List<String> terms, String question) {
        List<ScoredRef> result = new ArrayList<>();
        if (!questionMentionsMedical(question) && terms.stream().noneMatch(this::looksLikeMedicalTerm)) {
            return result;
        }
        try {
            String keyword = pickMedicalKeyword(question, terms);
            if (keyword == null || keyword.isBlank()) {
                return result;
            }
            Page<MedicalHospital> hospitals = medicalResourceService.pageHospitals(
                    null, null, null, null, null, null, keyword, 1, 3);
            if (hospitals.getRecords() != null) {
                for (MedicalHospital h : hospitals.getRecords()) {
                    AiKnowledgeRef ref = new AiKnowledgeRef();
                    ref.setType("medical");
                    ref.setTitle(h.getName());
                    ref.setUrl("/medical/hospitals/" + h.getId());
                    ref.setExcerpt(nullSafe(h.getProvince()) + nullSafe(h.getCity())
                            + " " + nullSafe(h.getLevel()) + " " + nullSafe(h.getAddress()));
                    ref.setSource("医疗资源·医院");
                    result.add(new ScoredRef(4.0, ref));
                }
            }
            Page<MedicalDrugCatalog> drugs = medicalResourceService.pageDrugCatalog(
                    null, null, null, keyword, null, 1, 3);
            if (drugs.getRecords() != null) {
                for (MedicalDrugCatalog d : drugs.getRecords()) {
                    AiKnowledgeRef ref = new AiKnowledgeRef();
                    ref.setType("medical");
                    ref.setTitle(d.getDrugName());
                    ref.setUrl("/medical");
                    ref.setExcerpt(nullSafe(d.getCategoryName()) + " "
                            + nullSafe(d.getDosageForm()) + " " + nullSafe(d.getInsuranceType()));
                    ref.setSource("医疗资源·药品目录");
                    result.add(new ScoredRef(3.5, ref));
                }
            }
        } catch (Exception e) {
            log.debug("Medical search skipped: {}", e.getMessage());
        }
        return result;
    }

    private List<AiFeatureAction> detectFeatureActions(String question, List<ScoredRef> top) {
        LinkedHashMap<String, AiFeatureAction> actions = new LinkedHashMap<>();
        String q = question == null ? "" : question;

        if (isSymptomIntent(q)) {
            actions.put("symptom", new AiFeatureAction(
                    "symptom", "去症状自查", "/symptom-check",
                    "您描述的是不适症状，可用症状自查按部位梳理可能原因与就医建议"));
        }
        if (questionMentionsPolicy(q) || top.stream().anyMatch(s -> "policy".equals(s.ref().getType()))) {
            actions.put("policy", new AiFeatureAction(
                    "policy", "查看卫生政策", "/policy",
                    "相关政策文件可在卫生政策栏目查阅"));
        }
        if (q.contains("百科") || q.contains("科普") || q.contains("疾病知识")
                || top.stream().anyMatch(s -> "knowledge".equals(s.ref().getType()))) {
            actions.put("knowledge", new AiFeatureAction(
                    "knowledge", "打开健康百科", "/knowledge",
                    "更多健康科普与疾病知识可在健康百科浏览"));
        }
        if (q.contains("新闻") || q.contains("资讯") || q.contains("疫情通报")
                || top.stream().anyMatch(s -> "news".equals(s.ref().getType()))) {
            actions.put("news", new AiFeatureAction(
                    "news", "浏览新闻中心", "/news",
                    "最新健康资讯可在新闻中心查看"));
        }
        if (questionMentionsMedical(q)
                || top.stream().anyMatch(s -> "medical".equals(s.ref().getType()))) {
            actions.put("medical", new AiFeatureAction(
                    "medical", "查询医疗资源", "/medical",
                    "可查医院、等级与药品目录等医疗资源"));
        }
        if (questionMentionsData(q)
                || top.stream().anyMatch(s -> "open-data".equals(s.ref().getType()))) {
            actions.put("data", new AiFeatureAction(
                    "data", "打开数据资源", "/data",
                    "开放数据集与统计资源可在数据资源目录获取"));
        }
        return new ArrayList<>(actions.values());
    }

    private static boolean isSymptomIntent(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        if (question.contains("症状自查") || question.contains("自查")) {
            return true;
        }
        int hits = 0;
        for (String kw : SYMPTOM_KEYWORDS) {
            if (question.contains(kw)) {
                hits++;
                if (hits >= 1 && (question.contains("怎么了") || question.contains("怎么办")
                        || question.contains("是什么") || question.contains("什么病")
                        || question.contains("严重吗") || question.contains("要紧")
                        || hits >= 2)) {
                    return true;
                }
            }
        }
        // 单条明确症状词也算意图，例如「发烧了怎么办」
        return SYMPTOM_KEYWORDS.stream().anyMatch(question::contains)
                && (question.contains("怎么") || question.contains("如何")
                || question.contains("能否") || question.contains("可以")
                || question.contains("怎么办") || question.contains("怎么了")
                || question.length() <= 40);
    }

    private static boolean questionMentionsMedical(String question) {
        String q = question == null ? "" : question;
        return q.contains("医院") || q.contains("诊所") || q.contains("药店")
                || q.contains("药品") || q.contains("用药") || q.contains("医保药品")
                || q.contains("三甲") || q.contains("就医") || q.contains("挂号")
                || q.contains("医疗资源") || q.contains("哪个医院");
    }

    private boolean looksLikeMedicalTerm(String term) {
        return term != null && (term.contains("医院") || term.contains("药") || term.length() >= 2);
    }

    private static String pickMedicalKeyword(String question, List<String> terms) {
        for (String term : terms) {
            if (term != null && term.length() >= 2
                    && (term.contains("医院") || term.contains("药") || term.length() >= 3)) {
                if (!STOP_WORDS.contains(term)) {
                    return term;
                }
            }
        }
        String cleaned = question == null ? "" : question.replaceAll("[^\\u4e00-\\u9fff]", "");
        return cleaned.length() >= 2 ? cleaned.substring(0, Math.min(6, cleaned.length())) : null;
    }

    private List<PortalLink> selectFallbackLinks(String question, List<String> terms,
                                                   boolean hasPolicy, List<ScoredRef> top) {
        boolean wantsPolicy = hasPolicy || questionMentionsPolicy(question);
        boolean wantsData = questionMentionsData(question);
        LinkedHashMap<String, PortalLink> links = new LinkedHashMap<>();

        for (PortalLink link : DEFAULT_LINKS) {
            boolean policyLike = link.url().contains("gov.cn") || link.name().contains("政策");
            boolean dataLike = link.url().contains("stats.gov.cn") || link.url().contains("/data")
                    || link.url().contains("data.sh.gov.cn") || link.name().contains("数据");
            if (policyLike && !wantsPolicy) {
                continue;
            }
            if (dataLike && !wantsData && !wantsPolicy) {
                // 纯症状/护理类问题不推统计与开放数据目录
                continue;
            }
            double score = scoreText(link.name() + " " + link.desc() + " " + question, terms);
            if (score > 0 || linkMatchesQuestion(link, question)) {
                links.put(link.url(), link.withScore(score));
            }
        }
        for (ScoredRef s : top) {
            AiKnowledgeRef ref = s.ref();
            if (ref.getUrl() != null && !ref.getUrl().isBlank()) {
                if ("policy".equals(ref.getType()) && !wantsPolicy) {
                    continue;
                }
                links.putIfAbsent(ref.getUrl(), new PortalLink(ref.getTitle(), ref.getUrl(),
                        nullSafe(ref.getSource()), s.score()));
            }
        }
        // 仅当用户问政策或已命中政策条目时，补充政策权威入口
        if (wantsPolicy) {
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

    private static boolean questionMentionsPolicy(String question) {
        String q = question == null ? "" : question;
        return q.contains("政策") || q.contains("规划") || q.contains("纲要")
                || q.contains("条例") || q.contains("办法") || q.contains("通知")
                || q.contains("法规") || q.contains("文件") || q.contains("解读");
    }

    private static boolean questionMentionsData(String question) {
        String q = question == null ? "" : question.toLowerCase();
        return q.contains("数据") || q.contains("统计") || q.contains("指标")
                || q.contains("gdp") || q.contains("资源池") || q.contains("开放数据")
                || q.contains("年鉴") || q.contains("数据集");
    }

    private static boolean linkMatchesQuestion(PortalLink link, String question) {
        String q = question.toLowerCase();
        if (q.contains("统计") || q.contains("gdp") || q.contains("指标")) {
            return link.url().contains("stats.gov.cn");
        }
        if (q.contains("上海") || q.contains("医疗机构")) {
            return link.url().contains("sh.gov.cn") || link.name().contains("上海");
        }
        if (questionMentionsPolicy(question)) {
            return link.url().contains("gov.cn") || link.name().contains("政策");
        }
        if (questionMentionsData(question)) {
            return link.url().contains("/data") || link.url().contains("stats.gov.cn");
        }
        return false;
    }

    private static String sectionLabel(String type, String source) {
        return switch (type == null ? "" : type) {
            case "policy" -> "卫生政策";
            case "knowledge" -> "健康百科";
            case "notice" -> "通知公告";
            case "news" -> "新闻中心";
            case "open-data" -> "开放数据";
            case "internet" -> "互联网公开";
            case "medical" -> "医疗资源";
            case "portal-link" -> "应用中心";
            default -> source == null || source.isBlank() ? "门户检索" : source;
        };
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
