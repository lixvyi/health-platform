package com.csu.health.portal.module.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.csu.health.portal.common.BusinessException;
import com.csu.health.portal.module.content.dto.ContentSaveRequest;
import com.csu.health.portal.module.content.entity.CmsApp;
import com.csu.health.portal.module.content.entity.CmsBanner;
import com.csu.health.portal.module.content.entity.CmsContent;
import com.csu.health.portal.module.content.entity.CmsSiteConfig;
import com.csu.health.portal.module.content.mapper.CmsAppMapper;
import com.csu.health.portal.module.content.mapper.CmsBannerMapper;
import com.csu.health.portal.module.content.mapper.CmsContentMapper;
import com.csu.health.portal.module.content.mapper.CmsSiteConfigMapper;
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

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public ContentService(CmsContentMapper contentMapper, CmsBannerMapper bannerMapper,
                          CmsAppMapper appMapper, CmsSiteConfigMapper siteConfigMapper) {
        this.contentMapper = contentMapper;
        this.bannerMapper = bannerMapper;
        this.appMapper = appMapper;
        this.siteConfigMapper = siteConfigMapper;
    }

    public Page<CmsContent> pageContent(String categoryCode, String keyword, Integer status, int page, int size) {
        long total = contentMapper.countByCondition(categoryCode, keyword, status);
        int offset = (page - 1) * size;
        List<CmsContent> records = contentMapper.selectPageByCondition(categoryCode, keyword, status, offset, size);
        Page<CmsContent> result = new Page<>(page, size, total);
        result.setRecords(records);
        return result;
    }

    public Page<CmsContent> pagePublished(String categoryCode, String keyword, int page, int size) {
        return pageContent(categoryCode, keyword, 1, page, size);
    }

    public CmsContent getById(Long id) {
        CmsContent content = contentMapper.selectByIdXml(id);
        if (content == null) {
            throw new BusinessException("内容不存在");
        }
        return content;
    }

    public CmsContent getPublishedDetail(Long id) {
        CmsContent content = getById(id);
        if (content.getStatus() != 1) {
            throw new BusinessException("内容未发布");
        }
        contentMapper.incrementViewCount(id);
        content.setViewCount(content.getViewCount() + 1);
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
     * 新闻中心聚合：当日/近期 NEWS + 3日内新政策速递 + 资源池相关资讯
     */
    public List<CmsContent> buildHomeNewsFeed() {
        List<CmsContent> merged = new ArrayList<>();
        merged.addAll(contentMapper.selectRecentPublished("NEWS", 7, 15));
        merged.addAll(contentMapper.selectRecentPublished("POLICY", 3, 8));

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
}
