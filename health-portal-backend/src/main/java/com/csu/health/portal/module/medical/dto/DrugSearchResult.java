package com.csu.health.portal.module.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugSearchResult {
    private Long id;
    private String genericName;
    private String brandName;
    private String category;
    private String dosageForm;
    private String prescriptionType;
    private String indicationsSummary;
}
