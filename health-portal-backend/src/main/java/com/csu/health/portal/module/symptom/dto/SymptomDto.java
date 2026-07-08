package com.csu.health.portal.module.symptom.dto;

import lombok.Data;

/**
 * 症状
 */
@Data
public class SymptomDto {
    private Integer id;
    private String name;
    private String aliases;
    private String description;
}
