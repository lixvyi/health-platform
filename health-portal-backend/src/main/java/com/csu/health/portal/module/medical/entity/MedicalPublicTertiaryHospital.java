package com.csu.health.portal.module.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("medical_public_tertiary_hospital")
public class MedicalPublicTertiaryHospital {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String grade;
    private String province;
    private String hospitalName;
    private String sourceName;
    private String sourceFile;
    private String sourceSheet;
    private Integer sourceRow;
    private LocalDate dataAsOfDate;
    private String verificationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

