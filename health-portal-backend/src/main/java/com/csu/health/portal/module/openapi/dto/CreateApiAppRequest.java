package com.csu.health.portal.module.openapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateApiAppRequest {
    @NotBlank(message = "应用名称不能为空")
    private String appName;
    private String owner;
    @Email(message = "邮箱格式不正确")
    private String email;
    private String organization;
    private String description;
    private Integer dailyQuota;
    private Integer qpsLimit;
    private String tier;
}
