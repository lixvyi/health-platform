package com.csu.health.portal.config;

import com.csu.health.portal.module.openapi.entity.ApiApp;
import com.csu.health.portal.module.openapi.service.ApiAppService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AppKey 鉴权过滤器 — 用于 /api/external/** 路径。<br>
 * 调用方需在 Header 中传递：
 * <ul>
 *   <li><b>X-App-Key</b> — 应用公钥</li>
 *   <li><b>X-Timestamp</b> — 当前 Unix 毫秒时间戳（服务端容忍 ±5 分钟偏差）</li>
 *   <li><b>X-Sign</b> — HMAC-SHA256( appKey + timestamp + httpMethod + path + queryString [+ body] , secret )</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_APP_KEY = "X-App-Key";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_SIGN = "X-Sign";
    private static final String PREFIX = "/api/external/";

    /**
     * 时间戳容忍偏差：±5 分钟
     */
    private static final long TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000L;

    /**
     * 每个 AppKey 的 QPS 计数器
     */
    private final ConcurrentHashMap<String, WindowCounter> qpsCounters = new ConcurrentHashMap<>();

    private final ApiAppService apiAppService;

    /**
     * HMAC-SHA256 签名
     */
    private static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes("UTF-8")));
        } catch (NoSuchAlgorithmException | InvalidKeyException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }

    // ──────────────── 签名相关 ────────────────

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        long startNs = System.nanoTime();
        String appKey = request.getHeader(HEADER_APP_KEY);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);
        String sign = request.getHeader(HEADER_SIGN);
        String ip = getClientIp(request);

        // ── 1. 验证 AppKey ──
        if (!StringUtils.hasText(appKey)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "缺少 AppKey，请在 Header 中传递 X-App-Key");
            return;
        }
        ApiApp app = apiAppService.authenticate(appKey);
        if (app == null) {
            writeError(response, HttpStatus.FORBIDDEN, "AppKey 无效或已被禁用");
            return;
        }

        // ── 2. 签名校验 ──
        if (!StringUtils.hasText(timestampStr) || !StringUtils.hasText(sign)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "缺少 X-Timestamp 或 X-Sign");
            return;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            writeError(response, HttpStatus.BAD_REQUEST, "X-Timestamp 格式错误，请传递 Unix 毫秒时间戳");
            return;
        }
        // 防重放攻击：时间戳偏差在 ±5 分钟内
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > TIMESTAMP_TOLERANCE_MS) {
            writeError(response, HttpStatus.UNAUTHORIZED, "请求已过期，请检查 X-Timestamp（偏差超过 5 分钟）");
            return;
        }
        // 构造签名原文
        String signPayload = buildSignPayload(appKey, timestampStr, request);
        String expectedSign = hmacSha256(app.getAppSecret(), signPayload);
        if (!expectedSign.equals(sign)) {
            log.warn("签名不匹配: appKey={}, ip={}", appKey, ip);
            writeError(response, HttpStatus.FORBIDDEN, "签名验证失败，请检查 X-Sign");
            return;
        }

        // ── 3. IP 白名单检查 ──
        if (StringUtils.hasText(app.getIpWhitelist())) {
            Set<String> allowed = Set.of(app.getIpWhitelist().split(","));
            if (!allowed.contains(ip)) {
                log.warn("IP 不在白名单: appKey={}, ip={}", appKey, ip);
                writeError(response, HttpStatus.FORBIDDEN, "IP 不在白名单内");
                return;
            }
        }

        // ── 4. 日配额检查 ──
        if (!apiAppService.checkDailyQuota(app)) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "今日调用额度已用完");
            return;
        }

        // ── 5. QPS 限流 ──
        int qpsLimit = app.getQpsLimit();
        WindowCounter counter = qpsCounters.computeIfAbsent(appKey, k -> new WindowCounter(qpsLimit));
        if (!counter.tryAcquire()) {
            log.warn("QPS 超限: appKey={}, limit={}/s", appKey, qpsLimit);
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            return;
        }

        // ── 6. 将鉴权上下文设置到 Request 属性中，供后续 Controller 使用 ──
        request.setAttribute("_appKey", appKey);
        request.setAttribute("_appName", app.getAppName());
        request.setAttribute("_appTier", app.getTier());

        // ── 7. 放行并记录日志 ──
        try {
            chain.doFilter(request, response);
        } finally {
            long latency = (System.nanoTime() - startNs) / 1_000_000;
            int status = response.getStatus();
            apiAppService.logCall(appKey, path, request.getMethod(), ip, status, latency);
        }
    }

    /**
     * 构造签名原文：appKey + timestamp + HTTP方法(大写) + 请求路径 + 查询字符串 + 请求体
     */
    private String buildSignPayload(String appKey, String timestamp, HttpServletRequest request) throws IOException {
        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String body = readBody(request);

        StringBuilder sb = new StringBuilder();
        sb.append(appKey).append(timestamp).append(method).append(path);
        sb.append(query != null ? query : "");
        sb.append(body != null ? body : "");
        return sb.toString();
    }

    /**
     * 读取请求体（只对 POST/PUT/PATCH 有效，GET 返回空）
     */
    private String readBody(HttpServletRequest request) throws IOException {
        String method = request.getMethod().toUpperCase();
        if (!Set.of("POST", "PUT", "PATCH").contains(method)) {
            return null;
        }
        // 避免重复读取：ContentCachingRequestWrapper 等方案，简单起见只读取一次
        BufferedReader reader = request.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    // ──────────────── 工具方法 ────────────────

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private void writeError(HttpServletResponse res, HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":" + status.value() + ",\"message\":\"" + message + "\"}");
    }

    /**
     * 单机 QPS 滑动窗口计数器（1 秒窗口，每 AppKey 独立）
     */
    private static class WindowCounter {
        private final int max;
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        WindowCounter(int max) {
            this.max = max;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 1000) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= max;
        }
    }
}
