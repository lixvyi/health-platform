package com.csu.health.portal.module.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("medical_specialty_ranking")
public class MedicalSpecialtyRanking {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String specialtyName;
    private Integer rankYear;
    private String hospitalName;
    private Integer ranking;
    private BigDecimal score;
    private String sourceName;
    private String sourceUrl;
    private String sourceFile;
    private String sourceSheet;
    private Integer sourceRow;
    private LocalDateTime createdAt;
}
