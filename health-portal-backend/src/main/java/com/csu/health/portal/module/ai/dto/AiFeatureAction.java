package com.csu.health.portal.module.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiFeatureAction {
    /** symptom / policy / knowledge / news / medical / data */
    private String code;
    private String label;
    private String path;
    private String tip;
}
