package com.csu.health.portal.module.medical.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("drug_basic")
public class DrugBasic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String approvalNumber;
    private String genericName;
    private String brandName;
    private String manufacturer;
    private String category;
    private String dosageForm;
    private String prescriptionType;
    private String atcCode;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
