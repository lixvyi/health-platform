package com.csu.health.portal.module.medical.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("drug_detail")
public class DrugDetail {
    @TableId
    private Long drugId;
    private String indications;
    private String contraindications;
    private String adverseReactions;
    private String usageDosage;
    private String warnings;
    private String interactionsRaw;
    private String composition;
    private String storage;
    private String validity;
    private Integer schemaVersion;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
