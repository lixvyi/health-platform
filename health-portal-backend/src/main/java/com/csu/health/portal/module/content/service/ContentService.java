package com.csu.health.portal.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.common.BusinessException;
import com.csu.health.portal.module.content.dto.ContentSaveRequest;
import com.csu.health.portal.module.content.entity.CmsApp;
import com.csu.health.portal.module.content.entity.CmsBanner;
import com.csu.health.portal.module.content.entity.CmsContent;
import com.csu.health.portal.module.content.entity.CmsSiteConfig;
import com.csu.health.portal.module.content.entity.KnowledgeCategory;
import com.csu.health.portal.module.content.mapper.CmsAppMapper;
import com.csu.health.portal.module.content.mapper.CmsBannerMapper;
import com.csu.health.portal.module.content.mapper.CmsContentMapper;
import com.csu.health.portal.module.content.mapper.CmsSiteConfigMapper;
import com.csu.health.portal.module.content.mapper.KnowledgeCategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ContentService {

    private final CmsContentMapper contentMapper;
    private final CmsBannerMapper bannerMapper;
    private final CmsAppMapper appMapper;
    private final CmsSiteConfigMapper siteConfigMapper;
    private final KnowledgeCategoryMapper knowledgeCategoryMapper;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public ContentService(CmsContentMapper contentMapper, CmsBannerMapper bannerMapper,
                          CmsAppMapper appMapper, CmsSiteConfigMapper siteConfigMapper,
                          KnowledgeCategoryMapper knowledgeCategoryMapper) {
        this.contentMapper = contentMapper;
        this.bannerMapper = bannerMapper;
        this.appMapper = appMapper;
        this.siteConfigMapper = siteConfigMapper;
        this.knowledgeCategoryMapper = knowledgeCategoryMapper;
    }

    public Page<CmsContent> pageContent(String categoryCode, String keyword, Integer status, int page, int size) {
        return pageContent(categoryCode, null, keyword, status, page, size);
    }

    public Page<CmsContent> pageContent(String categoryCode, String knowledgeCategoryCode,
                                        String keyword, Integer status, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long total = contentMapper.countByCondition(categoryCode, knowledgeCategoryCode, keyword, status);
        int offset = (safePage - 1) * safeSize;
        List<CmsContent> records = contentMapper.selectPageByCondition(
                categoryCode, knowledgeCategoryCode, keyword, status, offset, safeSize);
        Page<CmsContent> result = new Page<>(safePage, safeSize, total);
        result.setRecords(records);
        return result;
    }

    public Page<CmsContent> pagePublished(String categoryCode, String keyword, int page, int size) {
        return pageContent(categoryCode, keyword, 1, page, size);
    }

    public Page<CmsContent> pagePublished(String categoryCode, String knowledgeCategoryCode,
                                          String keyword, int page, int size) {
        return pageContent(categoryCode, knowledgeCategoryCode, keyword, 1, page, size);
    }

    public List<KnowledgeCategory> listKnowledgeCategories() {
        List<KnowledgeCategory> categories = knowledgeCategoryMapper.selectList(new LambdaQueryWrapper<KnowledgeCategory>()
                .eq(KnowledgeCategory::getStatus, 1)
                .orderByAsc(KnowledgeCategory::getSortOrder, KnowledgeCategory::getId));
        categories.forEach(category -> category.setIcon(iconForCategory(category.getCode())));
        return categories;
    }

    private String iconForCategory(String code) {
        if ("DISEASE".equals(code)) return "🏥";
        if ("DRUG".equals(code)) return "💊";
        if ("VACCINE".equals(code)) return "💉";
        if ("EPIDEMIC".equals(code)) return "🦠";
        if ("HEALTH_POPULARIZATION".equals(code)) return "❤️";
        if ("MEDICAL_TERMS".equals(code)) return "📋";
        return "📚";
    }

    public List<CmsContent> relatedPublished(Long id, int limit) {
        getPublishedDetailWithoutIncrement(id);
        return contentMapper.selectRelated(id, Math.min(10, Math.max(1, limit)));
    }

    public CmsContent getById(Long id) {
        CmsContent content = contentMapper.selectByIdXml(id);
        if (content == null) {
            throw new BusinessException("内容不存在");
        }
        return content;
    }

    public CmsContent getPublishedDetail(Long id) {
        CmsContent content = getPublishedDetailWithoutIncrement(id);
        contentMapper.incrementViewCount(id);
        content.setViewCount(content.getViewCount() + 1);
        return content;
    }

    private CmsContent getPublishedDetailWithoutIncrement(Long id) {
        CmsContent content = getById(id);
        if (content.getStatus() != 1) {
            throw new BusinessException("内容未发布");
        }
        return content;
    }

    public Long save(ContentSaveRequest req, Long userId) {
        CmsContent entity = new CmsContent();
        entity.setCategoryCode(req.getCategoryCode());
        entity.setTitle(req.getTitle());
        entity.setSummary(req.getSummary());
        entity.setContent(req.getContent());
        entity.setCoverUrl(req.getCoverUrl());
        entity.setAuthor(req.getAuthor());
        copyMedicalMetadata(req, entity, true);
        entity.setStatus(req.getStatus() == null ? 0 : req.getStatus());
        entity.setPublishTime(req.getPublishTime() == null ? LocalDateTime.now() : req.getPublishTime());
        entity.setCreatedBy(userId);
        entity.setViewCount(0);
        contentMapper.insertXml(entity);
        evictHomeCache();
        return entity.getId();
    }

    public void update(Long id, ContentSaveRequest req) {
        getById(id);
        CmsContent entity = new CmsContent();
        entity.setId(id);
        entity.setCategoryCode(req.getCategoryCode());
        entity.setTitle(req.getTitle());
        entity.setSummary(req.getSummary());
        entity.setContent(req.getContent());
        entity.setCoverUrl(req.getCoverUrl());
        entity.setAuthor(req.getAuthor());
        copyMedicalMetadata(req, entity, false);
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        if (req.getPublishTime() != null) {
            entity.setPublishTime(req.getPublishTime());
        }
        contentMapper.updateDynamic(entity);
        evictHomeCache();
    }

    public void delete(Long id) {
        contentMapper.deleteByIdXml(id);
        evictHomeCache();
    }

    public List<CmsBanner> listBanners() {
        return bannerMapper.selectList(new LambdaQueryWrapper<CmsBanner>()
                .eq(CmsBanner::getStatus, 1)
                .orderByAsc(CmsBanner::getSortOrder));
    }

    public List<CmsBanner> listAllBanners() {
        return bannerMapper.selectList(new LambdaQueryWrapper<CmsBanner>()
                .orderByAsc(CmsBanner::getSortOrder));
    }

    public Long saveBanner(CmsBanner banner) {
        bannerMapper.insert(banner);
        evictHomeCache();
        return banner.getId();
    }

    public void updateBanner(CmsBanner banner) {
        bannerMapper.updateById(banner);
        evictHomeCache();
    }

    public void deleteBanner(Long id) {
        bannerMapper.deleteById(id);
        evictHomeCache();
    }

    public List<CmsApp> listApps() {
        return appMapper.selectList(new LambdaQueryWrapper<CmsApp>()
                .eq(CmsApp::getStatus, 1)
                .orderByAsc(CmsApp::getSortOrder));
    }

    public List<CmsApp> listAllApps() {
        return appMapper.selectList(new LambdaQueryWrapper<CmsApp>()
                .orderByAsc(CmsApp::getSortOrder));
    }

    public Long saveApp(CmsApp app) {
        appMapper.insert(app);
        evictHomeCache();
        return app.getId();
    }

    public void updateApp(CmsApp app) {
        appMapper.updateById(app);
        evictHomeCache();
    }

    public void deleteApp(Long id) {
        appMapper.deleteById(id);
        evictHomeCache();
    }

    public Map<String, Object> getAbout() {
        Map<String, Object> map = new HashMap<>();
        map.put("title", getConfigValue("about_title"));
        map.put("content", getConfigValue("about_content"));
        return map;
    }

    public void updateAbout(String title, String content) {
        upsertConfig("about_title", title, "关于我们标题");
        upsertConfig("about_content", content, "关于我们正文");
        evictHomeCache();
    }

    public String getHomeIntro() {
        return getConfigValue("home_intro");
    }

    public void updateHomeIntro(String intro) {
        upsertConfig("home_intro", intro, "首页简介");
        evictHomeCache();
    }

    private String getConfigValue(String key) {
        CmsSiteConfig cfg = siteConfigMapper.findByKey(key);
        return cfg == null ? "" : cfg.getConfigValue();
    }

    private void upsertConfig(String key, String value, String remark) {
        CmsSiteConfig cfg = siteConfigMapper.findByKey(key);
        if (cfg == null) {
            cfg = new CmsSiteConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            cfg.setRemark(remark);
            siteConfigMapper.insert(cfg);
        } else {
            cfg.setConfigValue(value);
            siteConfigMapper.updateById(cfg);
        }
    }

    public Map<String, Object> homeData() {
        Map<String, Object> data = new HashMap<>();
        data.put("intro", getHomeIntro());
        data.put("banners", listBanners());
        data.put("news", buildHomeNewsFeed());
        data.put("notices", pagePublished("NOTICE", null, 1, 5).getRecords());
        data.put("apps", listApps());
        return data;
    }

    /**
     * 新闻中心只聚合 NEWS，卫生政策保持在 POLICY 栏目，避免两个栏目内容重复。
     */
    public List<CmsContent> buildHomeNewsFeed() {
        List<CmsContent> merged = new ArrayList<>();
        merged.addAll(contentMapper.selectRecentPublished("NEWS", 30, 15));

        LinkedHashMap<String, CmsContent> deduped = new LinkedHashMap<>();
        for (CmsContent c : merged) {
            String key = c.getSourceUrl() != null && !c.getSourceUrl().isBlank()
                    ? c.getSourceUrl() : "id:" + c.getId();
            deduped.putIfAbsent(key, c);
        }
        return deduped.values().stream()
                .sorted(Comparator.comparing(CmsContent::getPublishTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();
    }

    public Map<String, Long> stats() {
        String cacheKey = "portal:stats";
        if (redisTemplate != null) {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && cached.startsWith("{")) {
                Map<String, Long> parsed = new HashMap<>();
                for (String part : cached.replace("{", "").replace("}", "").split(",")) {
                    String[] kv = part.split("=");
                    if (kv.length == 2) {
                        parsed.put(kv[0].trim(), Long.parseLong(kv[1].trim()));
                    }
                }
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            }
        }
        Map<String, Long> stats = buildStats();
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(cacheKey, stats.toString(), 5, TimeUnit.MINUTES);
        }
        return stats;
    }

    private Map<String, Long> buildStats() {
        Map<String, Long> stats = new HashMap<>();
        for (String code : List.of("NEWS", "NOTICE", "POLICY", "KNOWLEDGE")) {
            long count = contentMapper.selectCount(new LambdaQueryWrapper<CmsContent>()
                    .eq(CmsContent::getCategoryCode, code)
                    .eq(CmsContent::getStatus, 1));
            stats.put(code, count);
        }
        stats.put("APP", (long) listApps().size());
        return stats;
    }

    private void evictHomeCache() {
        if (redisTemplate != null) {
            redisTemplate.delete("portal:stats");
        }
    }

    private static void copyMedicalMetadata(ContentSaveRequest req, CmsContent entity, boolean create) {
        entity.setSourceUrl(req.getSourceUrl());
        entity.setSourceName(req.getSourceName());
        entity.setSourcePublishDate(req.getSourcePublishDate());
        entity.setPublisher(req.getPublisher());
        entity.setLastReviewTime(req.getLastReviewTime());
        entity.setTargetAudience(req.getTargetAudience());
        entity.setContentType(create && req.getContentType() == null ? "ARTICLE" : req.getContentType());
        entity.setIsMedical(create && req.getIsMedical() == null ? 0 : req.getIsMedical());
        entity.setHasEmergencyWarning(create && req.getHasEmergencyWarning() == null
                ? 0 : req.getHasEmergencyWarning());
        entity.setContraindications(req.getContraindications());
        entity.setAdverseReactions(req.getAdverseReactions());
        entity.setVerificationStatus(create && req.getVerificationStatus() == null
                ? "UNVERIFIED" : req.getVerificationStatus());
    }
}
