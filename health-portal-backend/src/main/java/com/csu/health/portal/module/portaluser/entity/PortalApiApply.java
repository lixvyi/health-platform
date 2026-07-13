package com.csu.health.portal.module.portaluser.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("portal_api_apply")
public class PortalApiApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long apiId;
    private String projectName;
    private String purpose;
    private String reason;
    private String status;
    private String reviewRemark;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
