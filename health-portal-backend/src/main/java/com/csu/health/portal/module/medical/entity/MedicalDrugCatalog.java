package com.csu.health.portal.module.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medical_drug_catalog")
public class MedicalDrugCatalog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String categoryCode;
    private String categoryName;
    private String drugNumber;
    private String drugName;
    private String dosageForm;
    private String insuranceType;
    private String remark;
    private Integer catalogYear;
    private String sourceFile;
    private String sourceSheet;
    private Integer sourceRow;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

