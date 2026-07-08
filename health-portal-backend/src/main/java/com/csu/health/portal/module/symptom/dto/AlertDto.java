package com.csu.health.portal.module.symptom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 急诊预警信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDto {
    private Integer id;
    private Integer level;
    private String color;
    private String description;
    private String advice;
    private String responseTime;
}
