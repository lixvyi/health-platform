package com.csu.health.portal.module.portaluser.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("portal_user")
public class PortalUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String email;
    private String phone;
    private String realName;
    private String role;
    private Integer status;
    private String organization;
    private String orgType;
    private String researchDirection;
    private String certifyStatus;
    private String certifyReason;
    private String certifyRemark;
    private LocalDateTime certifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
