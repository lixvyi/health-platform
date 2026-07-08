package com.csu.health.portal.module.symptom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则建议DTO（用于 level 3/4 规则的建议文本展示）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleSuggestionDto {
    /**
     * 规则ID
     */
    private Integer id;

    /**
     * 级别
     */
    private Integer level;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 建议文本
     */
    private String advice;
}
