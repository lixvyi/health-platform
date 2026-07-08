package com.csu.health.portal.module.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("medical_hospital")
public class MedicalHospital {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceRecordNo;
    private String name;
    private String aliasName;
    private String province;
    private String city;
    private String district;
    private String address;
    private String level;
    private String type;
    private String foundedYear;
    private String operationMode;
    private Integer isInsurance;
    private Integer bedCount;
    private Long annualVisits;
    private Integer medicalStaffCount;
    private String departments;
    private String phone;
    private String email;
    private String postalCode;
    private String introduction;
    private String website;
    private String sourceName;
    private String sourceFile;
    private String sourceSheet;
    private Integer sourceRow;
    private LocalDate dataAsOfDate;
    private LocalDateTime verifiedAt;
    private String verificationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

