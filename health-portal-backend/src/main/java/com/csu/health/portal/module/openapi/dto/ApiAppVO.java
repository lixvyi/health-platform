package com.csu.health.portal.module.openapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 给前端用的展示VO，隐藏 appSecret
 */
@Data
@AllArgsConstructor
public class ApiAppVO {
    private Long id;
    private String appName;
    private String appKey;
    private Integer status;
    private String owner;
    private String email;
    private String organization;
    private String description;
    private String ipWhitelist;
    private Integer dailyQuota;
    private Integer qpsLimit;
    private String tier;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    // 仅创建/重置时返回，其他接口隐藏
    private String appSecret;
}
