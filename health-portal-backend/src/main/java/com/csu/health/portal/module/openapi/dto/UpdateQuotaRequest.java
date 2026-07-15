package com.csu.health.portal.module.openapi.dto;

import lombok.Data;

@Data
public class UpdateQuotaRequest {
    private Integer dailyQuota;
    private Integer qpsLimit;
}
