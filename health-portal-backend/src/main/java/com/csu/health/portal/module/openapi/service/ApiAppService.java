package com.csu.health.portal.module.openapi.service;

import com.csu.health.portal.common.BusinessException;
import com.csu.health.portal.module.openapi.entity.ApiApp;
import com.csu.health.portal.module.openapi.entity.ApiAppUsageLog;
import com.csu.health.portal.module.openapi.mapper.ApiAppMapper;
import com.csu.health.portal.module.openapi.mapper.ApiAppUsageLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiAppService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final ApiAppMapper apiAppMapper;
    private final ApiAppUsageLogMapper usageLogMapper;

    // ──────────────── AppKey 管理 ────────────────

    private static String generateKey(String prefix) {
        byte[] bytes = new byte[14];
        RANDOM.nextBytes(bytes);
        return prefix + HEX.formatHex(bytes);
    }

    /**
     * 创建应用凭证（初始状态为禁用，需要管理员审批）
     */
    public ApiApp create(String appName, String owner, String email, String organization,
                         String description, Integer dailyQuota, Integer qpsLimit, String tier) {
        ApiApp app = new ApiApp();
        app.setAppName(appName);
        app.setAppKey(generateKey("ak_"));
        app.setAppSecret(generateKey("sk_"));
        app.setStatus(0); // 默认禁用，等待审批
        app.setOwner(owner);
        app.setEmail(email);
        app.setOrganization(organization);
        app.setDescription(description);
        app.setDailyQuota(dailyQuota != null ? dailyQuota : 10000);
        app.setQpsLimit(qpsLimit != null ? qpsLimit : 10);
        app.setTier(tier != null ? tier : "FREE");
        apiAppMapper.insert(app);
        log.info("创建 AppKey: {} (appName={})", app.getAppKey(), appName);
        return app;
    }

    /**
     * 审批通过
     */
    public void approve(Long id, Long adminUserId) {
        ApiApp app = apiAppMapper.selectById(id);
        if (app == null) throw new BusinessException("应用不存在");
        app.setStatus(1);
        app.setApprovedBy(adminUserId);
        app.setApprovedAt(LocalDateTime.now());
        apiAppMapper.updateById(app);
        log.info("审批通过 AppKey: {}", app.getAppKey());
    }

    /**
     * 禁用/启用
     */
    public void toggleStatus(Long id) {
        ApiApp app = apiAppMapper.selectById(id);
        if (app == null) throw new BusinessException("应用不存在");
        app.setStatus(app.getStatus() == 1 ? 0 : 1);
        apiAppMapper.updateById(app);
        log.info("切换 AppKey 状态: {} -> {}", app.getAppKey(), app.getStatus());
    }

    /**
     * 重新生成 AppSecret
     */
    public String rotateSecret(Long id) {
        ApiApp app = apiAppMapper.selectById(id);
        if (app == null) throw new BusinessException("应用不存在");
        String newSecret = generateKey("sk_");
        app.setAppSecret(newSecret);
        apiAppMapper.updateById(app);
        log.info("重置 AppSecret: {}", app.getAppKey());
        return newSecret;
    }

    // ──────────────── 鉴权核心方法 ────────────────

    /**
     * 更新配额
     */
    public void updateQuota(Long id, Integer dailyQuota, Integer qpsLimit) {
        ApiApp app = apiAppMapper.selectById(id);
        if (app == null) throw new BusinessException("应用不存在");
        if (dailyQuota != null) app.setDailyQuota(dailyQuota);
        if (qpsLimit != null) app.setQpsLimit(qpsLimit);
        apiAppMapper.updateById(app);
    }

    /**
     * 验证 AppKey 有效性与配额，返回应用信息
     */
    public ApiApp authenticate(String appKey) {
        if (appKey == null || appKey.isBlank()) {
            return null;
        }
        ApiApp app = apiAppMapper.findByAppKey(appKey);
        if (app == null || app.getStatus() != 1) {
            return null;
        }
        // 检查过期
        if (app.getExpireAt() != null && app.getExpireAt().isBefore(LocalDateTime.now())) {
            log.warn("AppKey 已过期: {}", appKey);
            return null;
        }
        return app;
    }

    /**
     * 检查日配额
     */
    public boolean checkDailyQuota(ApiApp app) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        long used = usageLogMapper.countSince(app.getAppKey(), today + " 00:00:00");
        if (used >= app.getDailyQuota()) {
            log.warn("AppKey 日配额已用完: {} (used={}, limit={})",
                    app.getAppKey(), used, app.getDailyQuota());
            return false;
        }
        return true;
    }

    // ──────────────── 查询方法 ────────────────

    /**
     * 记录调用日志（异步场景下建议改为 @Async 或消息队列）
     */
    public void logCall(String appKey, String apiPath, String apiMethod, String ip,
                        int statusCode, long latencyMs) {
        ApiAppUsageLog log = new ApiAppUsageLog();
        log.setAppKey(appKey);
        log.setApiPath(apiPath);
        log.setApiMethod(apiMethod);
        log.setIp(ip);
        log.setStatusCode(statusCode);
        log.setLatencyMs((int) latencyMs);
        log.setRequestAt(LocalDateTime.now());
        usageLogMapper.insert(log);
    }

    public List<ApiApp> listAll() {
        return apiAppMapper.selectList(null);
    }

    public ApiApp getById(Long id) {
        return apiAppMapper.selectById(id);
    }

    public ApiApp getByAppKey(String appKey) {
        return apiAppMapper.findByAppKey(appKey);
    }

    // ──────────────── 用量统计 ────────────────

    public List<ApiApp> listByOwner(String owner) {
        return apiAppMapper.findByOwner(owner);
    }

    /**
     * 指定 AppKey 近 N 天的每日调用量
     */
    public List<Map<String, Object>> dailyUsage(String appKey, int days) {
        String since = LocalDate.now().minusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
        return usageLogMapper.dailyUsage(appKey, since + " 00:00:00");
    }

    /**
     * 所有 AppKey 近 N 天的每日调用量
     */
    public List<Map<String, Object>> allDailyUsage(int days) {
        String since = LocalDate.now().minusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
        return usageLogMapper.allDailyUsage(since + " 00:00:00");
    }

    /**
     * 今日各 AppKey 实时调用排行
     */
    public List<Map<String, Object>> todayUsage() {
        return usageLogMapper.todayUsage();
    }

    // ──────────────── 私有方法 ────────────────

    /**
     * 总体统计概览
     */
    public Map<String, Object> overallStats(int days) {
        String since = LocalDate.now().minusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
        return usageLogMapper.overallStats(since + " 00:00:00");
    }
}
