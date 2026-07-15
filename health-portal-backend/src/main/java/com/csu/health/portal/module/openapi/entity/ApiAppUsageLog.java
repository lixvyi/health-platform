package com.csu.health.portal.module.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("api_app_usage_log")
public class ApiAppUsageLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String appKey;
    private String apiPath;
    private String apiMethod;
    private String ip;
    private Integer statusCode;
    private Integer latencyMs;
    private LocalDateTime requestAt;
}
