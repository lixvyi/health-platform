package com.csu.health.portal.module.symptom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 症状自查响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SymptomCheckResponse {
    /**
     * 推荐科室列表
     */
    private List<DepartmentDto> departments;

    /**
     * 紧急警示列表
     */
    private List<AlertDto> alerts;

    /**
     * 提醒信息列表（部分匹配）
     */
    private List<String> reminders;

    /**
     * 就诊建议列表（匹配的level 3/4规则建议）
     */
    private List<RuleSuggestionDto> suggestions;

    /**
     * 免责声明
     */
    private String disclaimer;
}
