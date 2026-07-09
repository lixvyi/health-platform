package com.csu.health.portal.module.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medical_hospital_grade")
public class MedicalHospitalGrade {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String grade;
    private String hospitalName;
    private String sourceName;
    private String sourceFile;
    private String sourceSheet;
    private Integer sourceRow;
    private Integer sourceYear;
    private LocalDateTime createdAt;
}

