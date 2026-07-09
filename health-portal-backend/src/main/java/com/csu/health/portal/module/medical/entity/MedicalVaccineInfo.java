package com.csu.health.portal.module.medical.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 疫苗信息实体
 */
@Data
@TableName("medical_vaccine_info")
public class MedicalVaccineInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 疫苗名称 */
    private String vaccineName;

    /** 可预防疾病 */
    private String preventDisease;

    /** 疫苗种类 */
    private String vaccineType;

    /** 接种途径 */
    private String route;

    /** 剂量 */
    private String dosage;

    /** 英文缩写 */
    private String abbr;

    /** 接种程序说明 */
    private String scheduleInfo;

    /** 特殊人群接种建议 */
    private String specialPopulation;

    /** 版本年份 */
    private Integer versionYear;

    /** 数据来源 */
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
