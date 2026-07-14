package com.csu.health.portal.module.medical.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("symptom_otc_map")
public class SymptomOtcMap {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String symptom;
    private Long drugId;
    private String note;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
