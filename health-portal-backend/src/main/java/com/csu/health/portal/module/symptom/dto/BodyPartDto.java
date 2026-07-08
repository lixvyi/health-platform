package com.csu.health.portal.module.symptom.dto;

import lombok.Data;
import java.util.List;

/**
 * 身体部位
 */
@Data
public class BodyPartDto {
    private Integer id;
    private String name;
    private Integer parentId;
    private List<BodyPartDto> children;
}
