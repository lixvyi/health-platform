package com.csu.health.portal.module.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_app")
public class ApiApp {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appName;
    private String appKey;
    private String appSecret;
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
    private LocalDateTime updatedAt;
}
