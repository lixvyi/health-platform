package com.csu.health.portal.module.symptom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推荐科室
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDto {
    private String name;
    /**
     * 优先级：1=最推荐，2=次推荐，3=备选
     */
    private Integer priority;
    /**
     * 是否为最推荐科室（priority=1）
     */
    private Boolean recommended;
    /**
     * 优先级标签：如"立即就诊"（高危强制科室）
     */
    private String priorityTag;
    /**
     * 最终得分（用于排序）
     */
    private Double score;
}
