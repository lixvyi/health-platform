package com.csu.health.portal.module.symptom.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 症状自查请求
 */
@Data
public class SymptomCheckRequest {

    @NotEmpty(message = "症状ID列表不能为空")
    @Size(max = 5, message = "最多选择5个症状")
    private List<Integer> symptomIds;
}
