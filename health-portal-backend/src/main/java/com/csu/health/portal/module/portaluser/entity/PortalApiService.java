package com.csu.health.portal.module.portaluser.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("portal_api_service")
public class PortalApiService {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private String method;
    private String path;
    private String paramsJson;
    private String responseExample;
    private String permissionLevel;
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
