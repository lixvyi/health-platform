package com.csu.health.portal.module.medical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrugDetailVO {
    private Long id;
    private String approvalNumber;
    private String genericName;
    private String brandName;
    private String manufacturer;
    private String category;
    private String dosageForm;
    private String prescriptionType;
    private String atcCode;
    private String indications;
    private String contraindications;
    private String adverseReactions;
    private String usageDosage;
    private String warnings;
    private String composition;
    private String storage;
    private String validity;
}
